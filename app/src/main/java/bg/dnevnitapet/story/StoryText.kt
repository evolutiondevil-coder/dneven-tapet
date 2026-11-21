package bg.dnevnitapet.story

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.i18n.AppLanguage
import bg.dnevnitapet.i18n.I18n
import bg.dnevnitapet.i18n.tr
import bg.dnevnitapet.wallpaper.MyPhotos

/** Which texts of a picture are translated, and how. Pure logic, unit-tested. */
object StoryText {
    /** A picture's story: title, place and description (translated or original). */
    data class Story(val title: String, val location: String, val description: String)

    /** Already Bulgarian (or another Cyrillic text): nothing to translate. */
    fun isCyrillic(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return true
        return letters.count { it in 'Ѐ'..'ӿ' } * 2 >= letters.length
    }

    /** Texts are translated from English only (Bing's other markets keep their own language). */
    fun translatable(c: Candidate): Boolean =
        I18n.language == AppLanguage.BG && c.sourceId != MyPhotos.SOURCE_ID && (c.lang.isBlank() || c.lang == "en")

    fun original(c: Candidate) = Story(
        title = c.title.trim(),
        location = c.location.trim().takeIf { it != c.title.trim() }.orEmpty(),
        description = c.description.trim().takeIf { it != c.title.trim() }.orEmpty(),
    )

    /** The texts to send to the translator, in a fixed order: title, location, description (blank ones skipped). */
    fun parts(story: Story): List<String> = listOf(story.title, story.location, story.description).filter { it.isNotBlank() }

    /** Puts translated [parts] back in place. */
    fun assemble(original: Story, translated: List<String>): Story {
        val it = translated.iterator()
        fun next(s: String) = if (s.isBlank()) s else if (it.hasNext()) it.next() else s
        return Story(next(original.title), next(original.location), next(original.description))
    }

    /** The title shown in the app: in Bulgarian once translated ([entries] = the translations made so far). */
    fun title(c: Candidate, entries: Map<String, String>): String = when {
        // Own photos are called "Моя снимка" in the app's language.
        c.sourceId == MyPhotos.SOURCE_ID -> tr(c.title)
        translatable(c) -> entries[c.title.trim()] ?: c.title
        else -> c.title
    }

    /** What "Прочети в Уикипедия" searches for: the place, or else the title. */
    fun wikipediaQuery(story: Story): String = story.location.ifBlank { story.title }
}

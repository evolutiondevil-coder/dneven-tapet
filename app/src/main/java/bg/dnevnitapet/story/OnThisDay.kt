package bg.dnevnitapet.story

import android.content.Context
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.net.Http
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate

/** One event that happened on today's date in some year, for the "На този ден" box. */
@Serializable
data class DayFact(
    /** The day it is for (ISO) and the language of the text ("bg" or "en"). */
    val date: String,
    val lang: String,
    /** "1859" or "42 пр.н.е." – as the Wikipedia page writes it, without "г.". */
    val year: String,
    val text: String,
    /** The Wikipedia article about it (or the page of the date). */
    val link: String,
)

/**
 * "На този ден": the events listed on Wikipedia's page of today's date ("24 ноември" in Bulgarian,
 * "November 24" in English). Nothing sad is picked (wars, deaths, disasters); one event a day, kept
 * in a small file so the page is asked once a day.
 */
class OnThisDay(context: Context, private val http: Http) {
    private val store = JsonFileStore(File(context.filesDir, "day-fact.json"), DayFact.serializer()) {
        DayFact("", "", "", "", "")
    }

    /** Today's fact in [lang], from the file when it is already there. Null when there is none (or no network). */
    suspend fun today(lang: String, date: LocalDate = LocalDate.now()): DayFact? {
        store.value.takeIf { it.date == date.toString() && it.lang == lang && it.text.isNotEmpty() }?.let { return it }
        val fact = withTimeoutOrNull(15_000) {
            runCatching {
                val events = DayPage.events(http.getText(DayPage.rawUrl(lang, date)), lang)
                DayPage.pick(events, date)?.let { DayFact(date.toString(), lang, it.year, it.text, it.link) }
            }.getOrNull()
        } ?: return null
        store.update { fact }
        return fact
    }

    /** The fact kept from before, if it is for [date] and [lang] (to show it without waiting). */
    fun cached(lang: String, date: LocalDate = LocalDate.now()): DayFact? =
        store.value.takeIf { it.date == date.toString() && it.lang == lang && it.text.isNotEmpty() }
}

/** Reads the "Събития" / "Events" list of a Wikipedia date page (its raw wikitext). */
object DayPage {
    data class Event(val year: String, val text: String, val link: String)

    private val BG_MONTHS = listOf("януари", "февруари", "март", "април", "май", "юни", "юли", "август", "септември", "октомври", "ноември", "декември")
    private val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** "24 ноември" / "November 24". */
    fun title(lang: String, date: LocalDate): String =
        if (lang == "bg") "${date.dayOfMonth} ${BG_MONTHS[date.monthValue - 1]}" else "${EN_MONTHS[date.monthValue - 1]} ${date.dayOfMonth}"

    fun rawUrl(lang: String, date: LocalDate): String =
        "https://${site(lang)}.wikipedia.org/w/index.php?title=${encode(title(lang, date))}&action=raw"

    fun articleUrl(lang: String, title: String): String = "https://${site(lang)}.wikipedia.org/wiki/${encode(title)}"

    private fun site(lang: String) = if (lang == "bg") "bg" else "en"

    private fun encode(title: String): String =
        URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8").replace("+", "%20").replace("%2F", "/")

    private val HEADING = Regex("""^==\s*(Събития|Events)\s*==\s*$""")
    private val LEVEL2 = Regex("""^==[^=].*[^=]==\s*$""")
    private val DASH = Regex("""\s+(?:–|—|-|&ndash;|&mdash;|\{\{snd\}\}|\{\{spaced ndash\}\})\s+""")
    private val LINK = Regex("""\[\[([^\[\]|]+)(?:\|([^\[\]]*))?\]\]""")

    /** The events of the page, the year and the text cleaned of wiki markup. */
    fun events(wikitext: String, lang: String): List<Event> {
        val lines = wikitext.lines()
        val start = lines.indexOfFirst { HEADING.matches(it.trim()) }
        if (start < 0) return emptyList()
        val out = ArrayList<Event>()
        var yearAbove: String? = null
        for (raw in lines.drop(start + 1)) {
            val line = raw.trim()
            if (LEVEL2.matches(line)) break
            if (!line.startsWith("*")) {
                if (line.startsWith("=")) yearAbove = null
                continue
            }
            val depth = line.takeWhile { it == '*' }.length
            val body = line.drop(depth).trim()
            val split = DASH.find(body)
            val (yearPart, textPart) = when {
                split != null && depth == 1 -> body.substring(0, split.range.first) to body.substring(split.range.last + 1)
                depth > 1 && yearAbove != null -> yearAbove to body
                else -> {
                    // "* [[1859]] г.:" – the events of that year follow one level deeper.
                    if (body.endsWith(":")) yearAbove = year(body.dropLast(1))
                    continue
                }
            }
            val y = year(yearPart) ?: continue
            if (depth == 1) yearAbove = null
            val text = clean(textPart)
            if (text.length < 20 || text.length > 260 || text.any { it in "{}[]|<>=" }) continue
            out += Event(y, capitalize(text), link(textPart, lang) ?: articleUrl(lang, y))
        }
        return out
    }

    /** The year as written ("1859", "42 пр.н.е.", "AD 9"), or null when this is not a year. */
    internal fun year(part: String): String? {
        val y = clean(part).removeSuffix(":").trim()
            .replace(Regex("""\s*г\.?\s*$"""), "")
            .replace(Regex("""\s*г\.\s*(пр|от)"""), " $1")
            .trim()
        return y.takeIf { it.isNotEmpty() && it.length <= 16 && it.any(Char::isDigit) }
    }

    /** "1859 г." / "42 г. пр.н.е." in Bulgarian, "1859" in English. */
    fun yearLabel(year: String, lang: String): String {
        if (lang != "bg") return year
        val m = Regex("""^(\d+)\s*(.*)$""").find(year.trim()) ?: return year
        val rest = m.groupValues[2].trim()
        return if (rest.isEmpty()) "${m.groupValues[1]} г." else "${m.groupValues[1]} г. $rest"
    }

    /** The first article the text links to (not a year, a date or a file). */
    private fun link(wikitext: String, lang: String): String? =
        LINK.findAll(wikitext).map { it.groupValues[1].substringBefore('#').trim() }
            .firstOrNull { t -> t.isNotEmpty() && ':' !in t && !t.all { it.isDigit() || it.isWhitespace() } && !isDate(t) }
            ?.let { articleUrl(lang, it) }

    private fun isDate(t: String): Boolean =
        BG_MONTHS.any { t.endsWith(" $it") } || EN_MONTHS.any { t.startsWith("$it ") } || t.matches(Regex("""\d+\s*(г\.|BC|AD).*"""))

    /** Wiki markup out: links, templates, references, bold/italic, HTML. */
    fun clean(wikitext: String): String {
        var s = wikitext
            .replace(Regex("""<ref[^>]*/>"""), "")
            .replace(Regex("""<ref[^>]*>.*?</ref>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        // Templates that show words keep them ({{нп|Статия|Текст|en|…}} shows "Текст"); the rest go.
        var before: String
        do {
            before = s
            s = s.replace(Regex("""\{\{([^{}]*)\}\}""")) { m -> template(m.groupValues[1].split('|').map { it.trim() }) }
        } while (s != before)
        s = s.replace(LINK) { m -> m.groupValues[2].ifEmpty { m.groupValues[1] } }
            .replace(Regex("""\[https?://\S+\s+([^\]]+)\]"""), "$1")
            .replace(Regex("""\[https?://\S+\]"""), "")
            .replace("'''", "").replace("''", "")
            .replace(Regex("""<br\s*/?>"""), " ")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ").replace("&ndash;", "–").replace("&mdash;", "—").replace("&amp;", "&")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([,.;:!?])"""), "$1")
            .replace("( ", "(").replace(" )", ")").replace("()", "")
        return s.trim()
    }

    private fun template(parts: List<String>): String {
        fun p(i: Int) = parts.getOrNull(i).orEmpty()
        val name = parts[0].lowercase()
        return when (name) {
            "нп" -> p(2).ifEmpty { p(1) }
            "ill", "interlanguage link", "nobr", "nowrap", "small", "нобр" -> p(1)
            "lang" -> p(2)
            "convert" -> "${p(1)} ${p(2)}"
            "ship" -> "${p(1)} ${p(2)}"
            "hms", "uss", "sms", "rms", "hmas", "hmcs" -> "${name.uppercase()} ${p(1)}"
            "circa", "c." -> "c. "
            else -> ""
        }
    }

    private fun capitalize(s: String) = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    /** Beginnings of words of sad or violent events – such an event is skipped (the app should be pleasant). */
    private val SAD_STEMS = listOf(
        "уби", "загин", "жертв", "бомб", "атентат", "терор", "клане", "избит", "избиван", "екзекут", "разстрел", "обесен",
        "катастроф", "земетрес", "войн", "битк", "сражени", "нападе", "нахлу", "окупир", "окупац", "геноцид", "самоуби",
        "умира", "почина", "смърт", "пожар", "взрив", "експлоз", "потъна", "потъва", "ранени", "погром", "атак", "въстани",
        "репрес", "концлагер", "депорт",
        "kill", "murder", "massacr", "attack", "terror", "warfare", "warship", "battle", "invad", "invasion", "assassin",
        "shoot", "death", "crash", "earthquake", "genocid", "hostage", "suicid", "wound", "casualt", "disaster", "hijack",
        "fatal", "lynch", "slave", "holocaust", "deport", "famine", "epidemic", "pandemic", "plague", "hurricane", "tsunami",
    )

    /** Whole words of the same kind (as beginnings they would also catch "Warsaw", "Bombay" or "explorer"). */
    private val SAD_WORDS = listOf(
        "war", "wars", "bomb", "bombs", "bombed", "bombing", "bombings", "bomber", "bombers", "executed", "execution",
        "executions", "shot", "dies", "died", "dead", "explosion", "explosions", "exploded", "sank", "sinks", "sunk", "riot",
        "riots", "coup", "fire", "fires", "burned", "burnt", "flood", "floods", "flooding",
    )

    private val SAD = Regex(
        "(?<!\\p{L})(?:" + SAD_STEMS.joinToString("|") + "|(?:" + SAD_WORDS.joinToString("|") + ")(?!\\p{L}))"
    )

    fun pleasant(e: Event): Boolean = !SAD.containsMatchIn(e.text.lowercase())

    /** One event for [date]: a pleasant one, the same all day, another one next year. */
    fun pick(events: List<Event>, date: LocalDate): Event? {
        val good = events.filter(::pleasant)
        if (good.isEmpty()) return null
        return good[Math.floorMod(date.year * 7 + date.dayOfYear, good.size)]
    }
}

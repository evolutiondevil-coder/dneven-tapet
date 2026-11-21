package bg.dnevnitapet.update

/** "Какво ново": the newest section of RELEASE_NOTES.md as short items. */
object ReleaseNotes {
    data class Item(val title: String, val text: String)

    /** The bullets of the first "## …" section (until the next section or the install help). */
    fun current(markdown: String): List<Item> {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.startsWith("## ") }
        if (start < 0) return emptyList()
        return lines.drop(start + 1)
            .takeWhile { !it.startsWith("## ") && !it.contains("Инсталиране") && !it.contains("**Install") }
            .filter { it.trimStart().startsWith("- ") }
            .map { line ->
                val clean = line.trim().removePrefix("- ").replace("**", "").trim()
                val colon = clean.indexOf(':')
                if (colon in 1..40) Item(clean.substring(0, colon).trim(), clean.substring(colon + 1).trim())
                else Item(clean, "")
            }
    }
}

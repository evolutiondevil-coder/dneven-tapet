package bg.dnevnitapet.art

/**
 * Wikimedia Commons' descriptions of paintings carry machine text for Wikidata ("label QS:Lfr,…",
 * "date QS:P571,+1884…") and long date phrases ("between 1884 and 1886"). This keeps only what a
 * person reads: the title, the painter, a short date and the description.
 */
object ArtText {
    /** "label QS:Len,"…"", "date QS:P571,+1884-00-00T…" and everything after it on that line. */
    private val qsTail = Regex("""\s*\b[A-Za-z]+\s*QS\s*:.*$""", RegexOption.IGNORE_CASE)

    /** The same machine text in the middle of a longer description. */
    private val qsPart = Regex("""\s*\b[A-Za-z]+\s*QS\s*:\s*[^\s,]*(,\s*(\+?[\dT:\-/Z]+|"[^"]*"|[A-Za-z]\d+))*""", RegexOption.IGNORE_CASE)

    /** "A Sunday on La Grande Jatte -- 1884 label QS:…" → "A Sunday on La Grande Jatte". */
    fun title(raw: String): String {
        var t = raw.replace(qsTail, "").trim()
        t = t.replace(Regex("""\s+-{2,}\s+"""), " – ")
        // The year at the end is shown separately.
        t = t.replace(Regex("""\s*[-–—,]\s*(c\.\s*)?\d{3,4}(\s*[-–]\s*\d{2,4})?$"""), "")
        t = t.trim().trim('"', '„', '“', '”', '\'').trim()
        return t
    }

    fun artist(raw: String): String = raw.replace(qsTail, "").substringBefore(" (").trim().trimEnd(',', ';')

    /** "between 1884 and 1886 date QS:…" → "1884–1886"; "circa 1665" → "ок. 1665"; "1889-06-01" → "1889". */
    fun date(raw: String, bulgarian: Boolean): String {
        val d = raw.replace(qsTail, "").trim()
        Regex("""between\s+(\d{3,4})\s+and\s+(\d{3,4})""", RegexOption.IGNORE_CASE).find(d)
            ?.let { return "${it.groupValues[1]}–${it.groupValues[2]}" }
        Regex("""\b(circa|ca\.?|c\.|about|around)\s*(\d{3,4})""", RegexOption.IGNORE_CASE).find(d)
            ?.let { return (if (bulgarian) "ок. " else "c. ") + it.groupValues[2] }
        Regex("""^(\d{4})-(0[1-9]|1[0-2])(-\d{2})?(T.*)?$""").find(d)?.let { return it.groupValues[1] }
        Regex("""^(\d{3,4})\s*[-–]\s*(\d{2,4})$""").find(d)?.let { return "${it.groupValues[1]}–${it.groupValues[2]}" }
        if (d.length > 16) Regex("""\b(\d{4})\b""").find(d)?.let { return it.groupValues[1] }
        return d
    }

    fun story(raw: String): String =
        raw.replace(qsPart, "").replace(Regex("""\s{2,}"""), " ").replace(Regex("""\s+([,.;:])"""), "$1").trim()

    /** The painting with all its texts cleaned (also for paintings kept from before this cleaning). */
    fun cleaned(a: Artwork, bulgarian: Boolean): Artwork {
        val artist = artist(a.artist)
        val title = title(a.title).ifBlank { title(CommonsArtParser.nameFromFile(a.id, artist)) }
        return a.copy(title = title, artist = artist, date = date(a.date, bulgarian), story = story(a.story))
    }
}

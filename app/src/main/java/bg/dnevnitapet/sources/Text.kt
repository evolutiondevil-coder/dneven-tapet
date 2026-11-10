package bg.dnevnitapet.sources

/** Small text clean-up helpers shared by sources. */
object Text {
    private val tagRegex = Regex("<[^>]+>")
    private val spaceRegex = Regex("\\s+")
    private val wikiLink = Regex("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)]]")
    private val wikiExternal = Regex("\\[https?://\\S+\\s+([^\\]]*)]")
    private val wikiTemplate = Regex("\\{\\{[^{}]*}}")
    private val numericEntity = Regex("&#(x?[0-9a-fA-F]+);")

    fun stripHtml(html: String): String {
        val noTags = tagRegex.replace(html, " ")
        return spaceRegex.replace(decodeEntities(noTags), " ").trim()
    }

    fun stripWiki(wikitext: String): String {
        var s = wikitext
        repeat(3) { s = wikiTemplate.replace(s, "") }
        s = wikiLink.replace(s) { it.groupValues[1] }
        s = wikiExternal.replace(s) { it.groupValues[1] }
        s = s.replace("'''", "").replace("''", "")
        return stripHtml(s)
    }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        var r = numericEntity.replace(s) { m ->
            val v = m.groupValues[1]
            val code = if (v.startsWith("x") || v.startsWith("X")) v.substring(1).toIntOrNull(16) else v.toIntOrNull()
            if (code != null) String(Character.toChars(code)) else m.value
        }
        r = r.replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&amp;", "&")
        return r
    }

    fun clip(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
}

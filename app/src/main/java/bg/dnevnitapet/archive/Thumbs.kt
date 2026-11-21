package bg.dnevnitapet.archive

import bg.dnevnitapet.model.Candidate

/** A small rendition of a picture for the archive's grid (asks the server for a small size when it can). */
object Thumbs {
    private const val NASA_ORIGINAL = "https://assets.science.nasa.gov/content/dam/science/"
    private const val NASA_DYNAMIC = "https://assets.science.nasa.gov/dynamicimage/assets/science/"
    private val wikiThumb = Regex("/\\d+px-")

    /** A rendition about [width] px wide (Wikimedia only has standard widths: 500, 960, 1280). */
    fun url(c: Candidate, width: Int = 480): String? {
        val v = c.variants.minByOrNull { if (it.width > 0) it.width else Int.MAX_VALUE } ?: return null
        val u = v.url.substringBefore("?utm_")
        val wiki = when {
            width <= 500 -> 500
            width <= 960 -> 960
            else -> 1280
        }
        return when {
            v.sizedTemplate -> u.replace("{w}", "$width").replace("{h}", "${width * 4 / 3}")
            "/thumb/" in u && wikiThumb.containsMatchIn(u) -> u.replace(wikiThumb, "/${wiki}px-")
            // An original file on Wikimedia (they limit those): its standard small rendition instead.
            "upload.wikimedia.org/wikipedia/commons/" in u && Regex("\\.(jpe?g|png)$", RegexOption.IGNORE_CASE).containsMatchIn(u) ->
                u.replace("/commons/", "/commons/thumb/") + "/${wiki}px-" + u.substringAfterLast('/')
            u.startsWith(NASA_ORIGINAL) -> u.replace(NASA_ORIGINAL, NASA_DYNAMIC).substringBefore('?') + "?w=$width&h=$width&fit=clip"
            u.startsWith(NASA_DYNAMIC) -> u.substringBefore('?') + "?w=$width&h=$width&fit=clip"
            else -> u
        }
    }
}

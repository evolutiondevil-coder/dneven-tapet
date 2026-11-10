package bg.dnevnitapet.sources.bing

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.ImageVariant
import bg.dnevnitapet.sources.FetchRequest
import bg.dnevnitapet.sources.FetchResult
import bg.dnevnitapet.sources.ImageSource
import bg.dnevnitapet.sources.SourceFormatException
import bg.dnevnitapet.sources.arr
import bg.dnevnitapet.sources.bool
import bg.dnevnitapet.sources.obj
import bg.dnevnitapet.sources.parseJson
import bg.dnevnitapet.sources.str

/**
 * Bing "picture of the day" archive (unofficial endpoint used by Bing itself).
 * Each market returns the last 8 days (idx=0) and another 8 older ones (idx=7).
 * Every picture has a portrait rendition 1080x1920 and a 3840x2160 (UHD) one.
 */
class BingSource : ImageSource {
    override val id = "bing"
    override val displayName = "Bing – снимка на деня"

    /** English markets first so that captions are in English whenever the same picture repeats. */
    private val plan = listOf(
        "en-US" to 0, "en-GB" to 0, "en-US" to 7, "en-IN" to 0, "en-CA" to 0, "en-AU" to 0,
        "en-GB" to 7, "en-IN" to 7, "de-DE" to 0, "fr-FR" to 0, "es-ES" to 0, "it-IT" to 0,
        "de-DE" to 7, "fr-FR" to 7,
    )

    override suspend fun fetch(request: FetchRequest): FetchResult {
        var index = request.cursor?.toIntOrNull()?.mod(plan.size) ?: 0
        val calls = mutableListOf<Pair<String, Int>>()
        if (request.wantFresh) calls += "en-US" to 0
        repeat(2) {
            calls += plan[index]
            index = (index + 1) % plan.size
        }
        val result = LinkedHashMap<String, Candidate>()
        var lastError: Exception? = null
        for ((market, idx) in calls.distinct()) {
            try {
                val json = request.http.getText(url(market, idx))
                BingParser.parse(json).forEach { result.putIfAbsent(it.key, it) }
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (result.isEmpty() && lastError != null) throw lastError
        return FetchResult(result.values.toList(), index.toString())
    }

    companion object {
        fun url(market: String, idx: Int) =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=$market"
    }
}

object BingParser {
    private const val HOST = "https://www.bing.com"

    fun parse(json: String): List<Candidate> {
        val images = parseJson(json).obj()?.get("images").arr()
            ?: throw SourceFormatException("Bing: липсва списък със снимки")
        return images.mapNotNull { element ->
            val o = element.obj() ?: return@mapNotNull null
            val urlBase = o.str("urlbase")?.takeIf { it.contains("id=") } ?: return@mapNotNull null
            // "wp": false means Bing does not allow the picture to be used as wallpaper.
            if (o.bool("wp") == false) return@mapNotNull null
            val imageId = urlBase.substringAfter("id=") // OHR.RoseIsland_EN-US0952497192
            val name = imageId.removePrefix("OHR.").substringBefore('_')
            if (name.isBlank()) return@mapNotNull null
            val (description, credit) = splitCopyright(o.str("copyright").orEmpty())
            val title = o.str("title")?.takeIf { it.isNotBlank() && it != "Info" } ?: description
            val base = HOST + urlBase
            Candidate(
                key = "bing:$name",
                sourceId = "bing",
                title = title,
                description = description,
                author = credit.removePrefix("©").trim(),
                copyright = credit,
                infoUrl = o.str("copyrightlink").orEmpty(),
                date = formatDate(o.str("startdate")),
                variants = listOf(
                    ImageVariant(base + "_1080x1920.jpg", 1080, 1920, portrait = true),
                    ImageVariant(base + "_UHD.jpg", 3840, 2160),
                    ImageVariant(base + "_1920x1080.jpg", 1920, 1080),
                ),
                fullUrl = base + "_UHD.jpg",
            )
        }
    }

    /** "Aerial view of Rose Island, The Bahamas (© Abstract Aerial Art/Getty Images)" */
    fun splitCopyright(s: String): Pair<String, String> {
        val i = s.lastIndexOf(" (")
        return if (i > 0 && s.endsWith(")")) {
            s.substring(0, i).trim() to s.substring(i + 2, s.length - 1).trim()
        } else {
            s.trim() to ""
        }
    }

    private fun formatDate(d: String?): String =
        if (d != null && d.length == 8 && d.all { it.isDigit() }) "${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)}" else ""
}

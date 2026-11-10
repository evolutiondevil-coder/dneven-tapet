package bg.dnevnitapet.sources.spotlight

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.ImageVariant
import bg.dnevnitapet.sources.FetchRequest
import bg.dnevnitapet.sources.FetchResult
import bg.dnevnitapet.sources.ImageSource
import bg.dnevnitapet.sources.SourceFormatException
import bg.dnevnitapet.sources.arr
import bg.dnevnitapet.sources.child
import bg.dnevnitapet.sources.int
import bg.dnevnitapet.sources.obj
import bg.dnevnitapet.sources.parseJson
import bg.dnevnitapet.sources.str

/**
 * Windows Spotlight (unofficial endpoints used by Windows itself).
 * v4 (current, Windows 11 desktop): 3840x2160 landscape + 1080x1920 portrait.
 * v3 (older, lock screen) is used as a fallback if v4 fails.
 * Only the picture data is read; the tracking/impression URLs in the answer are never called.
 */
class SpotlightSource : ImageSource {
    override val id = "spotlight"
    override val displayName = "Windows Spotlight"

    private val locales = listOf(
        "US" to "en-US", "GB" to "en-GB", "AU" to "en-AU", "CA" to "en-CA",
        "IN" to "en-IN", "NZ" to "en-NZ", "IE" to "en-IE",
    )

    override suspend fun fetch(request: FetchRequest): FetchResult {
        var index = request.cursor?.toIntOrNull()?.mod(locales.size) ?: 0
        val result = LinkedHashMap<String, Candidate>()
        var lastError: Exception? = null
        repeat(2) {
            val (country, locale) = locales[index]
            index = (index + 1) % locales.size
            val items = try {
                SpotlightParser.parseV4(request.http.getText(v4Url(country, locale)))
            } catch (e: Exception) {
                lastError = e
                try {
                    SpotlightParser.parseV3(request.http.getText(v3Url(country, locale)))
                } catch (e3: Exception) {
                    lastError = e3
                    emptyList()
                }
            }
            items.forEach { result.putIfAbsent(it.key, it) }
        }
        if (result.isEmpty() && lastError != null) throw lastError!!
        return FetchResult(result.values.toList(), index.toString())
    }

    companion object {
        fun v4Url(country: String, locale: String) =
            "https://fd.api.iris.microsoft.com/v4/api/selection?placement=88000820&bcnt=4&country=$country&locale=$locale&fmt=json"

        fun v3Url(country: String, locale: String) =
            "https://arc.msn.com/v3/Delivery/Placement?pid=338387&fmt=json&ua=WindowsShellClient%2F0&cdm=1" +
                "&lc=$locale&pl=$locale&ctry=$country&disphorzres=3840&dispvertres=2160"
    }
}

object SpotlightParser {
    private val sizeRegex = Regex("_(\\d{3,5})x(\\d{3,5})\\.(?:jpg|jpeg|png)", RegexOption.IGNORE_CASE)

    private fun items(json: String) =
        parseJson(json).obj().child("batchrsp")?.get("items").arr()
            ?: throw SourceFormatException("Spotlight: липсва списък")

    private fun innerAd(element: kotlinx.serialization.json.JsonElement) =
        element.obj().str("item")?.let { runCatching { parseJson(it).obj().child("ad") }.getOrNull() }

    fun parseV4(json: String): List<Candidate> = items(json).mapNotNull { element ->
        val ad = innerAd(element) ?: return@mapNotNull null
        val landscape = ad.child("landscapeImage").str("asset")
        val portrait = ad.child("portraitImage").str("asset")
        if (landscape == null && portrait == null) return@mapNotNull null
        val hover = ad.str("iconHoverText").orEmpty()
        val location = hover.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("©") }.orEmpty()
        val copyright = ad.str("copyright").orEmpty()
        val id = ad.str("entityId") ?: (portrait ?: landscape)!!.substringAfterLast('/')
        Candidate(
            key = "spotlight:$id",
            sourceId = "spotlight",
            title = ad.str("title")?.takeIf { it.isNotBlank() } ?: location,
            description = ad.str("description").orEmpty(),
            location = location,
            author = copyright.removePrefix("©").trim(),
            copyright = copyright,
            infoUrl = ad.str("ctaUri").orEmpty().removePrefix("microsoft-edge:"),
            variants = listOfNotNull(
                portrait?.let { variant(it, portrait = true) },
                landscape?.let { variant(it, portrait = false) },
            ),
            fullUrl = landscape ?: portrait!!,
        )
    }

    fun parseV3(json: String): List<Candidate> = items(json).mapNotNull { element ->
        val ad = innerAd(element) ?: return@mapNotNull null
        val landscape = ad.child("image_fullscreen_001_landscape")
        val portrait = ad.child("image_fullscreen_001_portrait")
        val lUrl = landscape.str("u")
        val pUrl = portrait.str("u")
        if (lUrl == null && pUrl == null) return@mapNotNull null
        val title = ad.child("title_text").str("tx").orEmpty()
        val copyright = ad.child("copyright_text").str("tx").orEmpty()
        val description = listOfNotNull(
            ad.child("hs1_title_text").str("tx"),
            ad.child("hs2_title_text").str("tx"),
        ).filter { it.isNotBlank() }.joinToString(" ")
        val infoUrl = (ad.child("title_destination_url").str("u") ?: ad.child("hs2_destination_url").str("u")).orEmpty()
        val spotlightId = Regex("spotlightid=([A-Za-z0-9_]+)").find(infoUrl)?.groupValues?.get(1)
        val id = spotlightId ?: (pUrl ?: lUrl)!!.substringAfterLast('/')
        Candidate(
            key = "spotlight:$id",
            sourceId = "spotlight",
            title = title,
            description = description,
            location = title,
            author = copyright.removePrefix("©").trim(),
            copyright = copyright,
            infoUrl = infoUrl.removePrefix("microsoft-edge:"),
            variants = listOfNotNull(
                pUrl?.let { ImageVariant(it, portrait.int("w") ?: 1080, portrait.int("h") ?: 1920, portrait = true) },
                lUrl?.let { ImageVariant(it, landscape.int("w") ?: 1920, landscape.int("h") ?: 1080) },
            ),
            fullUrl = lUrl ?: pUrl!!,
        )
    }

    private fun variant(url: String, portrait: Boolean): ImageVariant {
        val m = sizeRegex.find(url)
        val w = m?.groupValues?.get(1)?.toIntOrNull() ?: if (portrait) 1080 else 3840
        val h = m?.groupValues?.get(2)?.toIntOrNull() ?: if (portrait) 1920 else 2160
        return ImageVariant(url, w, h, portrait = portrait)
    }
}

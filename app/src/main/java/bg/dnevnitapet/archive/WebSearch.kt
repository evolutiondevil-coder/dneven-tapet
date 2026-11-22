package bg.dnevnitapet.archive

import bg.dnevnitapet.art.CommonsArtParser
import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.ImageVariant
import bg.dnevnitapet.net.Http
import bg.dnevnitapet.sources.Text
import bg.dnevnitapet.sources.arr
import bg.dnevnitapet.sources.child
import bg.dnevnitapet.sources.int
import bg.dnevnitapet.sources.obj
import bg.dnevnitapet.sources.parseJson
import bg.dnevnitapet.sources.str
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder

/** A picture found on the internet: shown from its address, nothing is kept on the phone. */
data class WebPicture(val candidate: Candidate, val thumbUrl: String)

/**
 * "Търсене": looks for pictures on the internet – the millions of free pictures of Wikimedia
 * Commons and NASA's image library. Nothing is stored; only the chosen picture is downloaded.
 */
class WebSearch(private val http: Http) {
    /** One page of results for [queries] (the user's words and, if different, their English translation). */
    suspend fun search(queries: List<String>, page: Int): List<WebPicture> = coroutineScope {
        val calls = queries.distinct().flatMap { q ->
            listOf(
                async { runCatching { commons(q, page) }.getOrDefault(emptyList()) },
                async { runCatching { NasaSearchParser.parse(http.getText(NasaSearchParser.url(q, page + 1))) }.getOrDefault(emptyList()) },
            )
        }
        interleave(calls.map { it.await() }).distinctBy { it.candidate.key }
    }

    private suspend fun commons(q: String, page: Int): List<WebPicture> {
        val good = CommonsSearchParser.parse(http.getText(CommonsSearchParser.url(q, page, large = true)))
        // The size filter is not known everywhere: then without it.
        return if (good.isEmpty() && page == 0) CommonsSearchParser.parse(http.getText(CommonsSearchParser.url(q, page, large = false))) else good
    }

    companion object {
        /** One from each list in turn, so both sources are seen from the start. */
        fun <T> interleave(lists: List<List<T>>): List<T> {
            val out = ArrayList<T>()
            var i = 0
            while (lists.any { i < it.size }) {
                lists.forEach { if (i < it.size) out += it[i] }
                i++
            }
            return out
        }
    }
}

/** Wikimedia Commons' search (files only, large pictures). Pure logic, unit-tested. */
object CommonsSearchParser {
    private const val API = "https://commons.wikimedia.org/w/api.php"
    private const val PAGE = 30
    private val px = Regex("/\\d+px-")

    fun url(q: String, page: Int, large: Boolean): String =
        "$API?action=query&format=json&formatversion=2&generator=search&gsrnamespace=6&gsrlimit=$PAGE&gsroffset=${page * PAGE}" +
            "&gsrsearch=" + enc(q + " filetype:bitmap" + if (large) " filew:>1600" else "") +
            "&prop=imageinfo&iiprop=" + enc("url|size|mime|extmetadata") + "&iiurlwidth=500" +
            "&iiextmetadatafilter=" + enc("ImageDescription|Artist|ObjectName|DateTimeOriginal|LicenseShortName") +
            "&iiextmetadatalanguage=en"

    fun parse(json: String): List<WebPicture> {
        val pages = parseJson(json).obj().child("query")?.get("pages").arr() ?: return emptyList()
        return pages.mapNotNull { el ->
            val page = el.obj() ?: return@mapNotNull null
            val title = page.str("title") ?: return@mapNotNull null
            val rank = page.int("index") ?: Int.MAX_VALUE
            val ii = page["imageinfo"].arr()?.firstOrNull().obj() ?: return@mapNotNull null
            val mime = ii.str("mime").orEmpty()
            if (mime != "image/jpeg" && mime != "image/png") return@mapNotNull null
            val width = ii.int("width") ?: 0
            val height = ii.int("height") ?: 0
            if (width < 1000 || height < 700) return@mapNotNull null
            val original = clean(ii.str("url") ?: return@mapNotNull null)
            val thumb = ii.str("thumburl")?.let(::clean)?.takeIf { px.containsMatchIn(it) }
                ?: CommonsArtParser.thumbOf(original, 500)
            val meta = ii.child("extmetadata")
            fun value(name: String) = Text.stripHtml(meta.child(name).str("value").orEmpty()).trim()
            val artist = Text.clip(value("Artist").lineSequence().firstOrNull().orEmpty(), 80)
            val fullW = if (width >= 3840) 3840 else if (width >= 1920) 1920 else 1280
            val full = thumb.replace(px, "/${fullW}px-")
            rank to WebPicture(
                Candidate(
                    key = "wikimedia:" + title.removePrefix("File:").replace(' ', '_'),
                    sourceId = "wikimedia",
                    title = Text.clip(value("ObjectName").lineSequence().firstOrNull().orEmpty().ifBlank { CommonsArtParser.nameFromFile(title, artist) }, 120),
                    description = Text.clip(value("ImageDescription"), 1200),
                    author = artist,
                    copyright = listOf(artist, value("LicenseShortName")).filter { it.isNotBlank() }.joinToString(", "),
                    infoUrl = clean(ii.str("descriptionurl").orEmpty()),
                    date = value("DateTimeOriginal").take(10).takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }.orEmpty(),
                    variants = listOf(
                        ImageVariant(full, fullW, (height.toLong() * fullW / width).toInt()),
                        ImageVariant(thumb.replace(px, "/1280px-"), 1280, (height.toLong() * 1280 / width).toInt()),
                    ).distinctBy { it.url },
                ),
                thumbUrl = thumb,
            )
        }.sortedBy { it.first }.map { it.second }
    }

    private fun clean(url: String) = url.substringBefore("?utm_").substringBefore("&utm_")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** NASA's image library (images.nasa.gov). Pure logic, unit-tested. */
object NasaSearchParser {
    fun url(q: String, page: Int) =
        "https://images-api.nasa.gov/search?media_type=image&page=$page&q=" + URLEncoder.encode(q, "UTF-8")

    fun parse(json: String): List<WebPicture> {
        val items = parseJson(json).obj().child("collection")?.get("items").arr() ?: return emptyList()
        return items.mapNotNull { el ->
            val item = el.obj() ?: return@mapNotNull null
            val data = item["data"].arr()?.firstOrNull().obj() ?: return@mapNotNull null
            val id = data.str("nasa_id") ?: return@mapNotNull null
            val thumb = item["links"].arr()?.mapNotNull { it.obj() }?.firstOrNull { it.str("rel") == "preview" }?.str("href")
                ?: return@mapNotNull null
            if (!thumb.contains("~thumb.")) return@mapNotNull null
            val large = thumb.replace("~thumb.", "~large.")
            val original = thumb.replace("~thumb.", "~orig.")
            WebPicture(
                Candidate(
                    key = "nasa:$id",
                    sourceId = "nasa",
                    title = Text.clip(Text.stripHtml(data.str("title").orEmpty()), 120),
                    description = Text.clip(Text.stripHtml(data.str("description").orEmpty()), 1200),
                    author = Text.clip(data.str("photographer") ?: data.str("center").orEmpty(), 80),
                    copyright = "NASA",
                    infoUrl = "https://images.nasa.gov/details/$id",
                    date = data.str("date_created").orEmpty().take(10),
                    // Sizes unknown: the large rendition (enough for a phone), the original as a fallback.
                    variants = listOf(ImageVariant(large), ImageVariant(original)),
                ),
                thumbUrl = thumb,
            )
        }
    }
}

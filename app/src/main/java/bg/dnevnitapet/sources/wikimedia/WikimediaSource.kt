package bg.dnevnitapet.sources.wikimedia

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.ImageVariant
import bg.dnevnitapet.sources.FetchRequest
import bg.dnevnitapet.sources.FetchResult
import bg.dnevnitapet.sources.ImageSource
import bg.dnevnitapet.sources.SourceFormatException
import bg.dnevnitapet.sources.Text
import bg.dnevnitapet.sources.arr
import bg.dnevnitapet.sources.child
import bg.dnevnitapet.sources.int
import bg.dnevnitapet.sources.obj
import bg.dnevnitapet.sources.parseJson
import bg.dnevnitapet.sources.str
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Wikimedia Commons "Picture of the day" through the official MediaWiki API.
 * Two small requests per batch of days: one expands the POTD templates (file name + English
 * caption for every day), one reads sizes, author and licence of those files.
 * The archive goes back to 2004, so "Next" never runs out of pictures.
 */
class WikimediaSource : ImageSource {
    override val id = "wikimedia"
    override val displayName = "Wikimedia Commons – снимка на деня"

    override suspend fun fetch(request: FetchRequest): FetchResult {
        val cursor = Cursor.parse(request.cursor)
        val today = request.today
        val days: List<LocalDate>
        val next: Cursor
        when {
            cursor == null -> {
                days = (0 until BATCH).map { today.minusDays(it.toLong()) }
                next = Cursor(newest = today, oldest = days.last())
            }
            request.wantFresh && cursor.newest < today -> {
                days = generateSequence(today) { it.minusDays(1) }
                    .takeWhile { it > cursor.newest }.take(BATCH).toList()
                next = cursor.copy(newest = today)
            }
            else -> {
                days = (1..BATCH).map { cursor.oldest.minusDays(it.toLong()) }
                next = cursor.copy(oldest = days.last())
            }
        }
        if (days.isEmpty()) return FetchResult(emptyList(), next.toString())

        val expanded = WikimediaParser.parseExpanded(request.http.getText(expandUrl(days)), days)
        if (expanded.isEmpty()) throw SourceFormatException("Wikimedia: няма снимки за тези дни")
        val titles = expanded.values.map { "File:" + it.file }
        val info = WikimediaParser.parseImageInfo(request.http.getText(imageInfoUrl(titles)))
        return FetchResult(WikimediaParser.build(expanded, info), next.toString())
    }

    data class Cursor(val newest: LocalDate, val oldest: LocalDate) {
        override fun toString() = "$newest|$oldest"

        companion object {
            fun parse(s: String?): Cursor? = runCatching {
                val (a, b) = s!!.split('|')
                Cursor(LocalDate.parse(a), LocalDate.parse(b))
            }.getOrNull()
        }
    }

    companion object {
        const val BATCH = 8
        private const val API = "https://commons.wikimedia.org/w/api.php"

        fun expandText(days: List<LocalDate>): String =
            days.joinToString(WikimediaParser.DAY_SEPARATOR) { "{{Potd/$it}}${WikimediaParser.FIELD_SEPARATOR}{{Potd/$it (en)}}" }

        fun expandUrl(days: List<LocalDate>) =
            "$API?action=expandtemplates&format=json&formatversion=2&prop=wikitext&text=" + enc(expandText(days))

        fun imageInfoUrl(titles: List<String>) =
            "$API?action=query&format=json&formatversion=2&prop=imageinfo" +
                "&iiprop=" + enc("url|size|mime|extmetadata") +
                "&iiurlwidth=3840" +
                "&iiextmetadatafilter=" + enc("ImageDescription|Artist|LicenseShortName|ObjectName") +
                "&iiextmetadatalanguage=en" +
                "&titles=" + enc(titles.joinToString("|"))

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

object WikimediaParser {
    const val DAY_SEPARATOR = "\n#D#\n"
    const val FIELD_SEPARATOR = "\n#C#\n"

    data class Expanded(val date: LocalDate, val file: String, val caption: String)

    data class Info(
        val title: String,
        val url: String,
        val width: Int,
        val height: Int,
        val mime: String,
        val thumbUrl: String?,
        val thumbWidth: Int,
        val thumbHeight: Int,
        val pageUrl: String,
        val objectName: String,
        val artist: String,
        val license: String,
        val description: String,
    )

    fun parseExpanded(json: String, days: List<LocalDate>): Map<LocalDate, Expanded> {
        val wikitext = parseJson(json).obj().child("expandtemplates").str("wikitext")
            ?: throw SourceFormatException("Wikimedia: неочакван отговор")
        val parts = wikitext.split(DAY_SEPARATOR.trim()).map { it.trim() }
        val result = LinkedHashMap<LocalDate, Expanded>()
        parts.forEachIndexed { i, part ->
            val day = days.getOrNull(i) ?: return@forEachIndexed
            val file = part.substringBefore(FIELD_SEPARATOR.trim()).trim()
            val caption = part.substringAfter(FIELD_SEPARATOR.trim(), "").trim()
            // A missing day expands to a red link / template call instead of a file name.
            if (file.isEmpty() || file.contains("{{") || file.contains("[[") || file.contains('\n')) return@forEachIndexed
            if (!Regex("\\.(jpe?g|png|webp|tiff?)$", RegexOption.IGNORE_CASE).containsMatchIn(file)) return@forEachIndexed
            result[day] = Expanded(day, file, if (caption.contains("{{Potd/")) "" else Text.stripWiki(caption))
        }
        return result
    }

    fun parseImageInfo(json: String): Map<String, Info> {
        val query = parseJson(json).obj().child("query") ?: throw SourceFormatException("Wikimedia: неочакван отговор")
        val result = HashMap<String, Info>()
        query["pages"].arr()?.forEach { pageEl ->
            val page = pageEl.obj() ?: return@forEach
            val title = page.str("title") ?: return@forEach
            val ii = page["imageinfo"].arr()?.firstOrNull().obj() ?: return@forEach
            val meta = ii.child("extmetadata")
            fun metaValue(name: String) = Text.stripHtml(meta.child(name).str("value").orEmpty())
            result[normalize(title)] = Info(
                title = title,
                url = clean(ii.str("url") ?: return@forEach),
                width = ii.int("width") ?: 0,
                height = ii.int("height") ?: 0,
                mime = ii.str("mime").orEmpty(),
                thumbUrl = ii.str("thumburl")?.let(::clean),
                thumbWidth = ii.int("thumbwidth") ?: 0,
                thumbHeight = ii.int("thumbheight") ?: 0,
                pageUrl = clean(ii.str("descriptionurl").orEmpty()),
                objectName = metaValue("ObjectName"),
                artist = metaValue("Artist"),
                license = metaValue("LicenseShortName"),
                description = metaValue("ImageDescription"),
            )
        }
        return result
    }

    fun build(expanded: Map<LocalDate, Expanded>, info: Map<String, Info>): List<Candidate> =
        expanded.values.mapNotNull { e ->
            val i = info[normalize("File:" + e.file)] ?: return@mapNotNull null
            val variants = variants(i)
            if (variants.isEmpty()) return@mapNotNull null
            val title = i.objectName.ifBlank { e.file.substringBeforeLast('.') }
            val copyright = listOf(i.artist, i.license).filter { it.isNotBlank() }.joinToString(", ")
            Candidate(
                key = "wikimedia:" + e.file.replace(' ', '_'),
                sourceId = "wikimedia",
                title = Text.clip(title, 120),
                description = Text.clip(e.caption.ifBlank { i.description }, 1500),
                author = Text.clip(i.artist, 160),
                copyright = Text.clip(copyright, 200),
                infoUrl = i.pageUrl,
                date = e.date.toString(),
                variants = variants,
                // For "save to gallery": the 3840 px rendition when there is one (originals can be 50+ MB).
                fullUrl = (variants.firstOrNull { it.width == 3840 } ?: variants.last()).url,
            )
        }

    /** Wikimedia only serves standard thumbnail widths (1280, 1920, 3840 ...); other widths fail. */
    private fun variants(i: Info): List<ImageVariant> {
        if (i.width <= 0 || i.height <= 0) return emptyList()
        val rasterOriginal = i.mime in setOf("image/jpeg", "image/png", "image/webp")
        if (!rasterOriginal && i.mime != "image/tiff") return emptyList()
        val list = mutableListOf<ImageVariant>()
        val thumb = i.thumbUrl
        if (thumb != null && thumb.contains("/3840px-")) {
            if (i.width > 1920) list += ImageVariant(thumb.replace("/3840px-", "/1920px-"), 1920, i.height * 1920 / i.width)
            if (i.width > 3840) list += ImageVariant(thumb, 3840, i.height * 3840 / i.width)
        }
        if (rasterOriginal) list += ImageVariant(i.url, i.width, i.height)
        else if (thumb != null && i.thumbWidth > 0) list += ImageVariant(thumb, i.thumbWidth, i.thumbHeight)
        return list.distinctBy { it.url }
    }

    private fun normalize(title: String) = title.replace('_', ' ').trim()

    /** Drops the tracking query string the API adds to file URLs. */
    private fun clean(url: String) = url.substringBefore("?utm_").substringBefore("&utm_")
}

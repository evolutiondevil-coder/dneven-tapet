package bg.dnevnitapet.sources.apod

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
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

/**
 * NASA Astronomy Picture of the Day.
 *
 * In 2026 APOD moved from apod.nasa.gov to science.nasa.gov, and the old api.nasa.gov/planetary/apod
 * endpoint now returns the NASA logo for every date. The pictures are now WordPress posts of type
 * "image-article" on science.nasa.gov, readable through the site's public REST API.
 * Video days (an embedded video instead of a picture) are skipped.
 */
class ApodSource : ImageSource {
    override val id = "apod"
    override val displayName = "NASA – астрономическа снимка на деня"

    override suspend fun fetch(request: FetchRequest): FetchResult {
        val nextPage = request.cursor?.toIntOrNull() ?: 1
        val pages = buildList {
            if (request.wantFresh && nextPage != 1) add(1)
            add(nextPage)
        }
        val result = LinkedHashMap<String, Candidate>()
        var lastError: Exception? = null
        for (page in pages) {
            try {
                ApodParser.parse(request.http.getText(url(page))).forEach { result.putIfAbsent(it.key, it) }
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (result.isEmpty() && lastError != null) throw lastError
        return FetchResult(result.values.toList(), (nextPage + 1).toString())
    }

    companion object {
        fun url(page: Int) =
            "https://science.nasa.gov/wp-json/wp/v2/image-article?per_page=8&page=$page&search=APOD" +
                "&_embed=wp:featuredmedia&_fields=id,date,link,title,content,featured_media,_links,_embedded"
    }
}

object ApodParser {
    private val heroLink = Regex("<a href=\"(https://assets\\.science\\.nasa\\.gov/content/dam/[^\"]+?\\.(?:jpe?g|png))\"", RegexOption.IGNORE_CASE)
    private val explanation = Regex("<strong>\\s*Explanation:?\\s*</strong>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val credit = Regex("(?:Image\\s+)?Credits?(?:\\s*(?:&|and)\\s*Copyright)?\\s*:?\\s*(.+?)\\s+Authors\\s*&\\s*editors", RegexOption.IGNORE_CASE)
    private val video = Regex("<video|<iframe[^>]+(?:youtube|vimeo)", RegexOption.IGNORE_CASE)
    private const val DYNAMIC = "https://assets.science.nasa.gov/dynamicimage/assets/science/"
    private const val ORIGINAL = "https://assets.science.nasa.gov/content/dam/science/"

    fun parse(json: String): List<Candidate> {
        val posts = parseJson(json).arr() ?: throw SourceFormatException("NASA: неочакван отговор")
        return posts.mapNotNull { el ->
            val post = el.obj() ?: return@mapNotNull null
            val rawTitle = Text.decodeEntities(post.child("title").str("rendered").orEmpty())
            if (!rawTitle.startsWith("APOD")) return@mapNotNull null
            val content = post.child("content").str("rendered").orEmpty()
            if (video.containsMatchIn(content)) return@mapNotNull null

            val media = post.child("_embedded")?.get("wp:featuredmedia").arr()?.firstOrNull().obj()
            val mediaUrl = media.str("source_url")
            val mime = media.str("mime_type").orEmpty()
            if (mime.isNotEmpty() && !mime.startsWith("image/")) return@mapNotNull null
            val details = media.child("media_details")
            val width = details.int("width") ?: 0
            val height = details.int("height") ?: 0

            val original = heroLink.find(content)?.groupValues?.get(1)
                ?: mediaUrl?.takeIf { it.startsWith(DYNAMIC) }?.replace(DYNAMIC, ORIGINAL)
                ?: mediaUrl
                ?: return@mapNotNull null
            val dynamic = when {
                original.startsWith(ORIGINAL) -> original.replace(ORIGINAL, DYNAMIC)
                mediaUrl?.startsWith(DYNAMIC) == true -> mediaUrl
                else -> null
            }

            val variants = buildList {
                if (dynamic != null && width > 3840 || dynamic != null && height > 3840) {
                    val scale = 3840.0 / maxOf(width, height)
                    add(ImageVariant("$dynamic?w=3840&h=3840&fit=clip", (width * scale).toInt(), (height * scale).toInt()))
                }
                add(ImageVariant(original, width, height))
            }

            val title = rawTitle.substringAfter(" – ", rawTitle.substringAfter(" - ", rawTitle)).trim()
            val text = Text.stripHtml(content)
            Candidate(
                key = "apod:" + original.substringAfterLast('/'),
                sourceId = "apod",
                title = title,
                description = Text.clip(explanation.find(content)?.groupValues?.get(1)?.let(Text::stripHtml).orEmpty(), 1500),
                author = Text.clip(credit.find(text)?.groupValues?.get(1)?.trim().orEmpty(), 200),
                copyright = "",
                infoUrl = post.str("link").orEmpty(),
                date = post.str("date").orEmpty().take(10),
                variants = variants,
                fullUrl = original,
                category = Category.SPACE,
            )
        }
    }
}

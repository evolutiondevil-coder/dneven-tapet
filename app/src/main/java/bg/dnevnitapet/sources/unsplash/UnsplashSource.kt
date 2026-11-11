package bg.dnevnitapet.sources.unsplash

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.model.ImageVariant
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.sources.FetchRequest
import bg.dnevnitapet.sources.FetchResult
import bg.dnevnitapet.sources.ImageSource
import bg.dnevnitapet.sources.SourceFormatException
import bg.dnevnitapet.sources.arr
import bg.dnevnitapet.sources.child
import bg.dnevnitapet.sources.int
import bg.dnevnitapet.sources.obj
import bg.dnevnitapet.sources.parseJson
import bg.dnevnitapet.sources.searchQuery
import bg.dnevnitapet.sources.str
import java.net.URLEncoder

/** Unsplash official API, with the user's own free "Access Key". Searches by category. */
class UnsplashSource : ImageSource {
    override val id = "unsplash"
    override val displayName = "Unsplash"
    override val needsKey = true

    override fun isConfigured(settings: Settings) = settings.unsplashKey.isNotBlank()

    override suspend fun fetch(request: FetchRequest): FetchResult {
        val categories = request.settings.enabledCategories.ifEmpty { Category.entries.toSet() }.toList()
        val category = categories[request.random.nextInt(categories.size)]
        val json = request.http.getText(url(category), headers(request.settings.unsplashKey))
        return FetchResult(UnsplashParser.parse(json, category), null)
    }

    companion object {
        fun url(category: Category) =
            "https://api.unsplash.com/photos/random?count=10&orientation=portrait&content_filter=high&query=" +
                URLEncoder.encode(category.searchQuery, "UTF-8")

        fun headers(key: String) = mapOf("Authorization" to "Client-ID ${key.trim()}", "Accept-Version" to "v1")
    }
}

object UnsplashParser {
    fun parse(json: String, category: Category?): List<Candidate> {
        val root = parseJson(json)
        val photos = root.arr() ?: root.obj()?.let { kotlinx.serialization.json.JsonArray(listOf(it)) }
            ?: throw SourceFormatException("Unsplash: неочакван отговор")
        return photos.mapNotNull { el ->
            val p = el.obj() ?: return@mapNotNull null
            val id = p.str("id") ?: return@mapNotNull null
            val raw = p.child("urls").str("raw") ?: return@mapNotNull null
            val width = p.int("width") ?: 0
            val height = p.int("height") ?: 0
            val sep = if (raw.contains('?')) "&" else "?"
            val user = p.child("user")
            val name = user.str("name").orEmpty()
            val title = p.str("description")?.takeIf { it.isNotBlank() }
                ?: p.str("alt_description")?.replaceFirstChar { it.uppercase() }
                ?: "Снимка от Unsplash"
            Candidate(
                key = "unsplash:$id",
                sourceId = "unsplash",
                title = title,
                description = p.str("alt_description").orEmpty().takeIf { it != title }.orEmpty(),
                location = p.child("location").str("name").orEmpty(),
                author = name,
                copyright = if (name.isNotBlank()) "$name / Unsplash" else "Unsplash",
                infoUrl = p.child("links").str("html").orEmpty(),
                variants = listOfNotNull(
                    ImageVariant("$raw${sep}w={w}&h={h}&fit=crop&crop=faces,entropy&q=90&fm=jpg", width, height, sizedTemplate = true),
                    // Uncut, 2400 px wide: used by the crop editor.
                    if (width > 0 && height > 0) {
                        val w = minOf(width, 2400)
                        ImageVariant("$raw${sep}w=$w&q=90&fm=jpg", w, height * w / width)
                    } else {
                        null
                    },
                ),
                fullUrl = p.child("urls").str("full") ?: raw,
                category = category,
                downloadPingUrl = p.child("links").str("download_location").orEmpty(),
            )
        }
    }
}

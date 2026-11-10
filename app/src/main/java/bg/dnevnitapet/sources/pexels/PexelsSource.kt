package bg.dnevnitapet.sources.pexels

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

/** Pexels official API, with the user's own free API key. Searches by category. */
class PexelsSource : ImageSource {
    override val id = "pexels"
    override val displayName = "Pexels"
    override val needsKey = true

    override fun isConfigured(settings: Settings) = settings.pexelsKey.isNotBlank()

    override suspend fun fetch(request: FetchRequest): FetchResult {
        val categories = request.settings.enabledCategories.ifEmpty { Category.entries.toSet() }.toList()
        val category = categories[request.random.nextInt(categories.size)]
        val page = 1 + request.random.nextInt(15)
        val json = request.http.getText(url(category, page), mapOf("Authorization" to request.settings.pexelsKey.trim()))
        return FetchResult(PexelsParser.parse(json, category), null)
    }

    companion object {
        fun url(category: Category, page: Int) =
            "https://api.pexels.com/v1/search?orientation=portrait&size=large&per_page=15&page=$page&query=" +
                URLEncoder.encode(category.searchQuery, "UTF-8")
    }
}

object PexelsParser {
    fun parse(json: String, category: Category?): List<Candidate> {
        val photos = parseJson(json).obj()?.get("photos").arr()
            ?: throw SourceFormatException("Pexels: неочакван отговор")
        return photos.mapNotNull { el ->
            val p = el.obj() ?: return@mapNotNull null
            val id = p.str("id") ?: return@mapNotNull null
            val original = p.child("src").str("original") ?: return@mapNotNull null
            val width = p.int("width") ?: 0
            val height = p.int("height") ?: 0
            val photographer = p.str("photographer").orEmpty()
            val base = original.substringBefore('?')
            Candidate(
                key = "pexels:$id",
                sourceId = "pexels",
                title = p.str("alt")?.takeIf { it.isNotBlank() } ?: "Снимка от Pexels",
                author = photographer,
                copyright = if (photographer.isNotBlank()) "$photographer / Pexels" else "Pexels",
                infoUrl = p.str("url").orEmpty(),
                variants = listOf(
                    ImageVariant("$base?cs=srgb&fit=crop&w={w}&h={h}", width, height, sizedTemplate = true),
                    ImageVariant(original, width, height),
                ),
                fullUrl = original,
                category = category,
            )
        }
    }
}

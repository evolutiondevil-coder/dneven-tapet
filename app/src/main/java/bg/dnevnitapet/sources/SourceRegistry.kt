package bg.dnevnitapet.sources

import bg.dnevnitapet.sources.apod.ApodSource
import bg.dnevnitapet.sources.bing.BingSource
import bg.dnevnitapet.sources.pexels.PexelsSource
import bg.dnevnitapet.sources.spotlight.SpotlightSource
import bg.dnevnitapet.sources.unsplash.UnsplashSource
import bg.dnevnitapet.sources.wikimedia.WikimediaSource

/** All known sources. Adding a new one = one class + one line here. */
object SourceRegistry {
    val all: List<ImageSource> = listOf(
        BingSource(),
        SpotlightSource(),
        WikimediaSource(),
        ApodSource(),
        UnsplashSource(),
        PexelsSource(),
    )

    fun byId(id: String): ImageSource? = all.firstOrNull { it.id == id }

    fun name(id: String): String = when (id) {
        "bing" -> "Bing"
        "spotlight" -> "Windows Spotlight"
        "wikimedia" -> "Wikimedia Commons"
        "apod" -> "NASA APOD"
        "unsplash" -> "Unsplash"
        "pexels" -> "Pexels"
        "mine" -> "Моите снимки"
        else -> id
    }
}

package bg.dnevnitapet.sources

import bg.dnevnitapet.sources.apod.ApodSource
import bg.dnevnitapet.sources.bing.BingSource
import bg.dnevnitapet.sources.pexels.PexelsSource
import bg.dnevnitapet.sources.spotlight.SpotlightSource
import bg.dnevnitapet.sources.unsplash.UnsplashSource
import bg.dnevnitapet.sources.wikimedia.WikimediaSource
import bg.dnevnitapet.i18n.tr

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

    /** Short names for one line listing several sources. */
    fun shortName(id: String): String = when (id) {
        "spotlight" -> "Spotlight"
        "wikimedia" -> "Wikimedia"
        "apod" -> "NASA"
        else -> name(id)
    }

    fun name(id: String): String = when (id) {
        "bing" -> "Bing"
        "spotlight" -> "Windows Spotlight"
        "wikimedia" -> "Wikimedia Commons"
        "apod" -> "NASA APOD"
        "nasa" -> "NASA"
        "unsplash" -> "Unsplash"
        "pexels" -> "Pexels"
        "mine" -> tr("Моите снимки")
        "art" -> tr("Изкуство")
        else -> id
    }
}

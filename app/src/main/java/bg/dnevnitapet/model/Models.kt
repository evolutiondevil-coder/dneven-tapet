package bg.dnevnitapet.model

import kotlinx.serialization.Serializable

@Serializable
enum class Category { NATURE, MOUNTAINS, SEA, CITIES, SPACE, ANIMALS, ARCHITECTURE }

/** One downloadable rendition of a picture. */
@Serializable
data class ImageVariant(
    val url: String,
    /** Pixel size of this rendition; 0 when the source does not say. */
    val width: Int = 0,
    val height: Int = 0,
    /** Composed by the source itself for a portrait (phone) screen. */
    val portrait: Boolean = false,
    /**
     * The URL contains `{w}` and `{h}` placeholders and the server crops the original
     * (of size [width] x [height]) to exactly that size, keeping the subject.
     */
    val sizedTemplate: Boolean = false,
)

/** A picture offered by a source, before it is downloaded. */
@Serializable
data class Candidate(
    /** Stable id, unique across sources, e.g. "bing:RoseIsland". */
    val key: String,
    val sourceId: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val author: String = "",
    val copyright: String = "",
    val infoUrl: String = "",
    /** yyyy-MM-dd when the source dates the picture (picture of the day). */
    val date: String = "",
    val variants: List<ImageVariant>,
    /** Highest-quality rendition, used for "save to gallery". */
    val fullUrl: String = "",
    val category: Category? = null,
    /** Unsplash requires this endpoint to be called when a photo is used. */
    val downloadPingUrl: String = "",
)

/** A picture that has been downloaded and cropped to the screen. */
@Serializable
data class StoredImage(
    val candidate: Candidate,
    val file: String,
    val width: Int,
    val height: Int,
    val addedAt: Long,
    val shownAt: Long = 0,
) {
    val key: String get() = candidate.key
}

@Serializable
data class SourceStatus(
    val lastSuccessAt: Long = 0,
    val lastErrorAt: Long = 0,
    val lastError: String = "",
    val lastCount: Int = 0,
    val lastFreshFetchAt: Long = 0,
    val lastAttemptAt: Long = 0,
)

@Serializable
data class LogEntry(val at: Long, val message: String)

@Serializable
data class LibraryState(
    /** Fetched candidates not yet downloaded. */
    val pool: List<Candidate> = emptyList(),
    /** Downloaded and cropped pictures, ready to be set instantly. */
    val queue: List<StoredImage> = emptyList(),
    val current: StoredImage? = null,
    /** Previously shown pictures, newest first (current not included). */
    val history: List<StoredImage> = emptyList(),
    val favorites: List<StoredImage> = emptyList(),
    val blocked: Set<String> = emptySet(),
    /** Keys already used or rejected, so they are not offered again. Newest last. */
    val seen: List<String> = emptyList(),
    val sourceStatus: Map<String, SourceStatus> = emptyMap(),
    val cursors: Map<String, String> = emptyMap(),
    val lastAutoChangeAt: Long = 0,
    val lastChangeAt: Long = 0,
    val lastSourceUsed: String = "",
    val log: List<LogEntry> = emptyList(),
) {
    fun isFavorite(key: String) = favorites.any { it.key == key }
}

@Serializable
enum class WallpaperTarget { HOME, LOCK, BOTH }

@Serializable
enum class FrequencyMode { DAILY, INTERVAL }

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val autoChange: Boolean = true,
    val target: WallpaperTarget = WallpaperTarget.HOME,
    val frequency: FrequencyMode = FrequencyMode.DAILY,
    /** Minutes after midnight for the daily change. */
    val dailyMinuteOfDay: Int = 5 * 60,
    val intervalHours: Int = 6,
    val wifiOnly: Boolean = false,
    val enabledSources: Set<String> = setOf("bing", "spotlight", "wikimedia", "apod", "unsplash", "pexels"),
    val enabledCategories: Set<Category> = Category.entries.toSet(),
    val unsplashKey: String = "",
    val pexelsKey: String = "",
)

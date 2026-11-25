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
    /** Language of the texts ("" = English); Bing's other markets write in their own language. */
    val lang: String = "",
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
    /** The rendition the wallpaper was cut from and the cut, as fractions 0..1 of that picture. */
    val sourceUrl: String = "",
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    /** True once the user adjusted the cut by hand. */
    val userCrop: Boolean = false,
) {
    val key: String get() = candidate.key
    val hasCrop: Boolean get() = cropRight > cropLeft && cropBottom > cropTop
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

/** "Албуми": a named group of the user's own photos (a photo can be in several). */
@Serializable
data class Album(val id: String, val name: String, val keys: List<String> = emptyList())

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
    /** The user's own photos picked from the phone's gallery (copied into the app, screen-sized). */
    val myPhotos: List<StoredImage> = emptyList(),
    /** Own photos still to come in the current round, in order (each round is shuffled). */
    val myRotation: List<String> = emptyList(),
    /** The lock screen's own picture ("Различни снимки на двата екрана"). */
    val lockCurrent: StoredImage? = null,
    /** Hidden pictures whose details are known, for learning the taste. Newest last. */
    val disliked: List<Candidate> = emptyList(),
    /** The user's albums of own photos ("Албуми", optional). */
    val albums: List<Album> = emptyList(),
    /** "Моят дневник": wallpapers put so far (changes at unlock not counted). */
    val shownCount: Int = 0,
    /** yyyy-MM-dd of the days the app was opened (the last 400), for "дни поред". */
    val openedDays: List<String> = emptyList(),
    /** yyyy-MM-dd of the first day with the app. */
    val firstDay: String = "",
) {
    fun isFavorite(key: String) = favorites.any { it.key == key }
}

@Serializable
enum class WallpaperTarget { HOME, LOCK, BOTH }

@Serializable
enum class FrequencyMode { DAILY, INTERVAL }

/** Where the wallpapers come from. */
@Serializable
enum class PhotoMode {
    /** Pictures from the internet sources (Bing, Spotlight, ...). */
    ONLINE,
    /** Only the user's own photos from the phone. */
    MINE,
    /** Own photos and internet pictures take turns. */
    MIXED,
}

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    /** "bg" or "en"; "" = not chosen yet (asked at the first start; older installs are Bulgarian). */
    val language: String = "",
    val autoChange: Boolean = true,
    val photoMode: PhotoMode = PhotoMode.ONLINE,
    val target: WallpaperTarget = WallpaperTarget.HOME,
    /**
     * Panorama: the home-screen wallpaper is wider than the screen and slides while the home
     * screens are swiped (shown by the app's own live wallpaper).
     */
    val panorama: Boolean = false,
    /** How wide the panorama is, in screen widths. */
    val panoramaScreens: Float = 2f,
    /** The picture moves a little when the phone is tilted (live wallpaper). */
    val tilt: Boolean = false,
    /** Darker (and optionally blurred) home-screen picture, so icons and labels stand out. */
    val readableIcons: ReadableMode = ReadableMode.OFF,
    /** In the evening and at night the home-screen picture is dimmed a little. */
    val eveningDim: Boolean = false,
    /** "Снимки според часа" (optional): each part of the day prefers its category. */
    val timeRules: Boolean = false,
    val morningCategory: Category? = Category.NATURE,
    val dayCategory: Category? = Category.CITIES,
    val eveningCategory: Category? = Category.SEA,
    val nightCategory: Category? = Category.SPACE,
    /** "Според сезона" (optional): winter snow, summer sea… */
    val seasonRules: Boolean = false,
    /** "Според времето навън" (optional): the weather in [weatherCity] (Open-Meteo) chooses the mood. */
    val weatherRules: Boolean = false,
    val weatherCity: String = "",
    /** "Албуми": own photos in albums; the home and the lock screen can each take their own album. */
    val albumsOn: Boolean = false,
    /** Album for the home screen ("" = all own photos). */
    val homeAlbum: String = "",
    /** Album for the lock screen ("" = all own photos). */
    val lockAlbum: String = "",
    /** "Нова снимка при всяко отключване": every time the screen goes off, another picture is put (live wallpaper). */
    val changeOnUnlock: Boolean = false,
    /** A double tap on an empty spot of the home screen brings the next picture (live wallpaper). */
    val doubleTapNext: Boolean = false,
    /** With "И двата": the lock screen gets a different picture at every change. */
    val lockDifferent: Boolean = false,
    /** Prefer the sources and kinds of pictures the user likes (♥) and not the hidden ones. */
    val learnTaste: Boolean = true,
    /** Look on GitHub for a newer version when the app starts. */
    val checkUpdates: Boolean = true,
    /** The version whose "Какво ново" was already shown. */
    val lastSeenVersion: String = "",
    /** yyyy-MM-dd of the last morning greeting ("Добро утро"), shown once a day. */
    val greetedDay: String = "",
    /** Titles and stories of the pictures in Bulgarian (automatic translation, language pack once over Wi-Fi). */
    val translateStories: Boolean = true,
    /** "Пауза за изкуство": after every 30 pictures browsed on the main screen, a famous painting and its story. */
    val artBreaks: Boolean = true,
    /** The first painting asked "show more?"; after that they come only when the answer was yes. */
    val artBreaksAsked: Boolean = false,
    val artBreaksAgreed: Boolean = false,
    /** Every opening of the app: the picture settles in and a short phrase appears ("Здравей отново"). */
    val openGreeting: Boolean = true,
    val frequency: FrequencyMode = FrequencyMode.DAILY,
    /** Minutes after midnight for the daily change. */
    val dailyMinuteOfDay: Int = 5 * 60,
    /** Change once every N days (1 = every day) at [dailyMinuteOfDay]. */
    val everyDays: Int = 1,
    val intervalHours: Int = 6,
    val wifiOnly: Boolean = false,
    val enabledSources: Set<String> = setOf("bing", "spotlight", "wikimedia", "apod", "unsplash", "pexels"),
    val enabledCategories: Set<Category> = Category.entries.toSet(),
    val unsplashKey: String = "",
    val pexelsKey: String = "",
) {
    /** Panorama that actually applies: only the home screen slides. */
    val panoramaOn: Boolean get() = panorama && target != WallpaperTarget.LOCK

    /** Paintings come: switched on, and either not asked yet (the first one asks) or agreed to. */
    val artOn: Boolean get() = artBreaks && (artBreaksAgreed || !artBreaksAsked)

    /** The home screen is drawn by the app's own live wallpaper (panorama, an effect, or unlock/double-tap changes). */
    val liveOn: Boolean
        get() = target != WallpaperTarget.LOCK &&
            (panorama || tilt || readableIcons != ReadableMode.OFF || eveningDim || changeOnUnlock || doubleTapNext)

    /** The lock screen's own album applies (own photos in use, albums on, an album chosen). */
    val lockAlbumOn: Boolean get() = albumsOn && lockAlbum.isNotBlank() && photoMode != PhotoMode.ONLINE

    /** The lock screen gets its own picture. */
    val lockSeparate: Boolean get() = target == WallpaperTarget.BOTH && (lockDifferent || lockAlbumOn)
}

@Serializable
enum class ReadableMode { OFF, DIM, DIM_BLUR }

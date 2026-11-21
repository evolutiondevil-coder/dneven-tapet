package bg.dnevnitapet.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.net.Http
import bg.dnevnitapet.sources.FetchRequest
import bg.dnevnitapet.sources.ImageSource
import bg.dnevnitapet.sources.apod.ApodSource
import bg.dnevnitapet.sources.wikimedia.WikimediaSource
import bg.dnevnitapet.wallpaper.MyPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

@Serializable
data class ArchiveEntry(
    val candidate: Candidate,
    /** Small preview file in the app ("" = none). */
    val thumb: String = "",
    /** Main colour ([ColorBucket]), "" = unknown. */
    val color: String = "",
    val addedAt: Long,
    /** When it was the wallpaper on this phone (0 = never). */
    val shownAt: Long = 0,
    /** Found while browsing NASA's and Wikimedia's archives (not shown or prepared on this phone). */
    val fromArchive: Boolean = false,
)

@Serializable
data class ArchiveState(
    /** Newest first; pictures found in the sources' archives at the end. */
    val entries: List<ArchiveEntry> = emptyList(),
    /** Where browsing each source's archive goes on. */
    val cursors: Map<String, String> = emptyMap(),
)

/**
 * "Архив": every picture prepared or shown on this phone (a small preview, its colour and texts),
 * plus older pictures from NASA's and Wikimedia's archives on request ("Още от архива").
 */
class Archive(private val context: Context, private val http: Http) {
    private val store = JsonFileStore(File(context.filesDir, "archive.json"), ArchiveState.serializer()) { ArchiveState() }
    val state = store.state
    private val dir = File(context.filesDir, "archive")
    private val mutex = Mutex()

    /** Adds the pictures of the library not yet in the archive (own photos stay out). */
    suspend fun record(items: List<StoredImage>) = mutex.withLock {
        val known = store.value.entries.associateBy { it.candidate.key }
        val fresh = items.filter { it.key !in known && !MyPhotos.isMine(it.key) && File(it.file).exists() }.distinctBy { it.key }
        val shown = items.filter { it.shownAt > 0 && known[it.key]?.let { e -> e.shownAt < it.shownAt } == true }.associateBy { it.key }
        if (fresh.isEmpty() && shown.isEmpty()) return@withLock
        val made = withContext(Dispatchers.IO) {
            fresh.map { item ->
                val thumb = File(dir, name(item.key))
                val color = runCatching { makeThumb(File(item.file), thumb) }.getOrNull()
                ArchiveEntry(
                    candidate = trim(item.candidate),
                    thumb = if (color != null) thumb.path else "",
                    color = color.orEmpty(),
                    addedAt = item.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    shownAt = item.shownAt,
                )
            }
        }
        store.update { st ->
            val updated = st.entries.map { e -> shown[e.candidate.key]?.let { e.copy(shownAt = it.shownAt, fromArchive = false) } ?: e }
            st.copy(entries = (made + updated).take(MAX_ENTRIES))
        }
        cleanup()
    }

    /** Older pictures from NASA's and Wikimedia's archives. @return how many were added. */
    suspend fun loadMore(settings: Settings): Int = mutex.withLock {
        val today = LocalDate.now()
        var added = 0
        val sources: List<ImageSource> = listOf(WikimediaSource(), ApodSource())
        for (source in sources) {
            val cursor = store.value.cursors[source.id] ?: startCursor(source.id, today)
            val result = runCatching {
                source.fetch(FetchRequest(http, settings, cursor, wantFresh = false, today = today))
            }.getOrNull() ?: continue
            val known = store.value.entries.map { it.candidate.key }.toSet()
            val made = withContext(Dispatchers.IO) {
                result.candidates.filter { it.key !in known }.mapNotNull { c -> fromUrl(c) }
            }
            store.update { st ->
                st.copy(
                    entries = (st.entries + made).take(MAX_ENTRIES),
                    cursors = st.cursors + (source.id to (result.cursor ?: cursor)),
                )
            }
            added += made.size
        }
        cleanup()
        added
    }

    /** Wikimedia from a month back (newer ones come as usual), NASA from its fourth page. */
    private fun startCursor(sourceId: String, today: LocalDate): String = when (sourceId) {
        "wikimedia" -> "$today|${today.minusDays(30)}"
        else -> "4"
    }

    private suspend fun fromUrl(c: Candidate): ArchiveEntry? {
        val url = Thumbs.url(c) ?: return null
        val tmp = File(context.cacheDir, "archive-" + name(c.key) + ".part")
        return try {
            http.downloadImage(url, tmp, maxBytes = 12L * 1024 * 1024)
            val thumb = File(dir, name(c.key))
            val color = makeThumb(tmp, thumb) ?: return null
            ArchiveEntry(trim(c), thumb.path, color, addedAt = System.currentTimeMillis(), fromArchive = true)
        } catch (e: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    /** Writes a small preview (about 240 px wide) of [src] to [dest]. @return its main colour, or null. */
    private fun makeThumb(src: File, dest: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_W) sample *= 2
        val decoded = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val h = (decoded.height.toLong() * THUMB_W / decoded.width).toInt().coerceAtLeast(1)
        val small = if (decoded.width > THUMB_W) Bitmap.createScaledBitmap(decoded, THUMB_W, h, true) else decoded
        dir.mkdirs()
        dest.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        val tiny = Bitmap.createScaledBitmap(small, 32, (32L * small.height / small.width).toInt().coerceAtLeast(1), true)
        val pixels = IntArray(tiny.width * tiny.height)
        tiny.getPixels(pixels, 0, tiny.width, 0, 0, tiny.width, tiny.height)
        if (tiny !== small) tiny.recycle()
        if (small !== decoded) small.recycle()
        decoded.recycle()
        return ColorBucket.of(pixels)
    }

    /** Long stories are kept shorter in the archive. */
    private fun trim(c: Candidate) = c.copy(description = c.description.take(1200))

    private fun cleanup() {
        val used = store.value.entries.map { File(it.thumb).name }.toSet()
        dir.listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    private fun name(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(20) + ".jpg"

    companion object {
        private const val THUMB_W = 240
        private const val MAX_ENTRIES = 900
    }
}

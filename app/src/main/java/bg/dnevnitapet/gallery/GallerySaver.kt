package bg.dnevnitapet.gallery

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.net.Http
import bg.dnevnitapet.wallpaper.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a picture to the phone's gallery (Pictures/Дневен тапет). Tries the full-resolution original
 * first; without internet it saves the wallpaper-sized copy.
 * Android 10+ needs no permission; Android 7–9 needs "storage" (asked by the screen before saving).
 */
class GallerySaver(private val context: Context, private val http: Http) {

    /** True when the screen must ask for the storage permission first (Android 7–9 only). */
    fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    suspend fun save(image: StoredImage, settings: Settings): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.cacheDir, "gallery-download.tmp")
            try {
                var source = File(image.file)
                var original = false
                val url = image.candidate.fullUrl
                if (url.isNotBlank() && !url.contains("{w}") && DeviceInfo.isNetworkAllowed(context, settings)) {
                    try {
                        http.downloadImage(url, tmp)
                        source = tmp
                        original = true
                    } catch (_: Exception) {
                        // fall back to the local copy
                    }
                }
                if (!source.exists()) error("Файлът липсва")

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                val mime = bounds.outMimeType ?: "image/jpeg"
                val ext = when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val name = image.candidate.title
                    .replace(Regex("[\\\\/:*?\"<>|]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(60)
                    .ifBlank { "tapet" }
                if (Build.VERSION.SDK_INT >= 29) saveModern(source, "$name.$ext", mime) else saveLegacy(source, name, ext, mime)
                original
            } finally {
                tmp.delete()
            }
        }
    }

    private fun saveModern(source: File, fileName: String, mime: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Галерията отказа записа")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("Записът не успя")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(source: File, name: String, ext: String, mime: String) {
        if (needsPermission()) error("Няма разрешение за запис на снимки")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!dir.exists() && !dir.mkdirs()) error("Не мога да създам папката за снимки")
        var dest = File(dir, "$name.$ext")
        var n = 2
        while (dest.exists()) dest = File(dir, "$name ($n).$ext").also { n++ }
        source.copyTo(dest)
        MediaScannerConnection.scanFile(context, arrayOf(dest.path), arrayOf(mime), null)
    }

    companion object {
        const val ALBUM = "Дневен тапет"
    }
}

package bg.dnevnitapet.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.net.Http
import bg.dnevnitapet.wallpaper.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a picture to the phone's gallery (Pictures/Дневен тапет). Tries the full-resolution original
 * first; without internet it saves the wallpaper-sized copy. Needs no permission on Android 10+.
 */
class GallerySaver(private val context: Context, private val http: Http) {

    suspend fun save(image: StoredImage, settings: Settings): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.cacheDir, "gallery-download.tmp")
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
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Дневен тапет")
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
            } finally {
                tmp.delete()
            }
            original
        }
    }
}

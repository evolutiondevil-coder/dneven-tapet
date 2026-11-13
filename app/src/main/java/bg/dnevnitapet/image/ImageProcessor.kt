package bg.dnevnitapet.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.PointF
import android.media.FaceDetector
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImageTooSmallException(message: String) : IOException(message)

/**
 * Turns a downloaded picture into a wallpaper file of exactly the screen's size (or, for the
 * panorama, the screen's height and up to a few screens wide):
 * finds the crop that keeps the subject, decodes only that region (big originals never
 * have to fit in memory) and saves a high-quality JPEG.
 */
class ImageProcessor {

    /** A part of the upright source picture, as fractions 0..1 of its width and height. */
    data class CropFraction(val left: Float, val top: Float, val right: Float, val bottom: Float)

    data class Output(val width: Int, val height: Int, val crop: CropFraction)

    /** Small upright copy of a picture for the crop editor, plus the source's real size. */
    class Preview(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    /**
     * @param manualCrop the cut chosen by the user in the crop editor; null = automatic
     * (centred for pictures already composed for portrait, "smart" for the others).
     */
    fun process(
        src: File,
        dest: File,
        targetW: Int,
        targetH: Int,
        composedForPortrait: Boolean,
        manualCrop: CropFraction? = null,
        /** The user's own photos are used even when small (they chose them). */
        allowUpscale: Boolean = false,
        /** Panorama: the file may be up to this wide (wide pictures); [targetW] = always screen-wide. */
        maxTargetW: Int = targetW,
    ): Output {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Файлът не е снимка")

        val rotation = exifRotation(src)
        val (w, h) = if (rotation == 90 || rotation == 270) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
        val outW = WallpaperSize.width(targetW, targetH, maxTargetW, w, h)
        val aspect = outW.toDouble() / targetH
        if (manualCrop == null && !allowUpscale) {
            val upscale = targetH / min(h.toDouble(), w / aspect)
            if (upscale > VariantChooser.MAX_UPSCALE) {
                throw ImageTooSmallException("Снимката е с твърде ниска резолюция (${w}x$h)")
            }
        }

        val crop = when {
            manualCrop != null -> fromFraction(manualCrop, w, h, aspect)
            composedForPortrait -> SmartCrop.centreCrop(w, h, aspect)
            else -> smartCrop(src, rotation, w, h, aspect)
        }
        val bitmap = if (rotation == 0) decodeRegion(src, crop, outW, targetH) else decodeRotated(src, rotation, crop, w, outW, targetH)
        try {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            val fraction = CropFraction(
                crop.left.toFloat() / w, crop.top.toFloat() / h,
                crop.right.toFloat() / w, crop.bottom.toFloat() / h,
            )
            return Output(bitmap.width, bitmap.height, fraction)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Saves an upright copy of [src], reduced to at most [maxSide] px, as a JPEG (EXIF rotation applied).
     * Used to keep the user's own photos inside the app.
     */
    fun saveUprightCopy(src: File, dest: File, maxSide: Int): Pair<Int, Int> {
        val preview = loadPreview(src, maxSide)
        try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { preview.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            return preview.bitmap.width to preview.bitmap.height
        } finally {
            preview.bitmap.recycle()
        }
    }

    /** Upright, reduced copy for showing in the crop editor. */
    fun loadPreview(src: File, maxSide: Int): Preview {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Файлът не е снимка")
        val rotation = exifRotation(src)
        val decoded = decodeSampled(src, maxSide) ?: throw IOException("Снимката не може да се декодира")
        val upright = rotate(decoded, rotation)
        val (w, h) = if (rotation == 90 || rotation == 270) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
        return Preview(upright, w, h)
    }

    /** Turns fractions into a pixel rectangle with exactly the screen's aspect ratio, inside the picture. */
    private fun fromFraction(c: CropFraction, w: Int, h: Int, aspect: Double): SmartCrop.Rect {
        val cx = (c.left + c.right) / 2.0 * w
        val cy = (c.top + c.bottom) / 2.0 * h
        var ch = ((c.bottom - c.top) * h).toDouble().coerceAtLeast(16.0)
        var cw = ch * aspect
        if (cw > w) { cw = w.toDouble(); ch = cw / aspect }
        if (ch > h) { ch = h.toDouble(); cw = ch * aspect }
        val left = (cx - cw / 2).coerceIn(0.0, w - cw)
        val top = (cy - ch / 2).coerceIn(0.0, h - ch)
        return SmartCrop.Rect(left.roundToInt(), top.roundToInt(), cw.roundToInt().coerceAtMost(w), ch.roundToInt().coerceAtMost(h))
    }

    private fun smartCrop(src: File, rotation: Int, w: Int, h: Int, aspect: Double): SmartCrop.Rect {
        val analysis = decodeSampled(src, ANALYSIS_SIDE) ?: throw IOException("Снимката не може да се декодира")
        val upright = rotate(analysis, rotation)
        try {
            return cropFromAnalysis(upright, w, h, aspect)
        } finally {
            upright.recycle()
        }
    }

    /** Decodes only the needed part of the file, so big originals never have to fit in memory. */
    private fun decodeRegion(src: File, crop: SmartCrop.Rect, targetW: Int, targetH: Int): Bitmap {
        var sample = 1
        while (crop.height / (sample * 2) >= targetH && crop.width / (sample * 2) >= targetW) sample *= 2

        val decoder = if (Build.VERSION.SDK_INT >= 31) {
            BitmapRegionDecoder.newInstance(src.path)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(src.path, false)
        } ?: throw IOException("Снимката не може да се декодира")
        val region = try {
            decoder.decodeRegion(
                android.graphics.Rect(crop.left, crop.top, crop.right, crop.bottom),
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: throw IOException("Снимката не може да се декодира")
        } finally {
            decoder.recycle()
        }
        return scaleExactly(region, targetW, targetH)
    }

    /** Rare case (camera originals with an EXIF rotation flag): decode reduced, rotate, then cut. */
    private fun decodeRotated(src: File, rotation: Int, crop: SmartCrop.Rect, uprightW: Int, targetW: Int, targetH: Int): Bitmap {
        var sample = 1
        while (crop.height / (sample * 2) >= targetH && crop.width / (sample * 2) >= targetW) sample *= 2
        val decoded = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Снимката не може да се декодира")
        val upright = rotate(decoded, rotation)
        val f = upright.width.toDouble() / uprightW
        val left = (crop.left * f).roundToInt().coerceIn(0, upright.width - 1)
        val top = (crop.top * f).roundToInt().coerceIn(0, upright.height - 1)
        val cw = (crop.width * f).roundToInt().coerceIn(1, upright.width - left)
        val ch = (crop.height * f).roundToInt().coerceIn(1, upright.height - top)
        val cut = Bitmap.createBitmap(upright, left, top, cw, ch)
        if (cut !== upright) upright.recycle()
        return scaleExactly(cut, targetW, targetH)
    }

    private fun rotate(bitmap: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Runs [SmartCrop] on a small copy and maps the result back to full-size coordinates. */
    private fun cropFromAnalysis(analysis: Bitmap, fullW: Int, fullH: Int, aspect: Double): SmartCrop.Rect {
        val aw = analysis.width
        val ah = analysis.height
        val pixels = IntArray(aw * ah)
        analysis.getPixels(pixels, 0, aw, 0, 0, aw, ah)
        val small = SmartCrop.findCrop(pixels, aw, ah, aspect, detectFaces(analysis))
        val sx = fullW.toDouble() / aw
        val sy = fullH.toDouble() / ah
        val cropW = min(fullW, (fullH * aspect).roundToInt())
        val cropH = min(fullH, (fullW / aspect).roundToInt())
        val left = (small.left * sx).roundToInt().coerceIn(0, fullW - cropW)
        val top = (small.top * sy).roundToInt().coerceIn(0, fullH - cropH)
        return SmartCrop.Rect(left, top, cropW, cropH)
    }

    private fun detectFaces(bitmap: Bitmap): List<SmartCrop.Boost> = try {
        val evenW = bitmap.width - bitmap.width % 2
        val copy = Bitmap.createBitmap(evenW, bitmap.height, Bitmap.Config.RGB_565)
        android.graphics.Canvas(copy).drawBitmap(bitmap, 0f, 0f, null)
        val faces = arrayOfNulls<FaceDetector.Face>(5)
        val found = FaceDetector(evenW, bitmap.height, faces.size).findFaces(copy, faces)
        copy.recycle()
        (0 until found).mapNotNull { i ->
            val face = faces[i] ?: return@mapNotNull null
            if (face.confidence() < 0.4f) return@mapNotNull null
            val mid = PointF().also(face::getMidPoint)
            val e = face.eyesDistance()
            SmartCrop.Boost((mid.x - 1.3f * e).toInt(), (mid.y - 1.3f * e).toInt(), (mid.x + 1.3f * e).toInt(), (mid.y + 2.0f * e).toInt())
        }
    } catch (e: Throwable) {
        emptyList()
    }

    private fun decodeSampled(src: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val fitted = scaleToFit(decoded, maxSide)
        if (fitted !== decoded) decoded.recycle()
        return fitted
    }

    private fun scaleToFit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toDouble() / longest
        return Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * f).roundToInt()), max(1, (bitmap.height * f).roundToInt()), true)
    }

    private fun scaleExactly(bitmap: Bitmap, w: Int, h: Int): Bitmap {
        if (bitmap.width == w && bitmap.height == h) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun exifRotation(file: File): Int = try {
        when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    companion object {
        private const val ANALYSIS_SIDE = 400
    }
}

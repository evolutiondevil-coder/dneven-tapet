package bg.dnevnitapet.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.DisplayMetrics
import android.view.Display
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.WallpaperTarget
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

/** Sets the system wallpaper from a prepared file. */
class WallpaperSetter(private val context: Context) {
    /**
     * @param panorama the panorama is on: while the app's live wallpaper is on the home screen it
     * only gets the new file (and slides it); the lock screen gets the middle part.
     * @return the system's id of the new wallpaper (> 0 on success).
     */
    fun apply(file: File, target: WallpaperTarget, panorama: Boolean = false): Int {
        val wm = WallpaperManager.getInstance(context)
        if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) {
            throw IllegalStateException("Устройството не позволява смяна на тапета")
        }
        val (sw, sh) = DeviceInfo.screenSize(context)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("Файлът на снимката е повреден")
        // Wider than the screen's shape: a panorama file.
        val wide = bounds.outWidth.toLong() * sh > bounds.outHeight.toLong() * sw * 21 / 20
        if (target != WallpaperTarget.LOCK) Panorama.setFile(context, file.path)

        if (panorama && target != WallpaperTarget.LOCK && Panorama.isActive(context)) {
            // The home screen is drawn by the panorama live wallpaper, which reloads by itself.
            if (target == WallpaperTarget.BOTH) setLock(wm, file, wide, sw, sh)
            return max(1, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        }
        if (target != WallpaperTarget.LOCK) Panorama.hintSize(context, wm, bounds.outWidth, bounds.outHeight, sw, sh)
        if (!wide) return stream(wm, file, flags(target))
        return when (target) {
            WallpaperTarget.LOCK -> setLock(wm, file, true, sw, sh)
            WallpaperTarget.HOME -> stream(wm, file, WallpaperManager.FLAG_SYSTEM)
            WallpaperTarget.BOTH -> stream(wm, file, WallpaperManager.FLAG_SYSTEM).also { setLock(wm, file, true, sw, sh) }
        }
    }

    /** Panorama switched off: the home screen gets a plain wallpaper again instead of the live one. */
    fun leavePanorama(file: File) {
        if (!Panorama.isActive(context)) return
        val wm = WallpaperManager.getInstance(context)
        val (sw, sh) = DeviceInfo.screenSize(context)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        Panorama.hintSize(context, wm, sw, sh, sw, sh)
        // Set for "home and lock" in the system's preview: the lock screen gets a plain picture too.
        // (the lock screen either shares the home wallpaper or has the live one itself).
        val lockShares = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_LOCK) <= 0 }.getOrDefault(false)
        val lockIsOurs = Build.VERSION.SDK_INT >= 34 &&
            runCatching { wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.packageName == context.packageName }.getOrDefault(false)
        val lockToo = lockShares || lockIsOurs
        val flags = WallpaperManager.FLAG_SYSTEM or (if (lockToo) WallpaperManager.FLAG_LOCK else 0)
        if (bounds.outWidth.toLong() * sh > bounds.outHeight.toLong() * sw * 21 / 20) {
            middle(file, sw, sh).let { bmp ->
                try { wm.setBitmap(bmp, null, true, flags) } finally { bmp.recycle() }
            }
        } else {
            stream(wm, file, flags)
        }
    }

    private fun flags(target: WallpaperTarget) = when (target) {
        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

    private fun stream(wm: WallpaperManager, file: File, flags: Int): Int {
        // The file is already prepared, so it is handed over as is (no re-encoding).
        val id = FileInputStream(file).use { wm.setStream(it, null, true, flags) }
        if (id <= 0) throw IllegalStateException("Системата не прие тапета")
        return id
    }

    /** The lock screen does not slide: it gets the middle, screen-shaped part of a panorama. */
    private fun setLock(wm: WallpaperManager, file: File, wide: Boolean, sw: Int, sh: Int): Int {
        if (!wide) return stream(wm, file, WallpaperManager.FLAG_LOCK)
        val bmp = middle(file, sw, sh)
        try {
            val id = wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
            if (id <= 0) throw IllegalStateException("Системата не прие тапета")
            return id
        } finally {
            bmp.recycle()
        }
    }

    /** Decodes only the middle part of [file] that has the screen's shape. */
    private fun middle(file: File, sw: Int, sh: Int): Bitmap {
        val decoder = if (Build.VERSION.SDK_INT >= 31) {
            BitmapRegionDecoder.newInstance(file.path)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(file.path, false)
        } ?: throw IllegalStateException("Снимката не може да се декодира")
        try {
            val w = decoder.width
            val h = decoder.height
            val cw = min(w, (h.toLong() * sw / sh).toInt()).coerceAtLeast(1)
            val left = (w - cw) / 2
            return decoder.decodeRegion(Rect(left, 0, left + cw, h), BitmapFactory.Options())
                ?: throw IllegalStateException("Снимката не може да се декодира")
        } finally {
            decoder.recycle()
        }
    }
}

object DeviceInfo {
    /** Physical screen size in portrait orientation (width < height). */
    fun screenSize(context: Context): Pair<Int, Int> {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        } else {
            metrics.setTo(context.resources.displayMetrics)
        }
        var w = min(metrics.widthPixels, metrics.heightPixels)
        var h = max(metrics.widthPixels, metrics.heightPixels)
        if (w <= 0 || h <= 0) {
            w = 1080; h = 2340
        }
        return w to h
    }

    fun isNetworkAllowed(context: Context, settings: Settings): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (settings.wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        return true
    }
}

package bg.dnevnitapet.wallpaper

import android.app.WallpaperManager
import android.content.Context
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
    /** @return the system's id of the new wallpaper (> 0 on success). */
    fun apply(file: File, target: WallpaperTarget): Int {
        val wm = WallpaperManager.getInstance(context)
        if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) {
            throw IllegalStateException("Устройството не позволява смяна на тапета")
        }
        val flags = when (target) {
            WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        // The file is already exactly screen-sized, so it is handed over as is (no re-encoding).
        val id = FileInputStream(file).use { wm.setStream(it, null, true, flags) }
        if (id <= 0) throw IllegalStateException("Системата не прие тапета")
        return id
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

package bg.dnevnitapet.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import bg.dnevnitapet.image.WallpaperSize
import bg.dnevnitapet.model.Settings

/**
 * The panorama: the home screen shows the app's own live wallpaper ([PanoramaWallpaperService]),
 * which draws the current picture wider than the screen and slides it with the home screens.
 */
object Panorama {
    private const val PREFS = "panorama"
    const val KEY_FILE = "file"
    private const val KEY_HINTED = "hinted"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The picture the live wallpaper shows (always the current home-screen wallpaper). */
    fun file(context: Context): String? = prefs(context).getString(KEY_FILE, null)

    fun setFile(context: Context, path: String) {
        prefs(context).edit().putString(KEY_FILE, path).apply()
    }

    /** True while the home screen shows this app's live wallpaper. */
    fun isActive(context: Context): Boolean = runCatching {
        val info = WallpaperManager.getInstance(context).wallpaperInfo
        info != null && info.packageName == context.packageName
    }.getOrDefault(false)

    /** Widest wallpaper file for these settings (the screen width when the panorama is off). */
    fun maxWidth(screenW: Int, settings: Settings): Int =
        if (settings.panoramaOn) WallpaperSize.maxWidth(screenW, settings.panoramaScreens) else screenW

    /** The system screen that previews the panorama and sets it ("Задай тапет"). */
    fun pickerIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, PanoramaWallpaperService::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Fallback when a phone has no direct preview screen: the list of live wallpapers. */
    fun chooserIntent(): Intent =
        Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the system screen to switch the panorama on. @return false if the phone has none. */
    fun openPicker(context: Context): Boolean {
        for (intent in listOf(pickerIntent(context), chooserIntent())) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    /**
     * Tells the system how wide the wallpaper is, so that launchers which slide plain wallpapers
     * do so too (used when the live wallpaper is not switched on). Back to the screen size afterwards.
     */
    fun hintSize(context: Context, wm: WallpaperManager, width: Int, height: Int, screenW: Int, screenH: Int) {
        val wide = width > screenW
        val p = prefs(context)
        if (!wide && !p.getBoolean(KEY_HINTED, false)) return
        runCatching {
            if (wide) wm.suggestDesiredDimensions(width, height) else wm.suggestDesiredDimensions(screenW, screenH)
            p.edit().putBoolean(KEY_HINTED, wide).apply()
        }
    }
}

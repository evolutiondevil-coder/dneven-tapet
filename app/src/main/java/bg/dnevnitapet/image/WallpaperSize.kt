package bg.dnevnitapet.image

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Size of a wallpaper file. Normally exactly the screen; with the panorama the file is wider,
 * so the picture can slide while the home screens are swiped.
 */
object WallpaperSize {
    /**
     * Width of the file cut from a picture of [srcW] x [srcH]: as wide as the picture allows at
     * the screen's height, between the screen width and [maxW]. Tall pictures stay screen-wide.
     */
    fun width(screenW: Int, screenH: Int, maxW: Int, srcW: Int, srcH: Int): Int {
        if (maxW <= screenW || srcW <= 0 || srcH <= 0 || screenH <= 0) return screenW
        val natural = (screenH.toDouble() * srcW / srcH).roundToInt()
        // Barely wider than the screen would only wobble: then just fill the screen.
        if (natural < screenW + screenW / 10) return screenW
        return natural.coerceIn(screenW, maxW)
    }

    /** Widest file for the panorama: [screens] screen widths. */
    fun maxWidth(screenW: Int, screens: Float): Int = max(screenW, (screenW * screens).roundToInt())

    /**
     * Where to draw a [bitmapW] x [bitmapH] picture on a [surfaceW] x [surfaceH] screen at
     * [offset] (0 = the left edge of the picture, 1 = the right edge): it fills the screen,
     * centred vertically. Returns the scale and the left/top position.
     */
    fun placement(bitmapW: Int, bitmapH: Int, surfaceW: Int, surfaceH: Int, offset: Float): Placement {
        val scale = max(surfaceW.toFloat() / bitmapW, surfaceH.toFloat() / bitmapH)
        val extraW = bitmapW * scale - surfaceW
        val extraH = bitmapH * scale - surfaceH
        return Placement(scale, -extraW * offset.coerceIn(0f, 1f), -extraH / 2f)
    }

    data class Placement(val scale: Float, val left: Float, val top: Float)
}

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
     *
     * With tilt, the picture is enlarged by [zoom] and [tiltX]/[tiltY] (-1..1) move it inside
     * that extra margin; the panorama's sliding uses only the rest of the width.
     */
    fun placement(
        bitmapW: Int,
        bitmapH: Int,
        surfaceW: Int,
        surfaceH: Int,
        offset: Float,
        zoom: Float = 1f,
        tiltX: Float = 0f,
        tiltY: Float = 0f,
    ): Placement {
        val base = max(surfaceW.toFloat() / bitmapW, surfaceH.toFloat() / bitmapH)
        val scale = base * zoom
        val extraW = bitmapW * scale - surfaceW
        val extraH = bitmapH * scale - surfaceH
        // Margin kept free for tilting, both sides together.
        val tiltMarginW = (surfaceW * (zoom - 1f)).coerceIn(0f, extraW.coerceAtLeast(0f))
        val slideW = extraW - tiltMarginW
        val left = -(slideW * offset.coerceIn(0f, 1f) + tiltMarginW * (0.5f + 0.5f * tiltX.coerceIn(-1f, 1f)))
        val top = -(extraH * (0.5f + 0.5f * tiltY.coerceIn(-1f, 1f)))
        return Placement(scale, left, top)
    }

    data class Placement(val scale: Float, val left: Float, val top: Float)
}

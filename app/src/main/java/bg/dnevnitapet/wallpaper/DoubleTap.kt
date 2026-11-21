package bg.dnevnitapet.wallpaper

import kotlin.math.hypot

/**
 * Recognises a double tap from single taps (time in ms, position in px). Pure logic, unit-tested.
 * Two taps close together in time and place make a double tap; a third tap starts anew.
 */
class DoubleTap(private val timeoutMs: Long = 450, private val slopPx: Float = 120f) {
    private var lastAt = -1L
    private var lastX = 0f
    private var lastY = 0f

    /** @return true when this tap completes a double tap. */
    fun tap(at: Long, x: Float, y: Float): Boolean {
        val double = lastAt >= 0 && at - lastAt in 0..timeoutMs && hypot(x - lastX, y - lastY) <= slopPx
        if (double) {
            lastAt = -1L
        } else {
            lastAt = at
            lastX = x
            lastY = y
        }
        return double
    }
}

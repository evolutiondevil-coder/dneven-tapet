package bg.dnevnitapet.image

import bg.dnevnitapet.model.ReadableMode

/** How much the live wallpaper darkens the picture (0 = not at all, 1 = black). */
object Effects {
    /** "По-четливи икони". */
    fun readableDim(mode: ReadableMode): Float = when (mode) {
        ReadableMode.OFF -> 0f
        ReadableMode.DIM -> 0.25f
        ReadableMode.DIM_BLUR -> 0.18f
    }

    /**
     * "Вечер по-тъмно": none during the day, growing from 19:30 to 21:00, full during the night,
     * fading away from 06:00 to 07:00.
     */
    fun eveningDim(minuteOfDay: Int): Float {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        return when {
            m < 6 * 60 -> NIGHT
            m < 7 * 60 -> NIGHT * (7 * 60 - m) / 60f
            m < 19 * 60 + 30 -> 0f
            m < 21 * 60 -> NIGHT * (m - (19 * 60 + 30)) / 90f
            else -> NIGHT
        }
    }

    /** Two darkenings on top of each other. */
    fun combine(a: Float, b: Float): Float = 1f - (1f - a) * (1f - b)

    const val NIGHT = 0.35f

    /** Enlargement that leaves room for the tilt effect. */
    const val TILT_ZOOM = 1.08f
}

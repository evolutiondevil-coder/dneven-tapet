package bg.dnevnitapet.image

import bg.dnevnitapet.model.ReadableMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {
    @Test
    fun eveningDimFollowsTheClock() {
        assertEquals(0f, Effects.eveningDim(12 * 60), 1e-6f)
        assertEquals(0f, Effects.eveningDim(19 * 60 + 30), 1e-6f)
        assertEquals(Effects.NIGHT / 2, Effects.eveningDim(20 * 60 + 15), 1e-4f)
        assertEquals(Effects.NIGHT, Effects.eveningDim(23 * 60), 1e-6f)
        assertEquals(Effects.NIGHT, Effects.eveningDim(3 * 60), 1e-6f)
        assertEquals(Effects.NIGHT / 2, Effects.eveningDim(6 * 60 + 30), 1e-4f)
        assertEquals(0f, Effects.eveningDim(7 * 60), 1e-6f)
    }

    @Test
    fun readableAndEveningAddUp() {
        assertEquals(0f, Effects.readableDim(ReadableMode.OFF), 0f)
        val both = Effects.combine(Effects.readableDim(ReadableMode.DIM), Effects.NIGHT)
        assertTrue(both > Effects.NIGHT && both < 1f)
    }

    @Test
    fun tiltMovesTheEnlargedPictureInsideItsMargin() {
        // Screen-sized picture, enlarged 8 %: 86.4 px of margin to move in.
        val left = WallpaperSize.placement(1080, 2340, 1080, 2340, 0.5f, 1.08f, tiltX = -1f)
        val right = WallpaperSize.placement(1080, 2340, 1080, 2340, 0.5f, 1.08f, tiltX = 1f)
        val middle = WallpaperSize.placement(1080, 2340, 1080, 2340, 0.5f, 1.08f)
        assertEquals(0f, left.left, 0.01f)
        assertEquals(-86.4f, right.left, 0.1f)
        assertEquals(-43.2f, middle.left, 0.1f)
        // The picture always covers the screen.
        assertTrue(right.left + 1080 * right.scale >= 1080f - 0.1f)
        // Panorama and tilt together: sliding uses the width beyond the tilt margin.
        val pano = WallpaperSize.placement(2160, 2340, 1080, 2340, 1f, 1.08f)
        assertEquals(-(2160 * 1.08f - 1080) + 43.2f, pano.left, 0.5f)
    }
}

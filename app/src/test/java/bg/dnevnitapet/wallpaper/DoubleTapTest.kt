package bg.dnevnitapet.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapTest {
    @Test
    fun `two quick taps in one place are a double tap`() {
        val d = DoubleTap(timeoutMs = 450, slopPx = 100f)
        assertFalse(d.tap(1000, 500f, 800f))
        assertTrue(d.tap(1300, 520f, 790f))
        // The next tap starts a new double tap.
        assertFalse(d.tap(1500, 520f, 790f))
    }

    @Test
    fun `slow or far apart taps are not`() {
        val d = DoubleTap(timeoutMs = 450, slopPx = 100f)
        assertFalse(d.tap(1000, 500f, 800f))
        assertFalse(d.tap(1600, 500f, 800f)) // too slow – but counts as a new first tap
        assertTrue(d.tap(1800, 510f, 810f))
        assertFalse(d.tap(3000, 100f, 100f))
        assertFalse(d.tap(3200, 600f, 900f)) // too far
    }
}

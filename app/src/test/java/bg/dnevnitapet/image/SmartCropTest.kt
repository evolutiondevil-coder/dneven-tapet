package bg.dnevnitapet.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartCropTest {
    private val aspect = 1080.0 / 2340.0

    /** Grey noisy background with a coloured disc at (cx, cy). */
    private fun picture(w: Int, h: Int, cx: Int, cy: Int, radius: Int): IntArray {
        val rnd = Random(42)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val inside = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius
            if (inside) {
                0xFFE0302A.toInt()
            } else {
                val g = 110 + rnd.nextInt(12)
                (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
        }
    }

    @Test
    fun `keeps a subject on the right side in the crop`() {
        val w = 400
        val h = 225
        val crop = SmartCrop.findCrop(picture(w, h, cx = 330, cy = 110, radius = 25), w, h, aspect)
        assertEquals(h, crop.height)
        assertTrue(kotlin.math.abs(h * aspect - crop.width) <= 1.0)
        assertTrue("subject inside crop: $crop", 330 - 25 >= crop.left && 330 + 25 <= crop.right)
    }

    @Test
    fun `keeps a subject on the left side in the crop`() {
        val w = 400
        val h = 225
        val crop = SmartCrop.findCrop(picture(w, h, cx = 50, cy = 100, radius = 20), w, h, aspect)
        assertTrue("subject inside crop: $crop", 30 >= crop.left && 70 <= crop.right)
    }

    @Test
    fun `plain picture is cut in the middle`() {
        val w = 400
        val h = 225
        val flat = IntArray(w * h) { 0xFF808080.toInt() }
        val crop = SmartCrop.findCrop(flat, w, h, aspect)
        val centre = crop.left + crop.width / 2
        assertTrue("centre $centre", centre in 180..220)
    }

    @Test
    fun `face boost wins over a busier area`() {
        val w = 400
        val h = 225
        val px = picture(w, h, cx = 60, cy = 110, radius = 30)
        val crop = SmartCrop.findCrop(px, w, h, aspect, boosts = listOf(SmartCrop.Boost(300, 80, 340, 140)))
        assertTrue("face inside crop: $crop", 300 >= crop.left && 340 <= crop.right)
    }

    @Test
    fun `tall picture is cut vertically`() {
        val w = 200
        val h = 800
        val crop = SmartCrop.findCrop(picture(w, h, cx = 100, cy = 650, radius = 30), w, h, aspect)
        assertEquals(w, crop.width)
        assertTrue("subject inside crop: $crop", 620 >= crop.top && 680 <= crop.bottom)
    }

    @Test
    fun `picture with the right shape is not cut`() {
        val crop = SmartCrop.findCrop(IntArray(108 * 234) { -1 }, 108, 234, aspect)
        assertEquals(SmartCrop.Rect(0, 0, 108, 234), crop)
    }
}

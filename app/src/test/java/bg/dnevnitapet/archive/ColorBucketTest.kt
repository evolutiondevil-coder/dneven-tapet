package bg.dnevnitapet.archive

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorBucketTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `the most common bright colour wins`() {
        val sea = IntArray(100) { if (it < 70) rgb(30, 110, 220) else rgb(240, 220, 180) }
        assertEquals("blue", ColorBucket.of(sea))
        val forest = IntArray(100) { if (it < 50) rgb(40, 160, 60) else rgb(20, 20, 20) }
        assertEquals("green", ColorBucket.of(forest))
        val sunset = IntArray(100) { rgb(250, 140, 30) }
        assertEquals("orange", ColorBucket.of(sunset))
    }

    @Test
    fun `grey and dark pictures are black and white`() {
        val grey = IntArray(100) { rgb(120, 120, 125) }
        assertEquals("bw", ColorBucket.of(grey))
        val night = IntArray(100) { if (it < 5) rgb(30, 110, 220) else rgb(5, 5, 10) }
        assertEquals("bw", ColorBucket.of(night))
    }

    @Test
    fun `hues fall in their buckets`() {
        assertEquals("red", ColorBucket.hueBucket(355f))
        assertEquals("yellow", ColorBucket.hueBucket(55f))
        assertEquals("teal", ColorBucket.hueBucket(185f))
        assertEquals("purple", ColorBucket.hueBucket(290f))
    }
}

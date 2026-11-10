package bg.dnevnitapet.sources

import bg.dnevnitapet.Fixtures
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.sources.apod.ApodParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApodTest {
    @Test
    fun `parses the real science-nasa-gov answer and skips video days`() {
        val list = ApodParser.parse(Fixtures.read("apod_wp_oct16-19.json"))
        // 16 Oct is an mp4 video, 18 Oct an embedded YouTube video.
        assertEquals(listOf("2026-10-19", "2026-10-17"), list.map { it.date })

        val treble = list.first()
        assertEquals("Treble Clef: Unusual X-ray Hot Radio Source", treble.title)
        assertEquals(Category.SPACE, treble.category)
        assertTrue(treble.description.isNotBlank())
        assertFalse(treble.description.contains("<"))
        assertTrue(treble.infoUrl.startsWith("https://science.nasa.gov/image-article/"))
        assertTrue(treble.author.isNotBlank())
        assertFalse(treble.author.contains("Authors"))
        // Full-resolution original plus a 3840 px resized copy for big pictures.
        val original = treble.variants.last()
        assertTrue(original.url.startsWith("https://assets.science.nasa.gov/content/dam/"))
        assertEquals(7200, original.width)
        assertEquals(5361, original.height)
        val resized = treble.variants.first()
        assertTrue(resized.url.contains("/dynamicimage/") && resized.url.contains("w=3840"))
        assertEquals(3840, resized.width)
    }

    @Test
    fun `picture of unknown size is still offered`() {
        val list = ApodParser.parse(Fixtures.read("apod_wp_oct12.json"))
        assertEquals(1, list.size)
        val jupiter = list.single()
        assertEquals("Jupiter and the Moons", jupiter.title)
        assertEquals("Chirag Upreti", jupiter.author)
        assertEquals(0, jupiter.variants.single().width)
    }

    @Test
    fun `non-APOD posts are ignored`() {
        val json = """[{"date":"2026-10-01T00:05:00","title":{"rendered":"Some news"},"content":{"rendered":"<p>x</p>"}}]"""
        assertTrue(ApodParser.parse(json).isEmpty())
    }
}

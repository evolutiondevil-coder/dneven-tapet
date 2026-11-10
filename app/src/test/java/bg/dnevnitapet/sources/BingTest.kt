package bg.dnevnitapet.sources

import bg.dnevnitapet.FakeHttp
import bg.dnevnitapet.Fixtures
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.sources.bing.BingParser
import bg.dnevnitapet.sources.bing.BingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BingTest {
    @Test
    fun `parses the real en-US archive answer`() {
        val list = BingParser.parse(Fixtures.read("bing_en-US.json"))
        assertEquals(8, list.size)
        val first = list.first()
        assertEquals("bing:RoseIsland", first.key)
        assertEquals("Licensed to chill", first.title)
        assertEquals("Aerial view of Rose Island, The Bahamas", first.description)
        assertEquals("Abstract Aerial Art/Getty Images", first.author)
        assertEquals("2026-11-10", first.date)
        val portrait = first.variants.first { it.portrait }
        assertEquals("https://www.bing.com/th?id=OHR.RoseIsland_EN-US0952497192_1080x1920.jpg", portrait.url)
        assertEquals(1080, portrait.width)
        assertEquals(1920, portrait.height)
        assertTrue(first.variants.any { it.url.endsWith("_UHD.jpg") && it.width == 3840 })
        assertEquals("https://www.bing.com/th?id=OHR.RoseIsland_EN-US0952497192_UHD.jpg", first.fullUrl)
        // Non-ASCII captions survive (Pīpīwai, Haleakalā).
        assertTrue(list.any { it.description.contains("Pīpīwai") })
    }

    @Test
    fun `different markets give the same key for the same picture`() {
        val de = BingParser.parse(Fixtures.read("bing_de-DE.json"))
        assertEquals("Die Lizenz zum Chillen", de.first { it.key == "bing:RoseIsland" }.title)
        // In the real German answer "MauerBerlin" has "wp": false (not allowed as wallpaper).
        assertTrue(de.none { it.key == "bing:MauerBerlin" })
        assertEquals(7, de.size)
    }

    @Test
    fun `pictures not allowed as wallpaper are skipped`() {
        val json = """{"images":[{"startdate":"20261110","urlbase":"/th?id=OHR.Test_EN-US1","copyright":"x (© y)","title":"t","wp":false}]}"""
        assertTrue(BingParser.parse(json).isEmpty())
    }

    @Test
    fun `splits caption and credit`() {
        assertEquals("A" to "© B/C", BingParser.splitCopyright("A (© B/C)"))
        assertEquals("No credit" to "", BingParser.splitCopyright("No credit"))
    }

    @Test
    fun `fetch merges markets, de-duplicates and advances the cursor`() = runTest {
        val http = FakeHttp(
            listOf(
                "mkt=en-US" to Fixtures.read("bing_en-US.json"),
                "mkt=en-GB" to Fixtures.read("bing_de-DE.json"),
            ),
        )
        val result = BingSource().fetch(FetchRequest(http, Settings(), null, wantFresh = true, today = LocalDate.of(2026, 11, 10)))
        assertEquals(8, result.candidates.size)
        assertEquals(result.candidates.size, result.candidates.map { it.key }.toSet().size)
        // The English caption is kept when the same picture also comes from another market.
        assertEquals("Licensed to chill", result.candidates.first { it.key == "bing:RoseIsland" }.title)
        assertEquals("2", result.cursor)
        assertEquals(2, http.requested.size) // en-US idx 0 asked once only
    }
}

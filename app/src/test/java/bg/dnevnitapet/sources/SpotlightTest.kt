package bg.dnevnitapet.sources

import bg.dnevnitapet.FakeHttp
import bg.dnevnitapet.Fixtures
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.sources.spotlight.SpotlightParser
import bg.dnevnitapet.sources.spotlight.SpotlightSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpotlightTest {
    @Test
    fun `parses the real v4 answer`() {
        val list = SpotlightParser.parseV4(Fixtures.read("spotlight_v4.json"))
        assertEquals(4, list.size)
        val angkor = list.first()
        assertEquals("spotlight:128000000005885949", angkor.key)
        assertEquals("Stories etched in stone", angkor.title)
        assertEquals("Angkor Wat, Cambodia", angkor.location)
        assertEquals("Artie Photography (Artie Ng) / Moment / Getty Images", angkor.author)
        assertTrue(angkor.description.startsWith("Angkor Wat has been an important Buddhist center"))
        val portrait = angkor.variants.first { it.portrait }
        assertEquals(1080, portrait.width)
        assertEquals(1920, portrait.height)
        assertTrue(portrait.url.endsWith("_1080x1920.jpg"))
        val landscape = angkor.variants.first { !it.portrait }
        assertEquals(3840, landscape.width)
        assertEquals(2160, landscape.height)
        assertTrue(angkor.infoUrl.startsWith("https://www.bing.com/spotlight"))
        // Tracking endpoints are never offered as picture URLs.
        assertFalse(list.flatMap { it.variants }.any { it.url.contains("ris.api") || it.url.contains("Impression") })
    }

    @Test
    fun `parses the real v3 fallback answer`() {
        val list = SpotlightParser.parseV3(Fixtures.read("spotlight_v3.json"))
        assertEquals(3, list.size)
        val first = list.first()
        assertEquals("Saint-Jean-Cap-Ferrat, France", first.title)
        assertEquals("spotlight:WL_StJeanCapFerratFrance", first.key)
        assertEquals("Paolo Evangelista / Sime / eStock Photo", first.author)
        val portrait = first.variants.first { it.portrait }
        assertEquals(1080, portrait.width)
        assertEquals(1920, portrait.height)
    }

    @Test
    fun `falls back to v3 when v4 fails`() = runTest {
        val http = FakeHttp(listOf("arc.msn.com" to Fixtures.read("spotlight_v3.json")))
        val result = SpotlightSource().fetch(FetchRequest(http, Settings(), null, false, LocalDate.of(2026, 11, 10)))
        assertEquals(3, result.candidates.size)
    }
}

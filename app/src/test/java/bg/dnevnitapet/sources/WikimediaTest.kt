package bg.dnevnitapet.sources

import bg.dnevnitapet.FakeHttp
import bg.dnevnitapet.Fixtures
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.sources.wikimedia.WikimediaParser
import bg.dnevnitapet.sources.wikimedia.WikimediaSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.LocalDate

class WikimediaTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val days = (0L until 8L).map { today.minusDays(it) }

    @Test
    fun `parses expanded POTD templates of the real answer`() {
        val expanded = WikimediaParser.parseExpanded(Fixtures.read("wikimedia_expand.json"), days)
        assertEquals(8, expanded.size)
        val verdun = expanded.getValue(today)
        assertEquals("Verdun – Cathédrale Notre-Dame – Crypte 2025 08 16.jpg", verdun.file)
        assertTrue(verdun.caption.startsWith("12th-century crypt of Verdun Cathedral, France"))
        assertFalse(verdun.caption.contains("[["))
    }

    @Test
    fun `builds candidates with sizes, author and licence`() {
        val expanded = WikimediaParser.parseExpanded(Fixtures.read("wikimedia_expand.json"), days)
        val info = WikimediaParser.parseImageInfo(Fixtures.read("wikimedia_imageinfo.json"))
        val list = WikimediaParser.build(expanded, info)
        assertEquals(8, list.size)
        val verdun = list.first { it.date == "2026-11-10" }
        assertEquals("Verdun – Cathédrale Notre-Dame – Crypte 2025 08 16", verdun.title)
        assertEquals("Andreas F. Borchert", verdun.author)
        assertTrue(verdun.copyright.contains("CC BY-SA 4.0"))
        assertTrue(verdun.infoUrl.startsWith("https://commons.wikimedia.org/wiki/File:"))
        // Only standard thumbnail widths plus the original, no tracking parameters.
        val urls = verdun.variants.map { it.url }
        assertTrue(urls.any { it.contains("/1920px-") })
        assertTrue(urls.any { it.contains("/3840px-") })
        assertTrue(urls.none { it.contains("utm_") })
        val original = verdun.variants.last()
        assertEquals(6231, original.width)
        assertEquals(4154, original.height)
        assertTrue(verdun.fullUrl.contains("/3840px-"))

        // 3741 px wide: no 3840 thumbnail (that would be enlarged), original instead.
        val beeEaters = list.first { it.title.contains("bee-eaters") }
        assertTrue(beeEaters.variants.none { it.url.contains("/3840px-") })
        assertEquals(3741, beeEaters.variants.last().width)
    }

    @Test
    fun `fetch walks back in time and then picks up new days`() = runTest {
        val http = FakeHttp(
            listOf(
                "action=expandtemplates" to Fixtures.read("wikimedia_expand.json"),
                "prop=imageinfo" to Fixtures.read("wikimedia_imageinfo.json"),
            ),
        )
        val source = WikimediaSource()
        val first = source.fetch(FetchRequest(http, Settings(), null, true, today))
        assertEquals(8, first.candidates.size)
        assertEquals("2026-11-10|2026-11-03", first.cursor)
        val text = URLDecoder.decode(http.requested.first().substringAfter("text="), "UTF-8")
        assertTrue(text.contains("{{Potd/2026-11-10}}"))
        assertTrue(text.contains("{{Potd/2026-11-03 (en)}}"))

        http.requested.clear()
        val older = source.fetch(FetchRequest(http, Settings(), first.cursor, false, today))
        assertEquals("2026-11-10|2026-10-26", older.cursor)
        assertTrue(URLDecoder.decode(http.requested.first(), "UTF-8").contains("{{Potd/2026-11-02}}"))

        http.requested.clear()
        val fresh = source.fetch(FetchRequest(http, Settings(), older.cursor, true, today.plusDays(2)))
        assertEquals("2026-11-12|2026-10-26", fresh.cursor)
        val freshText = URLDecoder.decode(http.requested.first(), "UTF-8")
        assertTrue(freshText.contains("{{Potd/2026-11-12}}") && freshText.contains("{{Potd/2026-11-11}}"))
        assertFalse(freshText.contains("{{Potd/2026-11-10}}"))
    }

    @Test
    fun `missing days are ignored`() {
        val json = """{"expandtemplates":{"wikitext":"[[:Template:Potd/2030-01-01]]\n#C#\n{{Potd/2030-01-01 (en)}}"}}"""
        assertTrue(WikimediaParser.parseExpanded(json, listOf(LocalDate.of(2030, 1, 1))).isEmpty())
        assertNotNull(WikimediaParser.parseImageInfo("""{"query":{"pages":[]}}"""))
    }
}

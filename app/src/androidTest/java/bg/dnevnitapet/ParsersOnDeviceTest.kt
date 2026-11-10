package bg.dnevnitapet

import androidx.test.ext.junit.runners.AndroidJUnit4
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.sources.CategoryClassifier
import bg.dnevnitapet.sources.Text
import bg.dnevnitapet.sources.apod.ApodParser
import bg.dnevnitapet.sources.bing.BingParser
import bg.dnevnitapet.sources.pexels.PexelsParser
import bg.dnevnitapet.sources.spotlight.SpotlightParser
import bg.dnevnitapet.sources.unsplash.UnsplashParser
import bg.dnevnitapet.sources.wikimedia.WikimediaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The same recorded answers as the JVM unit tests, parsed on the device itself. */
@RunWith(AndroidJUnit4::class)
class ParsersOnDeviceTest {
    private fun read(name: String) = javaClass.classLoader!!.getResource("fixtures/$name").readText()

    @Test
    fun allParsersWorkOnAndroid() {
        assertEquals(8, BingParser.parse(read("bing_en-US.json")).size)
        assertEquals(4, SpotlightParser.parseV4(read("spotlight_v4.json")).size)
        assertEquals(3, SpotlightParser.parseV3(read("spotlight_v3.json")).size)

        val days = (0L until 8L).map { LocalDate.of(2026, 11, 10).minusDays(it) }
        val expanded = WikimediaParser.parseExpanded(read("wikimedia_expand.json"), days)
        val wiki = WikimediaParser.build(expanded, WikimediaParser.parseImageInfo(read("wikimedia_imageinfo.json")))
        assertEquals(8, wiki.size)
        assertTrue(wiki.first().description.startsWith("12th-century crypt of Verdun Cathedral"))

        assertEquals(2, ApodParser.parse(read("apod_wp_oct16-19.json")).size)
        assertEquals(1, UnsplashParser.parse(read("unsplash_random_docs.json"), Category.NATURE).size)
        assertEquals(1, PexelsParser.parse(read("pexels_search_docs.json"), Category.NATURE).size)

        assertEquals("a b", Text.stripWiki("[[x|a]] {{t}}b"))
        assertEquals(Category.ANIMALS, CategoryClassifier.classify("A red fox in the snow"))
    }
}

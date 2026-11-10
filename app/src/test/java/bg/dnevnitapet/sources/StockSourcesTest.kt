package bg.dnevnitapet.sources

import bg.dnevnitapet.FakeHttp
import bg.dnevnitapet.Fixtures
import bg.dnevnitapet.image.VariantChooser
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.sources.pexels.PexelsParser
import bg.dnevnitapet.sources.unsplash.UnsplashParser
import bg.dnevnitapet.sources.unsplash.UnsplashSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class StockSourcesTest {
    @Test
    fun `parses the documented Unsplash answer`() {
        val photo = UnsplashParser.parse(Fixtures.read("unsplash_random_docs.json"), Category.CITIES).single()
        assertEquals("unsplash:Dwu85P9SOIk", photo.key)
        assertEquals("A man drinking a coffee.", photo.title)
        assertEquals("Montreal, Canada", photo.location)
        assertEquals("Joe Example", photo.author)
        assertEquals("https://api.unsplash.com/photos/Dwu85P9SOIk/download", photo.downloadPingUrl)
        assertEquals(Category.CITIES, photo.category)
        val choice = VariantChooser.choose(photo.variants, 1080, 2340)!!
        assertEquals(
            "https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?w=1080&h=2340&fit=crop&crop=faces,entropy&q=90&fm=jpg",
            choice.url,
        )
    }

    @Test
    fun `Unsplash sends the key only in the header`() = runTest {
        val http = FakeHttp(listOf("api.unsplash.com" to Fixtures.read("unsplash_random_docs.json")))
        val settings = Settings(unsplashKey = "abc", enabledCategories = setOf(Category.SEA))
        val result = UnsplashSource().fetch(FetchRequest(http, settings, null, false, LocalDate.now(), Random(1)))
        assertEquals(1, result.candidates.size)
        val url = http.requested.single()
        assertTrue(url.contains("query=ocean+coast") || url.contains("query=ocean%20coast"))
        assertFalse(url.contains("abc"))
        assertFalse(UnsplashSource().isConfigured(Settings()))
    }

    @Test
    fun `parses the documented Pexels answer`() {
        val photo = PexelsParser.parse(Fixtures.read("pexels_search_docs.json"), Category.NATURE).single()
        assertEquals("pexels:3573351", photo.key)
        assertEquals("Brown Rocks During Golden Hour", photo.title)
        assertEquals("Lukas Rodriguez", photo.author)
        assertEquals("https://www.pexels.com/photo/trees-during-day-3573351/", photo.infoUrl)
        val choice = VariantChooser.choose(photo.variants, 1080, 2340)!!
        assertEquals("https://images.pexels.com/photos/3573351/pexels-photo-3573351.png?cs=srgb&fit=crop&w=1080&h=2340", choice.url)
    }
}

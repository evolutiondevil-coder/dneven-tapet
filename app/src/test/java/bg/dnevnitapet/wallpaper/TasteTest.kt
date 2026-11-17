package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TasteTest {
    private fun c(key: String, source: String, date: String = "2026-11-01", category: Category? = null) =
        Candidate(key = key, sourceId = source, title = key, date = date, variants = emptyList(), category = category)

    private val order = listOf("bing", "spotlight", "wikimedia", "apod")
    private val classify: (Candidate) -> Category? = { it.category }

    @Test
    fun withoutTasteSourcesTakeTurnsNewestFirst() {
        val pool = listOf(c("b1", "bing", "2026-11-01"), c("b2", "bing", "2026-11-02"), c("s1", "spotlight"), c("a1", "apod"))
        assertEquals("s1", Taste.pick(pool, order, "bing", Taste.NEUTRAL, classify)!!.key)
        assertEquals("a1", Taste.pick(pool, order, "spotlight", Taste.NEUTRAL, classify)!!.key)
        assertEquals("b2", Taste.pick(pool, order, "apod", Taste.NEUTRAL, classify)!!.key)
    }

    @Test
    fun likedSourceComesMoreOftenButOthersStill() {
        val liked = List(4) { c("fav$it", "apod", category = Category.SPACE) }
        val profile = Taste.profile(liked, emptyList(), emptyList(), classify)
        val pool = listOf(c("b", "bing"), c("s", "spotlight"), c("w", "wikimedia"), c("a", "apod"))
        val random = Random(7)
        val counts = HashMap<String, Int>()
        var last = ""
        repeat(2000) {
            val pick = Taste.pick(pool, order, last, profile, classify, random)!!
            counts[pick.sourceId] = (counts[pick.sourceId] ?: 0) + 1
            last = pick.sourceId
        }
        assertTrue("apod should come most often: $counts", counts.getValue("apod") > counts.getValue("bing") * 3 / 2)
        assertTrue("others still come: $counts", order.all { (counts[it] ?: 0) > 100 })
    }

    @Test
    fun hiddenSourceComesLessOften() {
        val profile = Taste.profile(emptyList(), emptyList(), listOf("bing", "bing", "bing"), classify)
        assertTrue(profile.sourceWeight("bing") < 0.5)
        assertEquals(1.0, profile.sourceWeight("apod"), 1e-9)
    }

    @Test
    fun likedKindComesFirstWithinASource() {
        val profile = Taste.profile(listOf(c("f", "bing", category = Category.MOUNTAINS)), emptyList(), emptyList(), classify)
        val pool = listOf(
            c("city-new", "bing", "2026-11-05", Category.CITIES),
            c("mountain-old", "bing", "2026-11-01", Category.MOUNTAINS),
        )
        assertEquals("mountain-old", Taste.pick(pool, order, "", profile, classify, Random(1))!!.key)
    }

    @Test
    fun ownPhotosDoNotCountAsASource() {
        val profile = Taste.profile(listOf(c("mine:1", MyPhotos.SOURCE_ID)), emptyList(), listOf(MyPhotos.SOURCE_ID), classify)
        assertTrue(profile.isNeutral)
    }

    @Test
    fun describesWhatWasLearned() {
        val profile = Taste.profile(listOf(c("f", "apod", category = Category.SPACE)), emptyList(), listOf("bing", "bing", "bing"), classify)
        val text = Taste.describe(profile, { it.uppercase() }, { it.name.lowercase() })
        assertTrue(text, text.contains("space") && text.contains("APOD") && text.contains("BING"))
        assertEquals("", Taste.describe(Taste.NEUTRAL, { it }, { it.name }))
    }
}

package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.model.FrequencyMode
import bg.dnevnitapet.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PictureRulesTest {
    private fun c(title: String, category: Category? = null) = Candidate(key = title, sourceId = "bing", title = title, variants = emptyList(), category = category)
    private val classify: (Candidate) -> Category? = { it.category }

    @Test
    fun `parts of the day, seasons and weather codes`() {
        assertEquals(PictureRules.DayPart.MORNING, PictureRules.dayPart(7))
        assertEquals(PictureRules.DayPart.NIGHT, PictureRules.dayPart(2))
        assertEquals(PictureRules.DayPart.EVENING, PictureRules.dayPart(20))
        assertEquals(PictureRules.Season.WINTER, PictureRules.season(1))
        assertEquals(PictureRules.Season.AUTUMN, PictureRules.season(11))
        assertEquals(PictureRules.Sky.RAIN, PictureRules.sky(61))
        assertEquals(PictureRules.Sky.SNOW, PictureRules.sky(73))
        assertEquals(PictureRules.Sky.CLEAR, PictureRules.sky(0))
    }

    @Test
    fun `no rule on means no preference`() {
        assertNull(PictureRules.context(Settings(), LocalDateTime.of(2026, 11, 21, 20, 0), PictureRules.Sky.RAIN))
    }

    @Test
    fun `the picture that suits the evening, the season and the weather wins`() {
        val s = Settings(timeRules = true, eveningCategory = Category.SEA, seasonRules = true)
        val ctx = PictureRules.context(s, LocalDateTime.of(2026, 1, 10, 19, 30), null)!!
        assertEquals(Category.SEA, ctx.category)
        assertEquals(PictureRules.Season.WINTER, ctx.season)
        val queue = listOf(c("A city street", Category.CITIES), c("Calm sea", Category.SEA), c("Snowy sea cliffs", Category.SEA))
        assertEquals("Snowy sea cliffs", PictureRules.best(queue, { it }, classify, ctx)!!.title)
        // Nothing suits: the usual order stays.
        assertNull(PictureRules.best(listOf(c("A city street", Category.CITIES)), { it }, classify, ctx))
    }

    @Test
    fun `refilling looks at the next change`() {
        val daily = Settings(frequency = FrequencyMode.DAILY, dailyMinuteOfDay = 5 * 60)
        assertEquals(LocalDateTime.of(2026, 11, 22, 5, 0), PictureRules.nextChange(daily, LocalDateTime.of(2026, 11, 21, 20, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 21, 5, 0), PictureRules.nextChange(daily, LocalDateTime.of(2026, 11, 21, 3, 0)))
        val interval = Settings(frequency = FrequencyMode.INTERVAL, intervalHours = 6)
        assertEquals(LocalDateTime.of(2026, 11, 22, 2, 0), PictureRules.nextChange(interval, LocalDateTime.of(2026, 11, 21, 20, 0)))
    }
}

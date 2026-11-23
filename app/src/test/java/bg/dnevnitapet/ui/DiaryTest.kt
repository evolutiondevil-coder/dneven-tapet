package bg.dnevnitapet.ui

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DiaryTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test
    fun `days in a row`() {
        assertEquals(3, Diary.streak(listOf("2026-11-21", "2026-11-22", "2026-11-23"), today))
        // Not opened yet today: yesterday's run still counts.
        assertEquals(2, Diary.streak(listOf("2026-11-21", "2026-11-22"), today))
        assertEquals(1, Diary.streak(listOf("2026-11-19", "2026-11-23"), today))
        assertEquals(0, Diary.streak(listOf("2026-11-10"), today))
    }

    @Test
    fun `favourite categories and source`() {
        fun c(source: String, cat: Category?) = Candidate(key = source + cat, sourceId = source, title = "", variants = emptyList(), category = cat)
        val liked = listOf(c("bing", Category.MOUNTAINS), c("bing", Category.MOUNTAINS), c("apod", Category.SPACE), c("bing", Category.SEA), c("mine", null))
        assertEquals(listOf(Category.MOUNTAINS, Category.SEA), Diary.topCategories(liked, { it.category }))
        assertEquals("bing", Diary.topSource(liked, emptyList()))
        assertEquals("apod", Diary.topSource(emptyList(), listOf(c("apod", Category.SPACE))))
        assertEquals(null, Diary.topSource(emptyList(), emptyList()))
    }
}

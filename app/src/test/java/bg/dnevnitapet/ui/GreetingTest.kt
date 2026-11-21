package bg.dnevnitapet.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class GreetingTest {
    @Test
    fun `phrases fit the time and the day`() {
        // 21 November 2026 is a Saturday.
        val evening = Greeting.openPhrases(LocalDateTime.of(2026, 11, 21, 19, 30))
        assertTrue("Добър вечер" in evening)
        assertTrue("Приятна събота" in evening)
        assertTrue("Здравей отново" in evening)
        assertFalse("Добро утро" in evening)

        val morning = Greeting.openPhrases(LocalDateTime.of(2026, 11, 23, 7, 0))
        assertTrue("Добро утро" in morning)
        assertTrue("Приятен понеделник" in morning)

        // At night no wish for the day.
        val night = Greeting.openPhrases(LocalDateTime.of(2026, 11, 21, 23, 40))
        assertTrue("Тиха нощ" in night)
        assertFalse(night.any { it.startsWith("Приятн") })
    }

    @Test
    fun `the same phrase never comes twice in a row`() {
        val time = LocalDateTime.of(2026, 11, 21, 12, 0)
        val random = Random(7)
        var last: String? = null
        repeat(200) {
            val phrase = Greeting.openPhrase(time, last, random)
            assertNotEquals(last, phrase)
            last = phrase
        }
    }
}

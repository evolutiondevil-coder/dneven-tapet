package bg.dnevnitapet.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnThisDayTest {
    private val bgPage = """
        {{Календар ноември}}
        '''24 ноември''' е 328-ият ден в годината според [[Григориански календар|григорианския календар]].

        == Събития ==
        * [[1642]] г. – [[Абел Тасман]] открива остров [[Тасмания]].<ref>{{cite web|url=http://example.com|title=x}}</ref>
        * [[1859]] г. – [[Чарлз Дарвин]] публикува „[[Произход на видовете]]“.
        * [[1863]] г. – Започва [[Битка при Чатануга|битката при Чатануга]].
        * [[1877]] г.:
        ** В [[София]] е открито първото [[читалище]] в града – ''Славянска беседа''.
        * [[1963]] г. – Убит е {{нп|Лий Харви Осуалд|Лий Осуалд|en|Lee Harvey Oswald}} в Далас, Тексас.
        * [[42 г. пр.н.е.]] – Кратко.

        == Родени ==
        * [[1632]] г. – [[Барух Спиноза]], нидерландски философ
    """.trimIndent()

    private val enPage = """
        {{pp-semi}}
        == Events ==
        === Pre-1600 ===
        * [[1642]] – [[Abel Tasman]] becomes the first European to discover the island [[Van Diemen's Land]] (later renamed Tasmania).
        === 1801–1900 ===
        * [[1859]] – [[Charles Darwin]] publishes ''[[On the Origin of Species]]''.<ref name="a" />
        * [[1863]] &ndash; [[American Civil War]]: [[Battle of Lookout Mountain]] begins.
        * [[1871]] – The [[National Rifle Association of America]] is incorporated in [[New York City|New York]].
        == Births ==
        * [[1632]] – [[Baruch Spinoza]], Dutch philosopher
    """.trimIndent()

    @Test
    fun readsTheBulgarianEvents() {
        val events = DayPage.events(bgPage, "bg")
        assertEquals(listOf("1642", "1859", "1863", "1877", "1963"), events.map { it.year })
        val darwin = events.first { it.year == "1859" }
        assertEquals("Чарлз Дарвин публикува „Произход на видовете“.", darwin.text)
        assertEquals("https://bg.wikipedia.org/wiki/%D0%A7%D0%B0%D1%80%D0%BB%D0%B7_%D0%94%D0%B0%D1%80%D0%B2%D0%B8%D0%BD", darwin.link)
        // References go, the label of a link stays, a year with sub-items keeps its year.
        assertEquals("Абел Тасман открива остров Тасмания.", events[0].text)
        assertEquals("Започва битката при Чатануга.", events[2].text)
        assertEquals("В София е открито първото читалище в града – Славянска беседа.", events[3].text)
        assertEquals("Убит е Лий Осуалд в Далас, Тексас.", events[4].text)
        // Nothing from "Родени".
        assertTrue(events.none { "Спиноза" in it.text })
    }

    @Test
    fun readsTheEnglishEvents() {
        val events = DayPage.events(enPage, "en")
        assertEquals(listOf("1642", "1859", "1863", "1871"), events.map { it.year })
        assertEquals("Charles Darwin publishes On the Origin of Species.", events[1].text)
        assertEquals("https://en.wikipedia.org/wiki/Charles_Darwin", events[1].link)
        assertEquals("https://en.wikipedia.org/wiki/Abel_Tasman", events[0].link)
    }

    @Test
    fun skipsSadEvents() {
        val bg = DayPage.events(bgPage, "bg")
        assertFalse(DayPage.pleasant(bg.first { it.year == "1863" }))
        assertFalse(DayPage.pleasant(bg.first { it.year == "1963" }))
        assertTrue(DayPage.pleasant(bg.first { it.year == "1859" }))
        val en = DayPage.events(enPage, "en")
        assertFalse(DayPage.pleasant(en.first { it.year == "1863" }))
        assertTrue(DayPage.pleasant(en.first { it.year == "1859" }))
        // Whole words only: Warsaw and explorers are fine.
        assertTrue(DayPage.pleasant(DayPage.Event("1", "The explorer reaches Warsaw and Bombay.", "")))
        assertFalse(DayPage.pleasant(DayPage.Event("1", "The war ends.", "")))
        assertTrue(DayPage.pleasant(DayPage.Event("1", "Открит е първият кубински музей.", "")))
        assertFalse(DayPage.pleasant(DayPage.Event("1", "Започва Войната за независимост.", "")))
    }

    @Test
    fun picksOnePleasantEventTheSameAllDay() {
        val events = DayPage.events(bgPage, "bg")
        val day = LocalDate.of(2026, 11, 24)
        val picked = DayPage.pick(events, day)
        assertNotNull(picked)
        assertTrue(DayPage.pleasant(picked!!))
        assertEquals(picked, DayPage.pick(events, day))
        assertNull(DayPage.pick(events.filterNot(DayPage::pleasant), day))
    }

    @Test
    fun pageTitlesAndYears() {
        val day = LocalDate.of(2026, 11, 24)
        assertEquals("24 ноември", DayPage.title("bg", day))
        assertEquals("November 24", DayPage.title("en", day))
        assertEquals("https://en.wikipedia.org/w/index.php?title=November_24&action=raw", DayPage.rawUrl("en", day))
        assertEquals("1859 г.", DayPage.yearLabel("1859", "bg"))
        assertEquals("42 г. пр.н.е.", DayPage.yearLabel("42 пр.н.е.", "bg"))
        assertEquals("1859", DayPage.yearLabel("1859", "en"))
        assertEquals("42 пр.н.е.", DayPage.year("[[42 г. пр.н.е.]]"))
        assertEquals("1859", DayPage.year("[[1859]] г."))
        assertNull(DayPage.year("Славянска беседа"))
    }
}

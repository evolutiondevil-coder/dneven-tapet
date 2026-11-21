package bg.dnevnitapet.story

import bg.dnevnitapet.model.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTextTest {
    private fun candidate(lang: String = "", source: String = "bing") = Candidate(
        key = "k", sourceId = source, title = "Northern lights over Lofoten", location = "Hamnøya, Norway",
        description = "Lofoten is a chain of islands.", variants = emptyList(), lang = lang,
    )

    @Test
    fun `only English internet pictures are translated`() {
        assertTrue(StoryText.translatable(candidate()))
        assertTrue(StoryText.translatable(candidate(lang = "en")))
        assertFalse(StoryText.translatable(candidate(lang = "de")))
        assertFalse(StoryText.translatable(candidate(source = "mine")))
    }

    @Test
    fun `cyrillic text needs no translation`() {
        assertTrue(StoryText.isCyrillic("Северно сияние над Лофотен"))
        assertFalse(StoryText.isCyrillic("Northern lights"))
        assertTrue(StoryText.isCyrillic("2026"))
    }

    @Test
    fun `translated parts go back in place, blank ones are kept`() {
        val original = StoryText.Story("Title", "", "Text")
        val parts = StoryText.parts(original)
        assertEquals(listOf("Title", "Text"), parts)
        assertEquals(StoryText.Story("Заглавие", "", "Текст"), StoryText.assemble(original, listOf("Заглавие", "Текст")))
        assertEquals("Title", StoryText.wikipediaQuery(original))
        assertEquals("Лофотен", StoryText.wikipediaQuery(StoryText.Story("T", "Лофотен", "")))
    }
}

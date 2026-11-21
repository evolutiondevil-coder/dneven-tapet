package bg.dnevnitapet.update

import bg.dnevnitapet.ui.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ReleaseNotesTest {
    private val notes = """
        ## Какво ново във v1.5.0

        - **Добре дошъл:** четири кратки страници при първото пускане.
        - **Поздрав всеки ден:** днешната снимка при отваряне.
        - Дребни поправки

        ## Във v1.4.1

        - **Поправка:** старо

        **Инсталиране:** докосни файла
    """.trimIndent()

    @Test
    fun takesOnlyTheNewestSection() {
        val items = ReleaseNotes.current(notes)
        assertEquals(3, items.size)
        assertEquals(ReleaseNotes.Item("Добре дошъл", "четири кратки страници при първото пускане."), items[0])
        assertEquals(ReleaseNotes.Item("Дребни поправки", ""), items[2])
    }

    @Test
    fun theRealReleaseNotesHaveWhatsNew() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "RELEASE_NOTES.md") }.first { it.exists() }
        assertTrue(ReleaseNotes.current(file.readText()).isNotEmpty())
    }

    @Test
    fun greetsByTimeOfDay() {
        assertEquals("Добро утро", Greeting.phrase(7))
        assertEquals("Добър ден", Greeting.phrase(13))
        assertEquals("Добър вечер", Greeting.phrase(21))
        assertEquals("Добър вечер", Greeting.phrase(2))
        assertEquals("Петък, 20 ноември", Greeting.date(LocalDate.of(2026, 11, 20)))
    }
}

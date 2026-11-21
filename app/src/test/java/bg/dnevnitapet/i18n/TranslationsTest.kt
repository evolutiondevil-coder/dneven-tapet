package bg.dnevnitapet.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranslationsTest {
    @After
    fun back() {
        I18n.language = AppLanguage.BG
    }

    /** Every text of the app (tr("…") in the code) has an English version with the same {0}, {1}… */
    @Test
    fun `every text has an English translation`() {
        val table = I18n.englishTable()
        assertTrue("English table not found", table.size > 400)
        val literal = Regex("""\btr\("((?:[^"\\]|\\.)*)"""")
        val missing = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            literal.findAll(f.readText()).forEach { m ->
                val key = m.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\").replace("\\$", "$")
                // Debug lines for the emulator's log stay as they are.
                if (key.startsWith("tap ")) return@forEach
                val en = table[key]
                if (en == null) missing += "${f.name}: $key"
                else assertEquals("placeholders of '$key'", Regex("\\{\\d}").findAll(key).map { it.value }.sorted().toList(), Regex("\\{\\d}").findAll(en).map { it.value }.sorted().toList())
            }
        }
        assertTrue("No English for:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun `texts follow the chosen language`() {
        I18n.language = AppLanguage.BG
        assertEquals("Настройки", tr("Настройки"))
        assertEquals("Добавени 3 снимки", tr("Добавени {0} снимки", 3))
        I18n.language = AppLanguage.EN
        assertEquals("Settings", tr("Настройки"))
        assertEquals("3 photos added", tr("Добавени {0} снимки", 3))
        // A text without a translation stays as it is.
        assertEquals("Непознат текст", tr("Непознат текст"))
    }
}

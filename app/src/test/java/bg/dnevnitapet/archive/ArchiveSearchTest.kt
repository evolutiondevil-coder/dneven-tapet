package bg.dnevnitapet.archive

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSearchTest {
    private fun entry(title: String, location: String = "", color: String = "", source: String = "bing", date: String = "2025-06-01", category: Category? = null) =
        ArchiveEntry(
            candidate = Candidate(key = title, sourceId = source, title = title, location = location, date = date, variants = emptyList(), category = category),
            color = color,
            addedAt = 0,
        )

    private val lofoten = entry("Northern lights over Lofoten", "Hamnøya, Lofoten, Norway (© Someone)", color = "green")
    private val beach = entry("A quiet beach", "Navagio, Greece", color = "blue", category = Category.SEA)
    private val nebula = entry("Witch Head Nebula", color = "blue", source = "apod", date = "2024-11-02")
    private val all = listOf(lofoten, beach, nebula)

    @Test
    fun `country is the last part of the place`() {
        assertEquals("Norway", ArchiveSearch.country(lofoten.candidate))
        assertEquals("Greece", ArchiveSearch.country(beach.candidate))
        assertEquals("", ArchiveSearch.country(nebula.candidate))
        assertEquals(listOf("Norway", "Greece"), ArchiveSearch.countries(all))
    }

    @Test
    fun `words match in English, in Bulgarian and without accents`() {
        val tr = mapOf("Northern lights over Lofoten" to "Северно сияние над Лофотен", "Norway" to "Норвегия")
        val q = { text: String -> all.filter { ArchiveSearch.matches(it, ArchiveSearch.Query(text), tr) } }
        assertEquals(listOf(lofoten), q("hamnoya"))
        assertEquals(listOf(lofoten), q("норвегия"))
        assertEquals(listOf(lofoten), q("сияние лофотен"))
        // Category names are Bulgarian: "море" finds the beach.
        assertEquals(listOf(beach), q("море"))
        assertEquals(all, q(""))
        assertTrue(q("nothing like this").isEmpty())
    }

    @Test
    fun `filters combine`() {
        val blue = ArchiveSearch.Query(color = "blue")
        assertEquals(listOf(beach, nebula), all.filter { ArchiveSearch.matches(it, blue, emptyMap()) })
        assertEquals(listOf(nebula), all.filter { ArchiveSearch.matches(it, blue.copy(source = "apod"), emptyMap()) })
        assertEquals(listOf(nebula), all.filter { ArchiveSearch.matches(it, ArchiveSearch.Query(year = 2024), emptyMap()) })
        assertFalse(ArchiveSearch.matches(beach, ArchiveSearch.Query(country = "Norway"), emptyMap()))
        assertEquals(listOf(2025, 2024), ArchiveSearch.years(all))
    }
}

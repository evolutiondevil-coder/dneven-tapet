package bg.dnevnitapet.archive

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.sources.CategoryClassifier
import bg.dnevnitapet.sources.label
import java.text.Normalizer

/** Search and filters of the archive. Pure logic, unit-tested. */
object ArchiveSearch {
    data class Query(
        val text: String = "",
        val category: Category? = null,
        val color: String? = null,
        val country: String? = null,
        val source: String? = null,
        val year: Int? = null,
    ) {
        val isEmpty get() = text.isBlank() && category == null && color == null && country == null && source == null && year == null
    }

    fun category(c: Candidate): Category? = c.category ?: CategoryClassifier.classify(c)

    /** The country of a place: the last part of "Hamnøya, Lofoten, Norway" (without brackets). */
    fun country(c: Candidate): String {
        val place = c.location.substringBefore('(').trim().trimEnd(',', '.')
        if (!place.contains(',')) return ""
        return place.substringAfterLast(',').trim()
    }

    fun year(e: ArchiveEntry): Int? =
        e.candidate.date.take(4).toIntOrNull()
            ?: e.shownAt.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).year }

    /** Lower case, no accents ("Hamnøya" finds "hamnoya"), so typing is forgiving. */
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('ø', 'o').replace('æ', 'a').replace('ß', 's')

    /**
     * Every word of the search must be found in the title, place, story, category or country —
     * in the original or in Bulgarian ([translations]: original → Bulgarian).
     */
    fun matches(e: ArchiveEntry, q: Query, translations: Map<String, String>): Boolean {
        val c = e.candidate
        if (q.category != null && category(c) != q.category) return false
        if (q.color != null && e.color != q.color) return false
        if (q.country != null && country(c) != q.country) return false
        if (q.source != null && c.sourceId != q.source) return false
        if (q.year != null && year(e) != q.year) return false
        val words = normalize(q.text).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return true
        val country = country(c)
        val haystack = normalize(
            listOf(
                c.title, c.location, c.description, country,
                translations[c.title.trim()].orEmpty(), translations[c.location.trim()].orEmpty(), translations[country].orEmpty(),
                category(c)?.label.orEmpty(),
            ).joinToString(" "),
        )
        return words.all { it in haystack }
    }

    /** Countries in the archive, the most frequent first. */
    fun countries(entries: List<ArchiveEntry>): List<String> =
        entries.map { country(it.candidate) }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun years(entries: List<ArchiveEntry>): List<Int> = entries.mapNotNull { year(it) }.distinct().sortedDescending()

    fun sources(entries: List<ArchiveEntry>): List<String> = entries.map { it.candidate.sourceId }.distinct()
}

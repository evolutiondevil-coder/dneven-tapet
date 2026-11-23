package bg.dnevnitapet.ui

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import java.time.LocalDate

/** "Моят дневник": numbers about the user's wallpapers. Pure logic, unit-tested. */
object Diary {
    /** Days in a row with the app, up to [today] (or yesterday, if not opened yet today). */
    fun streak(days: Collection<String>, today: LocalDate): Int {
        val set = days.toSet()
        var day = if (today.toString() in set) today else today.minusDays(1)
        var n = 0
        while (day.toString() in set) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    /** The categories of the liked pictures, the most frequent first (at most [max]). */
    fun topCategories(liked: List<Candidate>, classify: (Candidate) -> Category?, max: Int = 2): List<Category> =
        liked.mapNotNull(classify).groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Category, Int>> { it.value }.thenBy { it.key.ordinal })
            .take(max).map { it.key }

    /** The source the user likes or sees most (null when there is nothing yet). */
    fun topSource(liked: List<Candidate>, seen: List<Candidate>): String? =
        (liked.ifEmpty { seen }).filter { it.sourceId != "mine" }.groupingBy { it.sourceId }.eachCount()
            .maxByOrNull { it.value }?.key
}

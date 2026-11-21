package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import kotlin.random.Random
import bg.dnevnitapet.i18n.tr

/**
 * "Учи вкуса ти": favourites (♥) make their source and kind of picture (nature, space, …) come
 * more often, hidden pictures ("Не показвай повече") make theirs come less often. Nothing is ever
 * left out completely, so the variety stays.
 */
object Taste {
    /** Weights per source and per category; 1.0 = neutral. */
    data class Profile(val source: Map<String, Double>, val category: Map<Category, Double>) {
        val isNeutral: Boolean get() = source.isEmpty() && category.isEmpty()

        fun sourceWeight(id: String): Double = source[id] ?: 1.0
        fun categoryWeight(c: Category?): Double = c?.let { category[it] } ?: 1.0
    }

    val NEUTRAL = Profile(emptyMap(), emptyMap())

    private const val LIKE = 0.5
    private const val DISLIKE = 0.35
    private const val MIN = 0.3
    private const val MAX = 3.0

    /**
     * @param liked the favourites' pictures.
     * @param disliked the hidden pictures (only known ones; for the rest [hiddenSources] counts).
     * @param hiddenSources source ids of all hidden pictures (taken from their keys).
     */
    fun profile(
        liked: List<Candidate>,
        disliked: List<Candidate>,
        hiddenSources: List<String>,
        classify: (Candidate) -> Category?,
    ): Profile {
        val src = HashMap<String, Double>()
        val cat = HashMap<Category, Double>()
        liked.filter { it.sourceId != MyPhotos.SOURCE_ID }.forEach { c ->
            src[c.sourceId] = (src[c.sourceId] ?: 0.0) + LIKE
            classify(c)?.let { k -> cat[k] = (cat[k] ?: 0.0) + LIKE }
        }
        hiddenSources.filter { it != MyPhotos.SOURCE_ID }.forEach { id -> src[id] = (src[id] ?: 0.0) - DISLIKE }
        disliked.forEach { c -> classify(c)?.let { k -> cat[k] = (cat[k] ?: 0.0) - DISLIKE } }
        return Profile(
            src.mapValues { (1.0 + it.value).coerceIn(MIN, MAX) },
            cat.mapValues { (1.0 + it.value).coerceIn(MIN, MAX) },
        )
    }

    /**
     * Picks the next picture to prepare. Without a taste yet: sources take turns after [lastSource],
     * newest pictures first (as before). With a taste: a source is drawn by its weight, and within it
     * the best-liked kind of picture comes first, newest among equals.
     */
    fun pick(
        pool: List<Candidate>,
        sourceOrder: List<String>,
        lastSource: String,
        profile: Profile,
        classify: (Candidate) -> Category?,
        random: Random = Random.Default,
    ): Candidate? {
        if (pool.isEmpty()) return null
        if (profile.isNeutral) {
            val start = (sourceOrder.indexOf(lastSource) + 1).coerceAtLeast(0)
            for (k in sourceOrder.indices) {
                val id = sourceOrder[(start + k) % sourceOrder.size]
                val fromSource = pool.filter { it.sourceId == id }
                if (fromSource.isNotEmpty()) return fromSource.maxBy { it.date }
            }
            return pool.first()
        }
        val bySource = pool.groupBy { it.sourceId }
        // Never the same source twice in a row while another one is available.
        val choices = bySource.keys.filter { it != lastSource }.ifEmpty { bySource.keys.toList() }
        val total = choices.sumOf { profile.sourceWeight(it) }
        var r = random.nextDouble() * total
        var chosen = choices.last()
        for (id in choices) {
            r -= profile.sourceWeight(id)
            if (r <= 0) {
                chosen = id
                break
            }
        }
        return bySource.getValue(chosen).sortedWith(
            compareByDescending<Candidate> { profile.categoryWeight(classify(it)) }.thenByDescending { it.date },
        ).first()
    }

    /** Short description of what the app has learned, for the settings ("" while neutral). */
    fun describe(profile: Profile, sourceName: (String) -> String, categoryName: (Category) -> String): String {
        val likedSources = profile.source.filterValues { it > 1.01 }.entries.sortedByDescending { it.value }.map { sourceName(it.key) }
        val likedKinds = profile.category.filterValues { it > 1.01 }.entries.sortedByDescending { it.value }.map { categoryName(it.key) }
        val less = (profile.source.filterValues { it < 0.99 }.keys.map(sourceName) +
            profile.category.filterValues { it < 0.99 }.keys.map(categoryName))
        val parts = mutableListOf<String>()
        if (likedKinds.isNotEmpty() || likedSources.isNotEmpty()) {
            parts += tr("По-често: ") + (likedKinds + likedSources).take(4).joinToString(", ")
        }
        if (less.isNotEmpty()) parts += tr("по-рядко: ") + less.take(3).joinToString(", ")
        return parts.joinToString("; ")
    }
}

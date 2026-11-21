package bg.dnevnitapet.wallpaper

import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.model.FrequencyMode
import bg.dnevnitapet.model.Settings
import java.time.LocalDateTime
import bg.dnevnitapet.i18n.tr

/**
 * "Според часа, сезона и времето" (optional): which prepared picture suits the moment best.
 * Pure logic, unit-tested. Without a matching picture the usual one comes.
 */
object PictureRules {
    enum class DayPart(val label: String, val hours: String) {
        MORNING(tr("Сутрин"), "05:00 – 11:00"),
        DAY(tr("Ден"), "11:00 – 18:00"),
        EVENING(tr("Вечер"), "18:00 – 22:00"),
        NIGHT(tr("Нощ"), "22:00 – 05:00"),
    }

    enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

    enum class Sky(val label: String) {
        CLEAR(tr("ясно")), CLOUDY(tr("облачно")), FOG(tr("мъгла")), RAIN(tr("дъжд")), SNOW(tr("сняг")), STORM(tr("буря"))
    }

    data class Context(val category: Category?, val season: Season?, val sky: Sky?)

    fun dayPart(hour: Int): DayPart = when (hour) {
        in 5..10 -> DayPart.MORNING
        in 11..17 -> DayPart.DAY
        in 18..21 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }

    fun season(month: Int): Season = when (month) {
        12, 1, 2 -> Season.WINTER
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        else -> Season.AUTUMN
    }

    /** Open-Meteo weather codes (WMO). */
    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        45, 48 -> Sky.FOG
        in 51..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun categoryFor(s: Settings, part: DayPart): Category? = when (part) {
        DayPart.MORNING -> s.morningCategory
        DayPart.DAY -> s.dayCategory
        DayPart.EVENING -> s.eveningCategory
        DayPart.NIGHT -> s.nightCategory
    }

    /** What suits [time] (null when no rule is on). [sky] is today's weather when known. */
    fun context(s: Settings, time: LocalDateTime, sky: Sky?): Context? {
        if (!s.timeRules && !s.seasonRules && !(s.weatherRules && sky != null)) return null
        return Context(
            category = if (s.timeRules) categoryFor(s, dayPart(time.hour)) else null,
            season = if (s.seasonRules) season(time.monthValue) else null,
            sky = if (s.weatherRules) sky else null,
        )
    }

    /** When the next automatic change is about (the rules for refilling look at that moment). */
    fun nextChange(s: Settings, now: LocalDateTime): LocalDateTime = when (s.frequency) {
        FrequencyMode.DAILY -> {
            val today = now.toLocalDate().atStartOfDay().plusMinutes(s.dailyMinuteOfDay.toLong())
            if (today.isAfter(now)) today else today.plusDays(1)
        }
        FrequencyMode.INTERVAL -> now.plusHours(s.intervalHours.toLong())
    }

    private val seasonWords: Map<Season, Set<String>> = mapOf(
        Season.WINTER to setOf("winter", "snow", "snowy", "ice", "icy", "frozen", "frost", "christmas", "glacier", "ski", "aurora", "reindeer"),
        Season.SPRING to setOf("spring", "blossom", "blossoms", "flowers", "flower", "tulip", "tulips", "cherry", "bloom", "blooming", "meadow"),
        Season.SUMMER to setOf("summer", "beach", "beaches", "sea", "island", "islands", "coast", "lagoon", "sunny", "swimming", "surf"),
        Season.AUTUMN to setOf("autumn", "fall", "foliage", "leaves", "harvest", "vineyard", "pumpkin", "mushrooms", "fog", "misty"),
    )

    private val skyWords: Map<Sky, Set<String>> = mapOf(
        Sky.CLEAR to setOf("sunny", "sunshine", "beach", "sunrise", "sunset", "bright", "clear", "flowers", "coast", "summer"),
        Sky.CLOUDY to setOf("clouds", "cloudy", "mountains", "lake", "forest", "valley", "castle"),
        Sky.FOG to setOf("fog", "foggy", "mist", "misty", "forest", "mysterious", "haze"),
        Sky.RAIN to setOf("rain", "rainy", "cozy", "cosy", "cabin", "library", "cafe", "village", "lights", "forest", "waterfall"),
        Sky.SNOW to setOf("snow", "snowy", "winter", "ice", "frozen", "cabin", "christmas", "mountains"),
        Sky.STORM to setOf("storm", "lightning", "thunder", "waves", "dramatic", "clouds"),
    )

    private val token = Regex("\\p{L}+")

    /** How well a picture suits the moment: its category, words of the season and of the weather. */
    fun score(c: Candidate, category: Category?, ctx: Context): Int {
        var score = 0
        if (ctx.category != null && category == ctx.category) score += 3
        val words by lazy { token.findAll(listOf(c.title, c.description, c.location).joinToString(" ").lowercase()).map { it.value }.toSet() }
        if (ctx.season != null && words.any { it in seasonWords.getValue(ctx.season) }) score += 2
        if (ctx.sky != null && words.any { it in skyWords.getValue(ctx.sky) }) score += 2
        return score
    }

    /** The best suiting of [items], or null when none suits at all. */
    fun <T> best(items: List<T>, candidate: (T) -> Candidate, category: (Candidate) -> Category?, ctx: Context): T? {
        val scored = items.map { it to score(candidate(it), category(candidate(it)), ctx) }
        val top = scored.maxByOrNull { it.second } ?: return null
        return top.first.takeIf { top.second > 0 }
    }
}

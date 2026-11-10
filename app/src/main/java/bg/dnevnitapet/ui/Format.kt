package bg.dnevnitapet.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val bg = Locale("bg")
    private val dayTime = DateTimeFormatter.ofPattern("d MMMM, HH:mm", bg)
    private val time = DateTimeFormatter.ofPattern("HH:mm", bg)
    private val day = DateTimeFormatter.ofPattern("d MMMM yyyy", bg)

    /** "днес 05:00", "утре 05:00", "вчера 18:20" or "3 ноември, 05:00". */
    fun moment(ms: Long): String {
        if (ms <= 0) return "—"
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        return when (dt.toLocalDate()) {
            today -> "днес " + dt.format(time)
            today.plusDays(1) -> "утре " + dt.format(time)
            today.minusDays(1) -> "вчера " + dt.format(time)
            else -> dt.format(dayTime)
        }
    }

    fun date(iso: String): String = runCatching { LocalDate.parse(iso).format(day) }.getOrDefault(iso)

    fun minuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    fun megabytes(bytes: Long): String = "%.1f MB".format(bg, bytes / 1_048_576.0)
}

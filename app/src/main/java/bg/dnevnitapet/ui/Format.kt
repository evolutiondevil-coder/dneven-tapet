package bg.dnevnitapet.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import bg.dnevnitapet.i18n.I18n
import bg.dnevnitapet.i18n.tr

object Format {
    // In the app's language (Bulgarian or English).
    private val loc: Locale get() = I18n.locale
    private val dayTime get() = DateTimeFormatter.ofPattern("d MMMM, HH:mm", loc)
    private val time get() = DateTimeFormatter.ofPattern("HH:mm", loc)
    private val day get() = DateTimeFormatter.ofPattern("d MMMM yyyy", loc)

    /** "днес 05:00", "утре 05:00", "вчера 18:20" or "3 ноември, 05:00". */
    fun moment(ms: Long): String {
        if (ms <= 0) return "—"
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        return when (dt.toLocalDate()) {
            today -> tr("днес ") + dt.format(time)
            today.plusDays(1) -> tr("утре ") + dt.format(time)
            today.minusDays(1) -> tr("вчера ") + dt.format(time)
            else -> dt.format(dayTime)
        }
    }

    fun date(iso: String): String = runCatching { LocalDate.parse(iso).format(day) }.getOrDefault(iso)

    fun minuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    fun megabytes(bytes: Long): String = "%.1f MB".format(loc, bytes / 1_048_576.0)
}

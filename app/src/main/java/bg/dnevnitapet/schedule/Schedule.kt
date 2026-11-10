package bg.dnevnitapet.schedule

import bg.dnevnitapet.model.FrequencyMode
import bg.dnevnitapet.model.Settings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** When is the next automatic change due? Pure logic, unit-tested. */
object Schedule {
    private const val HOUR = 3_600_000L

    /** Allows an interval alarm that fires a little early to still count. */
    private const val TOLERANCE = 2 * 60_000L

    fun isActive(s: Settings) = s.autoChange && s.onboarded

    /** The most recent daily change time that is not in the future. */
    fun lastDailySlot(nowMs: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var slot = atMinute(now, minuteOfDay)
        if (slot.isAfter(now)) slot = atMinute(now.minusDays(1), minuteOfDay)
        return slot.toInstant().toEpochMilli()
    }

    /**
     * @param lastChangeMs the last time the wallpaper changed, automatically or by hand.
     * A missed change (phone off, app stopped by the system) stays due until it happens.
     */
    fun isDue(nowMs: Long, lastChangeMs: Long, s: Settings, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!isActive(s)) return false
        return when (s.frequency) {
            FrequencyMode.DAILY -> lastChangeMs < lastDailySlot(nowMs, s.dailyMinuteOfDay, zone)
            FrequencyMode.INTERVAL -> nowMs - lastChangeMs >= s.intervalHours.coerceAtLeast(1) * HOUR - TOLERANCE
        }
    }

    /** Epoch millis of the next automatic change, or null when automatic change is off. */
    fun nextTrigger(nowMs: Long, lastChangeMs: Long, s: Settings, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!isActive(s)) return null
        if (isDue(nowMs, lastChangeMs, s, zone)) return nowMs
        return when (s.frequency) {
            FrequencyMode.DAILY -> {
                val now = Instant.ofEpochMilli(nowMs).atZone(zone)
                var slot = atMinute(now, s.dailyMinuteOfDay)
                if (!slot.isAfter(now)) slot = atMinute(now.plusDays(1), s.dailyMinuteOfDay)
                slot.toInstant().toEpochMilli()
            }
            FrequencyMode.INTERVAL -> lastChangeMs + s.intervalHours.coerceAtLeast(1) * HOUR
        }
    }

    private fun atMinute(day: ZonedDateTime, minuteOfDay: Int): ZonedDateTime =
        day.toLocalDate().atTime(minuteOfDay / 60 % 24, minuteOfDay % 60).atZone(day.zone)
}

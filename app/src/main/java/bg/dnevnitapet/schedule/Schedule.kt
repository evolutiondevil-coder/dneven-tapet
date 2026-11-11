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

    /** The latest change time (hh:mm of [minuteOfDay]) that is not after [ms]. */
    fun slotOnOrBefore(ms: Long, minuteOfDay: Int, zone: ZoneId): ZonedDateTime {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        var slot = atMinute(t, minuteOfDay)
        if (slot.isAfter(t)) slot = atMinute(t.minusDays(1), minuteOfDay)
        return slot
    }

    /** Kept for callers that only need "today's (or yesterday's) slot". */
    fun lastDailySlot(nowMs: Long, minuteOfDay: Int, zone: ZoneId): Long =
        slotOnOrBefore(nowMs, minuteOfDay, zone).toInstant().toEpochMilli()

    /**
     * Daily mode: the change after [lastChangeMs] is due at the N-th change time after it
     * (N = every how many days). A manual change counts too, so a picture picked by hand stays
     * until then. A missed change (phone off, app stopped) stays due until it happens.
     */
    private fun dailyDueAt(lastChangeMs: Long, s: Settings, zone: ZoneId): Long {
        val base = slotOnOrBefore(lastChangeMs, s.dailyMinuteOfDay, zone)
        val due = atMinute(base.plusDays(s.everyDays.coerceIn(1, 365).toLong()), s.dailyMinuteOfDay)
        return due.toInstant().toEpochMilli()
    }

    /** @param lastChangeMs the last time the wallpaper changed, automatically or by hand. */
    fun isDue(nowMs: Long, lastChangeMs: Long, s: Settings, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!isActive(s)) return false
        return when (s.frequency) {
            FrequencyMode.DAILY -> nowMs >= dailyDueAt(lastChangeMs, s, zone)
            FrequencyMode.INTERVAL -> nowMs - lastChangeMs >= s.intervalHours.coerceAtLeast(1) * HOUR - TOLERANCE
        }
    }

    /** Epoch millis of the next automatic change, or null when automatic change is off. */
    fun nextTrigger(nowMs: Long, lastChangeMs: Long, s: Settings, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!isActive(s)) return null
        if (isDue(nowMs, lastChangeMs, s, zone)) return nowMs
        return when (s.frequency) {
            FrequencyMode.DAILY -> dailyDueAt(lastChangeMs, s, zone)
            FrequencyMode.INTERVAL -> lastChangeMs + s.intervalHours.coerceAtLeast(1) * HOUR
        }
    }

    private fun atMinute(day: ZonedDateTime, minuteOfDay: Int): ZonedDateTime =
        day.toLocalDate().atTime(minuteOfDay / 60 % 24, minuteOfDay % 60).atZone(day.zone)
}

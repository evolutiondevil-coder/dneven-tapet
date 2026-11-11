package bg.dnevnitapet.schedule

import bg.dnevnitapet.model.FrequencyMode
import bg.dnevnitapet.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Sofia")
    private val daily = Settings(onboarded = true, frequency = FrequencyMode.DAILY, dailyMinuteOfDay = 5 * 60)

    private fun t(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `daily change is due once the time has passed`() {
        val last = t("2026-11-09T05:00:10")
        assertFalse(Schedule.isDue(t("2026-11-10T04:59:00"), last, daily, zone))
        assertTrue(Schedule.isDue(t("2026-11-10T05:00:00"), last, daily, zone))
    }

    @Test
    fun `missed day is caught up later`() {
        // Phone was off at 05:00; it is now 09:30 and the last change was yesterday.
        assertTrue(Schedule.isDue(t("2026-11-10T09:30:00"), t("2026-11-09T05:01:00"), daily, zone))
        // Several days missed: still just one change due.
        assertTrue(Schedule.isDue(t("2026-11-10T09:30:00"), t("2026-11-01T05:01:00"), daily, zone))
    }

    @Test
    fun `a manual change after the daily time counts for that day`() {
        assertFalse(Schedule.isDue(t("2026-11-10T21:00:00"), t("2026-11-10T08:00:00"), daily, zone))
    }

    @Test
    fun `next daily trigger is tomorrow at the chosen time`() {
        val next = Schedule.nextTrigger(t("2026-11-10T07:00:00"), t("2026-11-10T05:00:01"), daily, zone)
        assertEquals(t("2026-11-11T05:00:00"), next)
        val before = Schedule.nextTrigger(t("2026-11-10T03:00:00"), t("2026-11-09T05:00:01"), daily, zone)
        assertEquals(t("2026-11-10T05:00:00"), before)
    }

    @Test
    fun `daylight saving change keeps local time`() {
        // Bulgaria leaves summer time on 2026-10-25 at 04:00.
        val next = Schedule.nextTrigger(t("2026-10-24T07:00:00"), t("2026-10-24T05:00:00"), daily, zone)
        assertEquals(t("2026-10-25T05:00:00"), next)
    }

    @Test
    fun `interval mode counts from the last change`() {
        val s = Settings(onboarded = true, frequency = FrequencyMode.INTERVAL, intervalHours = 3)
        val last = t("2026-11-10T08:00:00")
        assertFalse(Schedule.isDue(t("2026-11-10T10:30:00"), last, s, zone))
        assertTrue(Schedule.isDue(t("2026-11-10T11:00:00"), last, s, zone))
        assertEquals(t("2026-11-10T11:00:00"), Schedule.nextTrigger(t("2026-11-10T09:00:00"), last, s, zone))
    }

    @Test
    fun `nothing is planned when automatic change is off or before setup`() {
        val now = t("2026-11-10T09:00:00")
        assertNull(Schedule.nextTrigger(now, 0, daily.copy(autoChange = false), zone))
        assertNull(Schedule.nextTrigger(now, 0, daily.copy(onboarded = false), zone))
        assertFalse(Schedule.isDue(now, 0, daily.copy(onboarded = false), zone))
    }

    @Test
    fun `every 3 days counts from the last change`() {
        val s = daily.copy(everyDays = 3)
        val last = t("2026-11-10T05:00:30")
        assertFalse(Schedule.isDue(t("2026-11-12T09:00:00"), last, s, zone))
        assertTrue(Schedule.isDue(t("2026-11-13T05:00:00"), last, s, zone))
        assertEquals(t("2026-11-13T05:00:00"), Schedule.nextTrigger(t("2026-11-11T12:00:00"), last, s, zone))
    }

    @Test
    fun `a picture chosen by hand stays the whole period`() {
        // Chosen at 20:00; with "every 7 days" the next automatic change is a week later at 05:00.
        val s = daily.copy(everyDays = 7)
        val chosen = t("2026-11-10T20:00:00")
        assertEquals(t("2026-11-17T05:00:00"), Schedule.nextTrigger(t("2026-11-11T08:00:00"), chosen, s, zone))
        assertFalse(Schedule.isDue(t("2026-11-16T23:59:00"), chosen, s, zone))
    }

    @Test
    fun `holding the wallpaper stops all automatic changes`() {
        val held = daily.copy(autoChange = false)
        assertFalse(Schedule.isDue(t("2026-12-31T05:00:00"), t("2026-11-01T05:00:00"), held, zone))
        assertNull(Schedule.nextTrigger(t("2026-12-31T05:00:00"), t("2026-11-01T05:00:00"), held, zone))
    }
}

package bg.dnevnitapet.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.Trigger
import bg.dnevnitapet.wallpaper.WallpaperEngine
import java.util.concurrent.TimeUnit

/**
 * Keeps the automatic change reliable without a permanent notification:
 *  1. an exact alarm (allowed while idle) wakes the app at the planned time;
 *  2. an hourly WorkManager check catches anything missed (phone off, alarm dropped);
 *  3. boot, app update, time change, opening the app, the widget and the tile also check;
 *  4. a change that is due but has no prepared picture waits for the network and then happens.
 */
class Scheduler(private val context: Context, private val engine: WallpaperEngine) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val work get() = WorkManager.getInstance(context)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    /** Epoch millis of the next planned automatic change, or null. */
    fun nextPlanned(): Long? {
        val now = System.currentTimeMillis()
        val st = engine.library.value
        val s = engine.settings.value
        // If a change is overdue, it is handled by the catch-up paths; plan the one after it.
        val last = if (Schedule.isDue(now, st.lastChangeAt, s)) now else st.lastChangeAt
        return Schedule.nextTrigger(now, last, s)
    }

    fun reschedule() {
        val pi = alarmIntent()
        val next = nextPlanned()
        if (next == null) {
            alarms.cancel(pi)
            work.cancelUniqueWork(WATCHDOG)
            return
        }
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
        work.enqueueUniquePeriodicWork(
            WATCHDOG,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    /** Prepares pictures in the background as soon as an allowed network is available. */
    fun enqueueRefill(replace: Boolean = false) {
        val wifiOnly = engine.settings.value.wifiOnly
        val request = OneTimeWorkRequestBuilder<RefillWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()
        work.enqueueUniqueWork(REFILL, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** Runs the checks of [runIfDue] soon, outside of a broadcast receiver. */
    fun enqueueCatchUp() {
        work.enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<WatchdogWorker>().build())
    }

    /**
     * Changes the wallpaper if an automatic change is due.
     * @param allowDownload false where there is no time to download (alarm, tile): the change then
     * waits for the background refill instead.
     */
    suspend fun runIfDue(allowDownload: Boolean): ChangeResult? {
        engine.trimQueue()
        val now = System.currentTimeMillis()
        val result = if (Schedule.isDue(now, engine.library.value.lastChangeAt, engine.settings.value)) {
            engine.next(Trigger.SCHEDULED, allowDownload).also {
                if (it !is ChangeResult.Changed) enqueueRefill()
            }
        } else {
            null
        }
        if (engine.library.value.queue.size < WallpaperEngine.QUEUE_SIZE) enqueueRefill()
        reschedule()
        return result
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val WATCHDOG = "watchdog"
        const val REFILL = "refill"
        const val CATCH_UP = "catch-up"
    }
}

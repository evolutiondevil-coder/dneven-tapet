package bg.dnevnitapet.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import bg.dnevnitapet.Graph
import bg.dnevnitapet.wallpaper.ChangeResult

/** Hourly safety net (and one-off catch-up): performs a missed change and keeps the plan alive. */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = Graph.get(applicationContext)
        graph.scheduler.runIfDue(allowDownload = true)
        return Result.success()
    }
}

/** Downloads and prepares pictures; runs only with an allowed network (Wi-Fi only if chosen). */
class RefillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = Graph.get(applicationContext)
        val engine = graph.engine
        engine.refill()
        val changed = graph.scheduler.runIfDue(allowDownload = true)
        val stillWaiting = changed != null && changed !is ChangeResult.Changed
        // Retry later (with growing pauses) only when nothing could be prepared at all;
        // the hourly check starts a new attempt anyway, so no endless retries and no notifications.
        return if ((engine.library.value.queue.isEmpty() || stillWaiting) && runAttemptCount < 4) Result.retry() else Result.success()
    }
}

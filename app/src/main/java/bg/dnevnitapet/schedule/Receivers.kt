package bg.dnevnitapet.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bg.dnevnitapet.Graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Fired by the exact alarm at the planned time. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = Graph.get(context)
        val pending = goAsync()
        graph.appScope.launch {
            try {
                // Setting a prepared picture takes about a second; downloading is left to the worker.
                val done = withTimeoutOrNull(20_000) { graph.scheduler.runIfDue(allowDownload = false) }
                if (done == null) graph.scheduler.enqueueCatchUp()
            } catch (e: Exception) {
                graph.scheduler.enqueueCatchUp()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "bg.dnevnitapet.ALARM"
    }
}

/** Boot, app update and clock changes: plan again and catch up on a missed change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val graph = Graph.get(context)
                graph.scheduler.reschedule()
                graph.scheduler.enqueueCatchUp()
            }
        }
    }
}

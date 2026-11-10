package bg.dnevnitapet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import bg.dnevnitapet.Graph
import bg.dnevnitapet.R
import bg.dnevnitapet.ui.MainActivity
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Home-screen widget: title of the current picture, "favourite" and "next" buttons. */
class NextWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids, busy = false)
        // Periodic widget updates are one more chance to catch up on a missed change.
        val graph = Graph.get(context)
        val pending = goAsync()
        graph.appScope.launch {
            try {
                withTimeoutOrNull(15_000) { graph.scheduler.runIfDue(allowDownload = false) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> onNext(context)
            ACTION_FAVORITE -> onFavorite(context)
            else -> super.onReceive(context, intent)
        }
    }

    private fun onNext(context: Context) {
        val graph = Graph.get(context)
        val pending = goAsync()
        updateAll(context, busy = true)
        graph.appScope.launch {
            try {
                val result = withTimeoutOrNull(25_000) { graph.engine.next(Trigger.MANUAL) }
                val message = when (result) {
                    is ChangeResult.Changed -> null
                    is ChangeResult.NoImage, null -> "Няма готови снимки – ще изтегля, когато има интернет"
                    is ChangeResult.Failed -> result.message
                }
                if (message != null) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                }
                graph.scheduler.enqueueRefill()
                graph.scheduler.reschedule()
            } finally {
                updateAll(context, busy = false)
                pending.finish()
            }
        }
    }

    private fun onFavorite(context: Context) {
        val graph = Graph.get(context)
        val pending = goAsync()
        graph.appScope.launch {
            try {
                val key = graph.library.value.current?.key
                if (key != null) {
                    val nowFavorite = graph.engine.toggleFavorite(key)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, if (nowFavorite) "Добавена в любими" else "Махната от любими", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                updateAll(context, busy = false)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NEXT = "bg.dnevnitapet.widget.NEXT"
        const val ACTION_FAVORITE = "bg.dnevnitapet.widget.FAVORITE"

        fun updateAll(context: Context, busy: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NextWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, busy)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, busy: Boolean) {
            val st = Graph.get(context).library.value
            val current = st.current
            val title = when {
                busy -> "Сменям…"
                current != null -> current.candidate.title
                else -> "Дневен тапет"
            }
            val favorite = current != null && st.isFavorite(current.key)
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_next)
                views.setTextViewText(R.id.widget_title, title)
                views.setImageViewResource(R.id.widget_fav, if (favorite) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 1))
                views.setOnClickPendingIntent(R.id.widget_fav, broadcast(context, ACTION_FAVORITE, 2))
                views.setOnClickPendingIntent(
                    R.id.widget_title,
                    PendingIntent.getActivity(
                        context, 3, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                manager.updateAppWidget(id, views)
            }
        }

        private fun broadcast(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, NextWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

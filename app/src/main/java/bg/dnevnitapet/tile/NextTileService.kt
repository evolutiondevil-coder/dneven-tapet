package bg.dnevnitapet.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import bg.dnevnitapet.Graph
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Settings tile "Следващ тапет": one tap changes the wallpaper. */
class NextTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(busy = false)
        val graph = Graph.get(this)
        graph.appScope.launch { graph.scheduler.runIfDue(allowDownload = false) }
    }

    override fun onClick() {
        super.onClick()
        val graph = Graph.get(this)
        render(busy = true)
        graph.appScope.launch {
            val result = graph.engine.next(Trigger.MANUAL)
            graph.scheduler.enqueueRefill()
            graph.scheduler.reschedule()
            withContext(Dispatchers.Main) {
                render(busy = false)
                val message = when (result) {
                    is ChangeResult.Changed -> null
                    ChangeResult.NoImage -> "Няма готови снимки – ще изтегля, когато има интернет"
                    is ChangeResult.Failed -> result.message
                }
                if (message != null) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(busy: Boolean) {
        val tile = qsTile ?: return
        tile.label = "Следващ тапет"
        tile.state = if (busy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (busy) "Сменям…" else Graph.get(this).library.value.current?.candidate?.title ?: ""
        }
        tile.updateTile()
    }
}

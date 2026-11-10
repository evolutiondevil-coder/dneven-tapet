package bg.dnevnitapet

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.gallery.GallerySaver
import bg.dnevnitapet.model.LibraryState
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.net.OkHttpHttp
import bg.dnevnitapet.schedule.Scheduler
import bg.dnevnitapet.tile.NextTileService
import bg.dnevnitapet.wallpaper.WallpaperEngine
import bg.dnevnitapet.wallpaper.WallpaperSetter
import bg.dnevnitapet.widget.NextWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.appScope.launch {
            graph.scheduler.reschedule()
        }
    }
}

/** Hand-made dependency container: one instance of everything for the whole process. */
class AppGraph(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = OkHttpHttp()
    val settings = JsonFileStore(File(app.filesDir, "settings.json"), Settings.serializer()) { Settings() }
    val library = JsonFileStore(File(app.filesDir, "library.json"), LibraryState.serializer()) { LibraryState() }
    val engine = WallpaperEngine(app, library, settings, http, WallpaperSetter(app))
    val scheduler = Scheduler(app, engine)
    val gallery = GallerySaver(app, http)

    init {
        engine.onChanged = { refreshSurfaces(app) }
    }

    fun refreshSurfaces(context: Context) {
        NextWidgetProvider.updateAll(context)
        runCatching { TileService.requestListeningState(context, ComponentName(context, NextTileService::class.java)) }
    }
}

object Graph {
    fun get(context: Context): AppGraph = (context.applicationContext as App).graph
}

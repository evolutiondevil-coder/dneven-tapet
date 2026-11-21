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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        recordCrashes()
        graph = AppGraph(this)
        graph.appScope.launch {
            // A crash last time: its reason goes to the log (Настройки → За приложението → Дневник).
            val crash = File(filesDir, CRASH_FILE)
            if (crash.exists()) {
                runCatching { graph.engine.log("Приложението беше спряло: " + crash.readText().take(600)) }
                crash.delete()
            }
            graph.scheduler.reschedule()
        }
    }

    /** Keeps the reason of a crash, so it can be seen and fixed later. */
    private fun recordCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val where = e.stackTrace.take(6).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                File(filesDir, CRASH_FILE).writeText("${BuildConfig.VERSION_NAME} ${e.javaClass.simpleName}: ${e.message} @ $where")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        private const val CRASH_FILE = "last-crash.txt"
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
    val updater = bg.dnevnitapet.update.Updater(app, http)
    val stories = bg.dnevnitapet.story.StoryTranslator(app, settings)
    val places = bg.dnevnitapet.story.Places(app, http)
    val archive = bg.dnevnitapet.archive.Archive(app, http)
    val art = bg.dnevnitapet.art.ArtCollection(app, http)

    init {
        engine.onChanged = { refreshSurfaces(app) }
        // Every picture prepared or shown goes into the archive (with a small preview and its colour).
        appScope.launch {
            library.state
                .map { st -> listOfNotNull(st.current) + st.queue + st.history }
                .distinctUntilChanged { a, b -> a.map { it.key to it.shownAt } == b.map { it.key to it.shownAt } }
                .collectLatest { items ->
                    kotlinx.coroutines.delay(2000)
                    runCatching { archive.record(items) }
                }
        }
    }

    fun refreshSurfaces(context: Context) {
        NextWidgetProvider.updateAll(context)
        runCatching { TileService.requestListeningState(context, ComponentName(context, NextTileService::class.java)) }
    }
}

object Graph {
    fun get(context: Context): AppGraph = (context.applicationContext as App).graph
}

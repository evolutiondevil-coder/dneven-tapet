package bg.dnevnitapet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bg.dnevnitapet.Graph
import bg.dnevnitapet.model.LibraryState
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.Trigger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = Graph.get(app)
    private val engine = graph.engine
    private val scheduler = graph.scheduler

    val library: StateFlow<LibraryState> = graph.library.state
    val settings: StateFlow<Settings> = graph.settings.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    /**
     * Picture shown on the main screen while browsing; null = the current wallpaper.
     * Browsing with "Предишна"/"Следваща" only previews – the wallpaper changes on "Избери".
     */
    private val _previewKey = MutableStateFlow<String?>(null)
    val previewKey: StateFlow<String?> = _previewKey.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var pausedAt = 0L

    fun nextPlanned(): Long? = scheduler.nextPlanned()
    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()
    fun cacheBytes(): Long = engine.cacheBytes()

    /** Called when the app comes to the foreground: catch up on a missed change, refill the queue. */
    fun onResume() {
        // Back after a while: start again from the current wallpaper.
        if (pausedAt > 0 && System.currentTimeMillis() - pausedAt > 2 * 60_000L) _previewKey.value = null
        graph.appScope.launch { scheduler.runIfDue(allowDownload = false) }
    }

    fun onPause() {
        pausedAt = System.currentTimeMillis()
    }

    /** Oldest history first, then the current wallpaper, then the prepared (upcoming) pictures. */
    fun browseList(st: LibraryState = library.value): List<StoredImage> =
        st.history.asReversed() + listOfNotNull(st.current) + st.queue

    /** The picture the user swiped to (the current wallpaper counts as "no preview"). */
    fun setPreview(key: String) {
        _previewKey.value = if (key == library.value.current?.key) null else key
    }

    fun showCurrent() {
        _previewKey.value = null
    }

    /**
     * The user reached the end of the prepared pictures: download one more and show it.
     * New pictures are appended at the end, i.e. exactly where the user is looking.
     */
    fun loadMore() {
        if (_loadingMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val before = library.value.queue.map { it.key }.toSet()
                val wanted = (before.size + 1).coerceAtMost(MAX_BROWSE_QUEUE)
                runCatching { engine.refill(minQueue = wanted, budgetMs = 60_000L) }
                val added = library.value.queue.firstOrNull { it.key !in before }
                if (added != null) {
                    _previewKey.value = added.key
                } else {
                    say(
                        when {
                            before.size >= MAX_BROWSE_QUEUE -> "Стигна края – избери някоя от тези или се върни назад."
                            settings.value.wifiOnly -> "Няма повече готови снимки. Нови ще изтегля, когато има Wi-Fi."
                            else -> "Няма повече готови снимки в момента. Опитай пак след малко."
                        },
                    )
                }
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** "Избери": the picture on screen becomes the wallpaper. */
    fun choose(key: String) {
        if (_busy.value || key == library.value.current?.key) return
        val st = library.value
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = when {
                    st.queue.any { it.key == key } -> engine.chooseFromQueue(key)
                    else -> st.history.firstOrNull { it.key == key }?.let { engine.applyStored(it) }
                        ?: ChangeResult.Failed("Снимката вече не е в списъка")
                }
                if (r is ChangeResult.Changed) {
                    _previewKey.value = null
                    say("Тапетът е сменен")
                } else {
                    report(r)
                }
                scheduler.enqueueRefill()
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    /** Immediate change (first wallpaper after setup). */
    fun next() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                report(engine.next(Trigger.MANUAL))
                scheduler.enqueueRefill()
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    fun apply(image: StoredImage) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = engine.applyStored(image)
                if (r is ChangeResult.Changed) say("Тапетът е сменен") else report(r)
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch {
            val nowFavorite = engine.toggleFavorite(key)
            say(if (nowFavorite) "Добавена в любими" else "Махната от любими")
        }
    }

    fun block(key: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                // While browsing, move on to the neighbouring picture (the blocked one disappears).
                if (_previewKey.value == key) {
                    val list = browseList()
                    val i = list.indexOfFirst { it.key == key }
                    _previewKey.value = (list.getOrNull(i + 1) ?: list.getOrNull(i - 1))?.key
                        ?.takeIf { it != library.value.current?.key }
                }
                val r = engine.block(key)
                _messages.emit(UiMessage("Тази снимка няма да се показва повече", "Отмени") { unblock(key) })
                if (r != null && r !is ChangeResult.Changed) report(r)
                scheduler.enqueueRefill()
            } finally {
                _busy.value = false
            }
        }
    }

    private fun unblock(key: String) {
        viewModelScope.launch { engine.unblock(key) }
    }

    fun save(image: StoredImage) {
        viewModelScope.launch {
            say("Запазвам…")
            graph.gallery.save(image, settings.value)
                .onSuccess { original -> say(if (original) "Запазена в Галерия › Дневен тапет" else "Запазена (с размера на екрана – няма връзка за оригинала)") }
                .onFailure { say("Не успях да запазя: ${it.message}") }
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = settings.value
            val after = graph.settings.update(transform)
            if (before.wifiOnly != after.wifiOnly || before.enabledSources != after.enabledSources ||
                before.enabledCategories != after.enabledCategories ||
                before.unsplashKey != after.unsplashKey || before.pexelsKey != after.pexelsKey
            ) {
                scheduler.enqueueRefill(replace = true)
            }
            scheduler.reschedule()
        }
    }

    fun startOnboarding() {
        viewModelScope.launch {
            graph.settings.update { it.copy(onboarded = true) }
            next()
        }
    }

    fun prepareNow() {
        scheduler.enqueueRefill(replace = true)
        say("Подготвям нови снимки на заден план")
    }

    fun clearQueue() {
        viewModelScope.launch {
            engine.clearQueue()
            scheduler.enqueueRefill(replace = true)
            say("Готовите снимки са изтрити – ще изтегля нови")
        }
    }

    private suspend fun report(r: ChangeResult) {
        when (r) {
            is ChangeResult.Changed -> Unit
            ChangeResult.NoImage -> _messages.emit(
                UiMessage(
                    if (settings.value.wifiOnly) "Няма готови снимки. Ще изтегля, когато има Wi-Fi."
                    else "Няма готови снимки и интернет. Тапетът остава същият.",
                ),
            )
            is ChangeResult.Failed -> _messages.emit(UiMessage("Неуспешна смяна: ${r.message}"))
        }
    }

    /** Short message at the top of the screen. */
    fun hint(text: String) = say(text)

    private fun say(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    companion object {
        /** Browsing ahead downloads more pictures, but keeps at most this many prepared. */
        const val MAX_BROWSE_QUEUE = 15
    }
}

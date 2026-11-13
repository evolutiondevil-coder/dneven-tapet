package bg.dnevnitapet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bg.dnevnitapet.Graph
import bg.dnevnitapet.model.LibraryState
import bg.dnevnitapet.model.PhotoMode
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.MyPhotos
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

    /** One-off requests to move the gallery to a picture (by key). */
    private val _scrollRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val scrollRequests: SharedFlow<String> = _scrollRequests

    private fun scrollTo(key: String?) {
        if (key != null) _scrollRequests.tryEmit(key)
    }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var pausedAt = 0L

    fun nextPlanned(): Long? = scheduler.nextPlanned()
    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()
    fun cacheBytes(): Long = engine.cacheBytes()

    /** Called when the app comes to the foreground: catch up on a missed change, refill the queue. */
    fun onResume() {
        // Back after a while: start again from the current wallpaper.
        if (pausedAt > 0 && System.currentTimeMillis() - pausedAt > 2 * 60_000L) {
            _previewKey.value = null
            scrollTo(library.value.current?.key)
        }
        graph.appScope.launch { scheduler.runIfDue(allowDownload = false) }
    }

    fun onPause() {
        pausedAt = System.currentTimeMillis()
    }

    /**
     * Oldest history first, then the current wallpaper, then the upcoming pictures in the order the
     * changes will use them (internet pictures, own photos, or both taking turns).
     */
    fun browseList(st: LibraryState = library.value, s: Settings = settings.value): List<StoredImage> {
        val upcoming = MyPhotos.upcomingMixed(s.photoMode, st.queue, engine.upcomingMine(st), MyPhotos.isMine(st.current?.key))
        return st.history.asReversed() + listOfNotNull(st.current) + upcoming
    }

    /** The picture the user swiped to (the current wallpaper counts as "no preview"). */
    fun setPreview(key: String) {
        bg.dnevnitapet.util.Diag.i("preview -> $key (current=${library.value.current?.key})")
        _previewKey.value = if (key == library.value.current?.key) null else key
        // Close to the end of the prepared pictures: get the next ones ready in the background,
        // so swiping on stays smooth.
        val queue = library.value.queue
        val i = queue.indexOfFirst { it.key == key }
        if (i >= 0 && i >= queue.size - PREFETCH_AHEAD && queue.size < MAX_BROWSE_QUEUE) prefetchAhead()
    }

    private var prefetching = false

    private fun prefetchAhead() {
        if (prefetching || _loadingMore.value) return
        prefetching = true
        engine.lastBrowseAt = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val wanted = (library.value.queue.size + PREFETCH_AHEAD).coerceAtMost(MAX_BROWSE_QUEUE)
                runCatching { engine.refill(minQueue = wanted, budgetMs = 90_000L, minFetchGapMs = 20_000L) }
            } finally {
                prefetching = false
            }
        }
    }

    fun showCurrent() {
        _previewKey.value = null
        scrollTo(library.value.current?.key)
    }

    /**
     * The user reached the end of the prepared pictures: download one more and show it.
     * New pictures are appended at the end, i.e. exactly where the user is looking.
     */
    fun loadMore() {
        if (_loadingMore.value) return
        // Only own photos: nothing to download; the last page says so.
        if (settings.value.photoMode == PhotoMode.MINE) return
        engine.lastBrowseAt = System.currentTimeMillis()
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val before = library.value.queue.map { it.key }.toSet()
                val wanted = (before.size + 1).coerceAtMost(MAX_BROWSE_QUEUE)
                runCatching { engine.refill(minQueue = wanted, budgetMs = 60_000L, minFetchGapMs = 20_000L) }
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
        bg.dnevnitapet.util.Diag.i("choose $key busy=${_busy.value} current=${library.value.current?.key} inQueue=${library.value.queue.any { it.key == key }}")
        if (_busy.value || key == library.value.current?.key) return
        val st = library.value
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = when {
                    st.queue.any { it.key == key } -> engine.chooseFromQueue(key)
                    st.myPhotos.any { it.key == key } -> engine.chooseMine(key)
                    else -> st.history.firstOrNull { it.key == key }?.let { engine.applyStored(it) }
                        ?: ChangeResult.Failed("Снимката вече не е в списъка")
                }
                bg.dnevnitapet.util.Diag.i("choose result: $r")
                if (r is ChangeResult.Changed) {
                    _previewKey.value = null
                    scrollTo(r.image.key)
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
                // The gallery then shows the neighbouring picture (the blocked one disappears).
                if (key != library.value.current?.key) {
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

    fun unblock(key: String) {
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
            if (before.panoramaOn != after.panoramaOn || (after.panoramaOn && before.panoramaScreens != after.panoramaScreens)) {
                refitPanorama(before.panoramaOn)
            }
            if (before.wifiOnly != after.wifiOnly || before.enabledSources != after.enabledSources ||
                before.photoMode != after.photoMode ||
                before.enabledCategories != after.enabledCategories ||
                before.unsplashKey != after.unsplashKey || before.pexelsKey != after.pexelsKey
            ) {
                scheduler.enqueueRefill(replace = true)
            }
            scheduler.reschedule()
        }
    }

    /** (Re)cuts the pictures for the panorama setting that has just changed. */
    private suspend fun refitPanorama(wasOn: Boolean) {
        _busy.value = true
        try {
            if (settings.value.panoramaOn) say("Подготвям по-широки снимки за панорамата…")
            engine.refitForPanorama(wasOn)?.let { if (it is ChangeResult.Failed) report(it) }
        } finally {
            _busy.value = false
        }
        scheduler.enqueueRefill(replace = true)
    }

    /**
     * Switches the panorama on or off. When switched on, the wider pictures are prepared first and
     * then [onReady] opens the system screen where the user confirms the live wallpaper once.
     */
    fun setPanorama(on: Boolean, onReady: () -> Unit) {
        viewModelScope.launch {
            val before = settings.value
            graph.settings.update { it.copy(panorama = on) }
            if (before.panoramaOn != settings.value.panoramaOn) refitPanorama(before.panoramaOn)
            scheduler.reschedule()
            if (on) {
                onReady()
            } else {
                say("Панорамата е изключена – тапетът е пак с размера на екрана")
            }
        }
    }

    fun startOnboarding() {
        viewModelScope.launch {
            graph.settings.update { it.copy(onboarded = true) }
            // Show the wallpaper that is about to be set, not whatever page the gallery was on.
            _previewKey.value = null
            next()
            scrollTo(library.value.current?.key)
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
                    when {
                        settings.value.photoMode == PhotoMode.MINE -> "Още нямаш добавени снимки. Добави ги от „Моите снимки“."
                        settings.value.wifiOnly -> "Няма готови снимки. Ще изтегля, когато има Wi-Fi."
                        else -> "Няма готови снимки и интернет. Тапетът остава същият."
                    },
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

    fun galleryNeedsPermission(): Boolean = graph.gallery.needsPermission()

    // ---------------------------------------------------------------- own photos

    /** (done, total) while photos from the gallery are being added, else null. */
    private val _importing = MutableStateFlow<Pair<Int, Int>?>(null)
    val importing: StateFlow<Pair<Int, Int>?> = _importing.asStateFlow()

    fun myPhotosBytes(): Long = engine.myPhotosBytes()

    fun setPhotoMode(mode: PhotoMode) {
        // With no own photos yet the screen opens the phone's photo picker right away.
        updateSettings { it.copy(photoMode = mode) }
    }

    fun addMyPhotos(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || _importing.value != null) return
        viewModelScope.launch {
            _importing.value = 0 to uris.size
            try {
                val r = engine.importMine(uris) { done, total -> _importing.value = done to total }
                say(
                    buildString {
                        append(if (r.added == 1) "Добавена 1 снимка" else "Добавени ${r.added} снимки")
                        if (r.failed > 0) append(", ${r.failed} не можаха да се отворят")
                        if (r.skipped > 0) append(", ${r.skipped} над лимита от ${MyPhotos.MAX_PHOTOS}")
                    },
                )
            } finally {
                _importing.value = null
            }
        }
    }

    fun removeMine(key: String) {
        viewModelScope.launch {
            engine.removeMine(key)
            say("Снимката е махната от приложението (в галерията си остава)")
        }
    }

    /** "Сложи като тапет" on an own photo in the album. */
    fun applyMine(key: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = engine.chooseMine(key)
                if (r is ChangeResult.Changed) {
                    _previewKey.value = null
                    scrollTo(r.image.key)
                    say("Тапетът е сменен")
                } else {
                    report(r)
                }
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    /** "Задръж": stops (or resumes) the automatic change, so a liked wallpaper stays. */
    fun toggleHold() {
        val holdNow = settings.value.autoChange
        updateSettings { it.copy(autoChange = !holdNow) }
        say(
            if (holdNow) "Тапетът е задържан – няма да се сменя сам, докато не натиснеш „Пусни“."
            else "Автоматичната смяна е пусната отново.",
        )
    }

    // ---------------------------------------------------------------- crop editor

    sealed class CropState {
        data object Idle : CropState()
        data object Loading : CropState()
        data class Ready(
            val image: StoredImage,
            val source: bg.dnevnitapet.wallpaper.WallpaperEngine.EditSource,
            val preview: android.graphics.Bitmap,
            val sourceWidth: Int,
            val sourceHeight: Int,
            /** Where the current wallpaper was cut, if known for this source. */
            val initial: bg.dnevnitapet.image.ImageProcessor.CropFraction?,
            val screenWidth: Int,
            val screenHeight: Int,
        ) : CropState()
        data class Error(val message: String) : CropState()
    }

    private val _crop = MutableStateFlow<CropState>(CropState.Idle)
    val crop: StateFlow<CropState> = _crop.asStateFlow()

    /** Opens the crop editor for [image]: downloads the uncut picture (or uses the wallpaper offline). */
    fun openCrop(image: StoredImage) {
        _crop.value = CropState.Loading
        viewModelScope.launch {
            try {
                val source = engine.editSource(image)
                val preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    bg.dnevnitapet.image.ImageProcessor().loadPreview(source.file, 2048)
                }
                val initial = when {
                    !source.fullPicture -> null
                    image.hasCrop && image.sourceUrl == source.url ->
                        bg.dnevnitapet.image.ImageProcessor.CropFraction(image.cropLeft, image.cropTop, image.cropRight, image.cropBottom)
                    else -> null
                }
                val (_, sh) = bg.dnevnitapet.wallpaper.DeviceInfo.screenSize(getApplication())
                // The frame has the wallpaper's shape: the screen, or wider for the panorama.
                val sw = engine.outputWidth(preview.sourceWidth, preview.sourceHeight)
                _crop.value = CropState.Ready(image, source, preview.bitmap, preview.sourceWidth, preview.sourceHeight, initial, sw, sh)
                if (!source.fullPicture) say("Няма връзка с източника – можеш само да приближиш в рамките на сегашния тапет.")
            } catch (e: Exception) {
                _crop.value = CropState.Error(e.message ?: "Снимката не може да се отвори")
            }
        }
    }

    /** Saves the new cut; the current wallpaper is updated right away. */
    fun applyCrop(fraction: bg.dnevnitapet.image.ImageProcessor.CropFraction, onDone: () -> Unit) {
        val ready = _crop.value as? CropState.Ready ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = engine.recrop(ready.image.key, ready.source, fraction)
                if (r is ChangeResult.Changed) {
                    say(
                        if (library.value.current?.key == ready.image.key) "Тапетът е обновен с новото изрязване"
                        else "Изрязването е запазено – сложи снимката като тапет, когато поискаш",
                    )
                    closeCrop()
                    onDone()
                } else {
                    report(r)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun closeCrop() {
        _crop.value = CropState.Idle
    }

    companion object {
        /** Browsing ahead downloads more pictures, but keeps at most this many prepared. */
        const val MAX_BROWSE_QUEUE = 100

        /** How many pictures ahead are prepared while browsing. */
        private const val PREFETCH_AHEAD = 4
    }
}

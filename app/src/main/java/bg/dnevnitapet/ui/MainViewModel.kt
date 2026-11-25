package bg.dnevnitapet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bg.dnevnitapet.Graph
import bg.dnevnitapet.model.LibraryState
import bg.dnevnitapet.model.PhotoMode
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.label
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.MyPhotos
import bg.dnevnitapet.wallpaper.Trigger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import bg.dnevnitapet.i18n.tr

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = Graph.get(app)
    private val engine = graph.engine
    private val scheduler = graph.scheduler

    val library: StateFlow<LibraryState> = graph.library.state
    val settings: StateFlow<Settings> = graph.settings.state

    /** Translations of titles and places made so far (original → Bulgarian). */
    val translations: StateFlow<bg.dnevnitapet.story.TranslationCache> = graph.stories.cache

    init {
        // New pictures (prepared, shown, liked): their titles are translated in the background.
        viewModelScope.launch {
            graph.library.state
                .map { st -> (listOfNotNull(st.current) + st.queue + st.history.take(12) + st.favorites).map { it.key } }
                .distinctUntilChanged()
                .collectLatest {
                    kotlinx.coroutines.delay(1500)
                    translateTitles()
                }
        }
    }

    /** "Моят дневник": today counts as a day with the app; the first day is remembered. */
    private fun recordOpenedDay() {
        val today = java.time.LocalDate.now().toString()
        viewModelScope.launch {
            val earliest = (graph.archive.state.value.entries.map { it.addedAt } + library.value.history.map { it.shownAt })
                .filter { it > 0 }.minOrNull()
                ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }
                ?.takeIf { it < today } ?: today
            graph.library.update { st ->
                st.copy(
                    openedDays = (st.openedDays + today).distinct().takeLast(400),
                    firstDay = st.firstDay.ifBlank { earliest },
                )
            }
        }
    }

    // ---------------------------------------------------------------- language

    /**
     * Bulgarian or English. A different language than now restarts the app, so that every text
     * is made again in it (at the first start this happens only when the phone's language differs).
     */
    fun setLanguage(lang: bg.dnevnitapet.i18n.AppLanguage, activity: android.app.Activity) {
        viewModelScope.launch {
            val before = bg.dnevnitapet.i18n.I18n.language
            graph.settings.update { it.copy(language = lang.code) }
            applySystemLanguage(activity, lang)
            if (lang != before) {
                bg.dnevnitapet.i18n.I18n.language = lang
                restartApp(activity)
            }
        }
    }

    // ---------------------------------------------------------------- rules by weather

    /** "Сега в София: дъжд" for the settings (null = the city or the weather was not found). */
    fun checkWeather(city: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val sky = runCatching { graph.weather.sky(city) }.getOrNull()
            onResult(sky?.label)
        }
    }

    // ---------------------------------------------------------------- albums

    fun setAlbumsOn(on: Boolean) = updateSettings { it.copy(albumsOn = on) }

    /** A new, empty album; [onCreated] gets its id (to open it and choose the photos). */
    fun createAlbum(name: String, onCreated: (String) -> Unit) {
        val id = java.util.UUID.randomUUID().toString().take(8)
        viewModelScope.launch {
            graph.library.update { it.copy(albums = it.albums + bg.dnevnitapet.model.Album(id, name.trim().ifBlank { tr("Албум") })) }
            onCreated(id)
        }
    }

    fun renameAlbum(id: String, name: String) {
        viewModelScope.launch {
            graph.library.update { st -> st.copy(albums = st.albums.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }) }
        }
    }

    fun deleteAlbum(id: String) {
        viewModelScope.launch {
            graph.library.update { st -> st.copy(albums = st.albums.filter { it.id != id }) }
            updateSettings { s ->
                s.copy(homeAlbum = s.homeAlbum.takeIf { it != id }.orEmpty(), lockAlbum = s.lockAlbum.takeIf { it != id }.orEmpty())
            }
        }
    }

    /** Puts a photo into the album, or takes it out. */
    fun toggleInAlbum(id: String, key: String) {
        viewModelScope.launch {
            graph.library.update { st ->
                st.copy(albums = st.albums.map { a -> if (a.id != id) a else a.copy(keys = if (key in a.keys) a.keys - key else a.keys + key) })
            }
        }
    }

    /** The album the home screen ("" = all own photos) or the lock screen takes its photos from. */
    fun setScreenAlbum(lock: Boolean, id: String) = updateSettings { if (lock) it.copy(lockAlbum = id) else it.copy(homeAlbum = id) }

    // ---------------------------------------------------------------- art break

    /** A painting shown as "Пауза за изкуство", with its title and story in Bulgarian when possible. */
    // ---------------------------------------------------------------- on this day

    private val _dayFact = MutableStateFlow<bg.dnevnitapet.story.DayFact?>(null)

    /** "На този ден" for the logo's card: an event from history on today's date (Wikipedia). */
    val dayFact: StateFlow<bg.dnevnitapet.story.DayFact?> = _dayFact.asStateFlow()
    private var dayFactLoading = false

    /** Loads today's event once a day (at an opening, or when the card opens and it is still missing). */
    fun loadDayFact() {
        val lang = if (bg.dnevnitapet.i18n.I18n.language == bg.dnevnitapet.i18n.AppLanguage.EN) "en" else "bg"
        val today = java.time.LocalDate.now().toString()
        if (_dayFact.value?.let { it.date == today && it.lang == lang } == true || dayFactLoading) return
        graph.onThisDay.cached(lang)?.let { _dayFact.value = it; return }
        dayFactLoading = true
        viewModelScope.launch {
            try {
                graph.onThisDay.today(lang)?.let { _dayFact.value = it }
            } finally {
                dayFactLoading = false
            }
        }
    }

    data class ArtShow(val art: bg.dnevnitapet.art.Artwork, val title: String, val story: String)

    private val _art = MutableStateFlow<ArtShow?>(null)
    val art: StateFlow<ArtShow?> = _art.asStateFlow()
    private var viewedSinceArt = 0
    private var lastViewedKey: String? = null
    private var nextArt: ArtShow? = null
    private var artLoading = false

    /** Counts the pictures browsed; every [ART_EVERY] comes a painting (prepared a few pictures before). */
    private fun countViewed(key: String) {
        if (key == lastViewedKey) return
        lastViewedKey = key
        if (!settings.value.artBreaks) return
        viewedSinceArt++
        if (viewedSinceArt >= ART_EVERY - 4 && nextArt == null) prepareArt()
        if (viewedSinceArt >= ART_EVERY && nextArt != null && _art.value == null) {
            _art.value = nextArt
            nextArt = null
            viewedSinceArt = 0
        }
    }

    private fun prepareArt() {
        if (artLoading) return
        artLoading = true
        viewModelScope.launch {
            try {
                val a = runCatching { graph.art.next() }.getOrNull() ?: return@launch
                val c = a.candidate()
                // In Bulgarian as far as possible (the language pack comes over Wi-Fi once).
                runCatching { graph.stories.prepare() }
                val story = runCatching { graph.stories.story(c) }.getOrNull()
                nextArt = ArtShow(a, story?.title ?: a.title, story?.description ?: a.story)
            } finally {
                artLoading = false
            }
        }
    }

    fun closeArt() {
        _art.value?.let { shown -> graph.appScope.launch { graph.art.markShown(shown.art.id) } }
        _art.value = null
    }

    /** "Сложи като тапет" on the painting. */
    fun applyArt(show: ArtShow) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = engine.applyCandidate(show.art.candidate(), relaxed = true)
                if (r is ChangeResult.Changed) {
                    closeArt()
                    _previewKey.value = null
                    scrollTo(r.image.key)
                    say(tr("Картината е твоят тапет"))
                } else {
                    report(r)
                }
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    // ---------------------------------------------------------------- archive

    /** Every picture prepared or shown, and older ones from NASA's and Wikimedia's archives. */
    val archive: StateFlow<bg.dnevnitapet.archive.ArchiveState> = graph.archive.state

    /** "Търсене в интернет": the words, the results so far and where it stands. Nothing is stored. */
    data class WebSearchState(
        val query: String = "",
        val results: List<bg.dnevnitapet.archive.WebPicture> = emptyList(),
        val loading: Boolean = false,
        val page: Int = 0,
        val end: Boolean = false,
        /** The words in English, also searched (when the Bulgarian language pack is on the phone). */
        val english: String? = null,
    )

    private val _web = MutableStateFlow(WebSearchState())
    val web: StateFlow<WebSearchState> = _web.asStateFlow()
    private var webJob: kotlinx.coroutines.Job? = null
    private var webQueries: List<String> = emptyList()

    /** Searches the internet for [query]; [english] = ready English words (the category chips). */
    fun searchWeb(query: String, english: String? = null) {
        val q = query.trim()
        webJob?.cancel()
        if (q.isEmpty()) {
            _web.value = WebSearchState()
            return
        }
        _web.value = WebSearchState(query = q, loading = true)
        webJob = viewModelScope.launch {
            val en = english ?: runCatching { graph.stories.toEnglish(q) }.getOrNull()
            webQueries = listOfNotNull(english ?: q, en.takeIf { english == null })
            val found = runCatching { graph.webSearch.search(webQueries, 0) }.getOrDefault(emptyList())
            _web.value = WebSearchState(query = q, results = found, loading = false, page = 0, end = found.isEmpty(), english = en)
            // Their titles in Bulgarian, as they come.
            graph.appScope.launch { runCatching { graph.stories.translateTitles(found.map { it.candidate }) } }
        }
    }

    /** The next page, when the end of the results comes into view. */
    fun searchWebMore() {
        val st = _web.value
        if (st.loading || st.end || st.query.isEmpty()) return
        _web.value = st.copy(loading = true)
        webJob = viewModelScope.launch {
            val more = runCatching { graph.webSearch.search(webQueries, st.page + 1) }.getOrDefault(emptyList())
            val known = st.results.map { it.candidate.key }.toSet()
            val fresh = more.filter { it.candidate.key !in known }
            _web.value = _web.value.copy(results = st.results + fresh, loading = false, page = st.page + 1, end = fresh.isEmpty())
            graph.appScope.launch { runCatching { graph.stories.translateTitles(fresh.map { it.candidate }) } }
        }
    }

    /** "Сложи като тапет" in the archive (downloads the picture when it is no longer on the phone). */
    fun applyArchive(entry: bg.dnevnitapet.archive.ArchiveEntry, onDone: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val r = engine.applyCandidate(entry.candidate, relaxed = true)
                if (r is ChangeResult.Changed) {
                    _previewKey.value = null
                    scrollTo(r.image.key)
                    say(tr("Тапетът е сменен"))
                    onDone()
                } else {
                    report(r)
                }
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    private fun translateTitles() {
        val st = library.value
        val items = listOfNotNull(st.current) + st.queue + st.history.take(12) + st.favorites
        graph.appScope.launch { runCatching { graph.stories.translateTitles(items.map { it.candidate }) } }
    }

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

    /**
     * With only own photos in use: the main screen shows the internet pictures after the current
     * one ("От интернет"), so they can be browsed and chosen too.
     */
    private val _browseOnline = MutableStateFlow(false)
    val browseOnline: StateFlow<Boolean> = _browseOnline.asStateFlow()

    /** The gallery shows internet pictures although only own photos are in use. */
    private fun onlineView(): Boolean = settings.value.photoMode == PhotoMode.MINE && _browseOnline.value

    /** "Моите снимки" / "От интернет" on the main screen. */
    fun setBrowseOnline(on: Boolean) {
        if (_browseOnline.value == on) return
        _browseOnline.value = on
        if (!on) {
            showCurrent()
            return
        }
        // Straight to the first internet picture; none prepared yet: download one.
        val first = library.value.queue.firstOrNull()
        if (first != null) {
            _previewKey.value = first.key
            scrollTo(first.key)
            setPreview(first.key)
        } else {
            say(tr("Изтеглям снимки от интернет…"))
            loadMore()
        }
    }

    private var pausedAt = 0L

    fun nextPlanned(): Long? = scheduler.nextPlanned()
    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()
    fun cacheBytes(): Long = engine.cacheBytes()

    // ---------------------------------------------------------------- new version

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Downloading : UpdateState()
    }

    /** A newer version on GitHub (null = up to date or not checked yet). */
    val updateAvailable: StateFlow<bg.dnevnitapet.update.ReleaseInfo?> = graph.updater.available

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** "По-късно" hides the question until the app is opened again. */
    private val _updatePromptDismissed = MutableStateFlow(false)
    val updatePromptDismissed: StateFlow<Boolean> = _updatePromptDismissed.asStateFlow()

    /** Waiting for the user to allow installing updates in the system settings. */
    private var installAfterPermission = false

    /** Looks on GitHub for a newer version; [manual] = "Провери сега" (always asks, reports the result). */
    fun checkForUpdate(manual: Boolean) {
        if (!manual) {
            // At an opening the network is often not ready yet (the phone just woke up): a failed
            // question is asked again a few times instead of trusting an old answer.
            updateJob?.cancel()
            updateJob = viewModelScope.launch {
                for (wait in listOf(0L, 4_000L, 10_000L, 30_000L)) {
                    kotlinx.coroutines.delay(wait)
                    if (runCatching { graph.updater.check(minGapMs = UPDATE_CHECK_GAP_MS, allowStale = false) }.isSuccess) return@launch
                }
                runCatching { graph.updater.check(minGapMs = UPDATE_CHECK_GAP_MS) }
            }
            return
        }
        viewModelScope.launch {
            val r = runCatching { graph.updater.check(minGapMs = 0L) }
            val newer = r.getOrNull()
            say(
                when {
                    r.isFailure -> tr("Не успях да проверя за нова версия – няма връзка с GitHub")
                    newer != null -> tr("Има нова версия {0}", newer.version)
                    else -> tr("Имаш най-новата версия ({0})", bg.dnevnitapet.BuildConfig.VERSION_NAME)
                },
            )
            if (newer != null) _updatePromptDismissed.value = false
        }
    }

    private var updateJob: kotlinx.coroutines.Job? = null

    fun dismissUpdatePrompt() {
        _updatePromptDismissed.value = true
    }

    /** "Обнови": downloads the new version and opens the phone's installer. */
    fun installUpdate() {
        val release = updateAvailable.value ?: return
        if (_updateState.value == UpdateState.Downloading) return
        val updater = graph.updater
        if (!updater.canInstall()) {
            installAfterPermission = true
            say(tr("Разреши „Инсталиране от този източник“ и се върни – обновяването ще продължи само"))
            if (!updater.openInstallPermission()) say(tr("Отвори Настройки → Приложения → Дневен тапет и разреши инсталиране на приложения"))
            return
        }
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading
            try {
                val file = updater.download(release)
                updater.install(file)
            } catch (e: Exception) {
                say(tr("Новата версия не се изтегли: {0}. Опитай пак.", e.message ?: tr("грешка")))
            } finally {
                _updateState.value = UpdateState.Idle
            }
        }
    }

    // ---------------------------------------------------------------- what's new, daily greeting

    data class GreetingInfo(val greeting: String, val date: String, val line: String)

    private val _whatsNew = MutableStateFlow<List<bg.dnevnitapet.update.ReleaseNotes.Item>>(emptyList())

    /** "Какво ново" items, shown once after an update (empty = nothing to show). */
    val whatsNew: StateFlow<List<bg.dnevnitapet.update.ReleaseNotes.Item>> = _whatsNew.asStateFlow()

    private val _greeting = MutableStateFlow<GreetingInfo?>(null)

    /** The greeting of the first opening of the day. */
    val greeting: StateFlow<GreetingInfo?> = _greeting.asStateFlow()

    fun closeWhatsNew() {
        _whatsNew.value = emptyList()
        viewModelScope.launch { graph.settings.update { it.copy(lastSeenVersion = bg.dnevnitapet.BuildConfig.VERSION_NAME) } }
    }

    fun closeGreeting() {
        _greeting.value = null
        // The morning greeting already said hello: the main screen only settles in.
        _entrance.value = Entrance(System.currentTimeMillis(), "")
    }

    /** One opening of the app: the picture settles in, the controls rise, [phrase] shows for a moment ("" = none). */
    data class Entrance(val id: Long, val phrase: String)

    private val _entrance = MutableStateFlow<Entrance?>(null)
    val entrance: StateFlow<Entrance?> = _entrance.asStateFlow()
    private var lastPhrase: String? = null
    private var resumedBefore = false

    fun entranceDone() {
        _entrance.value = null
    }

    /** Every opening (start, or back after a while): the entrance, unless a bigger greeting is shown. */
    private fun checkEntrance() {
        val now = System.currentTimeMillis()
        val s = settings.value
        if (!s.onboarded || _greeting.value != null || _whatsNew.value.isNotEmpty()) return
        val phrase = Greeting.openPhrase(java.time.LocalDateTime.now(), lastPhrase)
        lastPhrase = phrase
        _entrance.value = Entrance(now, phrase)
    }

    /** After an update "Какво ново" (once); otherwise, on the first opening of the day, the greeting. */
    private fun checkWelcome() {
        val s = settings.value
        if (!s.onboarded || _whatsNew.value.isNotEmpty() || _greeting.value != null) return
        val today = java.time.LocalDate.now().toString()
        val version = bg.dnevnitapet.BuildConfig.VERSION_NAME
        if (bg.dnevnitapet.update.Versions.parse(version) != null && s.lastSeenVersion != version) {
            val items = runCatching {
                getApplication<Application>().assets.open(
                    if (bg.dnevnitapet.i18n.I18n.language == bg.dnevnitapet.i18n.AppLanguage.EN) "RELEASE_NOTES.en.md" else "RELEASE_NOTES.md",
                ).bufferedReader().use {
                    bg.dnevnitapet.update.ReleaseNotes.current(it.readText())
                }
            }.getOrDefault(emptyList())
            viewModelScope.launch { graph.settings.update { it.copy(greetedDay = today, lastSeenVersion = if (items.isEmpty()) version else it.lastSeenVersion) } }
            if (items.isNotEmpty()) {
                _whatsNew.value = items
                return
            }
        }
        if (s.greetedDay == today) return
        viewModelScope.launch { graph.settings.update { it.copy(greetedDay = today) } }
        val current = library.value.current
        _greeting.value = GreetingInfo(
            greeting = Greeting.phrase(java.time.LocalTime.now().hour),
            date = Greeting.date(java.time.LocalDate.now()),
            line = when {
                current == null -> tr("Днес ще ти сложа нова снимка.")
                MyPhotos.isMine(current.key) -> tr("Днешният ти тапет е твоя снимка.")
                else -> tr("Днешният ти тапет е от {0}:\n{1}", bg.dnevnitapet.sources.SourceRegistry.name(current.candidate.sourceId), graph.stories.title(current.candidate))
            },
        )
    }

    /** Called when the app comes to the foreground: catch up on a missed change, refill the queue. */
    fun onResume() {
        // Start, or back in the app after a while: a new opening.
        val opened = !resumedBefore || (pausedAt > 0 && System.currentTimeMillis() - pausedAt > OPEN_GAP_MS)
        resumedBefore = true
        checkWelcome()
        if (opened) {
            checkEntrance()
            recordOpenedDay()
            loadDayFact()
            // Bulgarian titles: the language pack comes once over Wi-Fi, then everything is translated on the phone.
            if (settings.value.translateStories) {
                graph.appScope.launch { if (runCatching { graph.stories.prepare() }.getOrDefault(false)) translateTitles() }
            }
            // Every opening looks on GitHub for a newer version and asks again ("По-късно" lasts until the next opening).
            if (settings.value.checkUpdates) {
                _updatePromptDismissed.value = false
                checkForUpdate(manual = false)
            }
        }
        if (installAfterPermission && graph.updater.canInstall()) {
            installAfterPermission = false
            installUpdate()
        }
        // Back after a while: start again from the current wallpaper.
        if (pausedAt > 0 && System.currentTimeMillis() - pausedAt > 2 * 60_000L) {
            _previewKey.value = null
            _browseOnline.value = false
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
    fun browseList(st: LibraryState = library.value, s: Settings = settings.value, online: Boolean = _browseOnline.value): List<StoredImage> {
        val upcoming = MyPhotos.upcomingShown(s.photoMode, st.queue, engine.upcomingMine(st), MyPhotos.isMine(st.current?.key), online)
        return MyPhotos.browseOrder(st.history, st.current, upcoming) { it.key }
    }

    /** The picture the user swiped to (the current wallpaper counts as "no preview"). */
    fun setPreview(key: String) {
        bg.dnevnitapet.util.Diag.i("preview -> $key (current=${library.value.current?.key})")
        _previewKey.value = if (key == library.value.current?.key) null else key
        countViewed(key)
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
                runCatching { engine.refill(minQueue = wanted, budgetMs = 90_000L, minFetchGapMs = 20_000L, forBrowsing = onlineView()) }
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
        // Only own photos: nothing to download; the last page says so (unless browsing "От интернет").
        if (settings.value.photoMode == PhotoMode.MINE && !onlineView()) return
        engine.lastBrowseAt = System.currentTimeMillis()
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val before = library.value.queue.map { it.key }.toSet()
                val wanted = (before.size + 1).coerceAtMost(MAX_BROWSE_QUEUE)
                runCatching { engine.refill(minQueue = wanted, budgetMs = 60_000L, minFetchGapMs = 20_000L, forBrowsing = onlineView()) }
                val added = library.value.queue.firstOrNull { it.key !in before }
                if (added != null) {
                    _previewKey.value = added.key
                    scrollTo(added.key)
                } else {
                    say(
                        when {
                            before.size >= MAX_BROWSE_QUEUE -> tr("Стигна до края – избери някоя от тези или се върни назад.")
                            settings.value.wifiOnly -> tr("Няма повече готови снимки. Нови ще изтегля, когато има Wi-Fi.")
                            else -> tr("Няма повече готови снимки в момента. Опитай пак след малко.")
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
                        ?: ChangeResult.Failed(tr("Снимката вече не е в списъка"))
                }
                bg.dnevnitapet.util.Diag.i("choose result: $r")
                if (r is ChangeResult.Changed) {
                    _previewKey.value = null
                    scrollTo(r.image.key)
                    say(tr("Тапетът е сменен"))
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
                if (r is ChangeResult.Changed) say(tr("Тапетът е сменен")) else report(r)
                scheduler.reschedule()
            } finally {
                _busy.value = false
            }
        }
    }

    fun toggleFavorite(key: String) {
        viewModelScope.launch {
            val nowFavorite = engine.toggleFavorite(key)
            say(if (nowFavorite) tr("Добавена в любими") else tr("Махната от любими"))
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
                _messages.emit(UiMessage(tr("Тази снимка няма да се показва повече"), tr("Отмени")) { unblock(key) })
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
            say(tr("Запазвам…"))
            graph.gallery.save(image, settings.value)
                .onSuccess { original -> say(if (original) tr("Запазена в Галерия › Дневен тапет") else tr("Запазена (с размера на екрана – няма връзка за оригинала)")) }
                .onFailure { say(tr("Не успях да запазя: {0}", it.message)) }
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = settings.value
            val after = graph.settings.update(transform)
            applyLiveChange(before, after)
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

    /**
     * The panorama or an effect changed: pictures are cut again for a changed panorama; when no
     * effect is left, the home screen gets a plain wallpaper again.
     */
    private suspend fun applyLiveChange(before: Settings, after: Settings) {
        if (before.panoramaOn != after.panoramaOn || (after.panoramaOn && before.panoramaScreens != after.panoramaScreens)) {
            refitPanorama(before.liveOn)
        } else if (before.liveOn && !after.liveOn) {
            engine.leaveLive()
        }
    }

    /**
     * Switches a home-screen effect (or the panorama) on or off. When the app's live wallpaper is
     * newly needed, [onReady] opens the system screen where the user confirms it once.
     */
    fun setLiveOption(transform: (Settings) -> Settings, onReady: () -> Unit) {
        viewModelScope.launch {
            val before = settings.value
            graph.settings.update(transform)
            val after = settings.value
            applyLiveChange(before, after)
            scheduler.reschedule()
            when {
                after.liveOn && !before.liveOn -> onReady()
                before.liveOn && !after.liveOn -> say(tr("Ефектите са изключени – тапетът е пак обикновен"))
            }
        }
    }

    /** What the app has learned from the favourites and the hidden pictures ("" = nothing yet). */
    fun tasteSummary(): String = bg.dnevnitapet.wallpaper.Taste.describe(
        engine.tasteProfile(),
        bg.dnevnitapet.sources.SourceRegistry::name,
        { it.label },
    )

    /** (Re)cuts the pictures for the panorama setting that has just changed. */
    private suspend fun refitPanorama(wasOn: Boolean) {
        _busy.value = true
        try {
            if (settings.value.panoramaOn) say(tr("Подготвям по-широки снимки за панорамата…"))
            engine.refitForPanorama(wasOn)?.let { if (it is ChangeResult.Failed) report(it) }
        } finally {
            _busy.value = false
        }
        scheduler.enqueueRefill(replace = true)
    }

    /** Switches the panorama on or off (wider pictures are prepared first). */
    fun setPanorama(on: Boolean, onReady: () -> Unit) = setLiveOption({ it.copy(panorama = on) }, onReady)

    fun startOnboarding() {
        viewModelScope.launch {
            val today = java.time.LocalDate.now().toString()
            graph.settings.update { it.copy(onboarded = true, greetedDay = today, lastSeenVersion = bg.dnevnitapet.BuildConfig.VERSION_NAME) }
            // Show the wallpaper that is about to be set, not whatever page the gallery was on.
            _previewKey.value = null
            next()
            scrollTo(library.value.current?.key)
        }
    }

    fun prepareNow() {
        scheduler.enqueueRefill(replace = true)
        say(tr("Подготвям нови снимки на заден план"))
    }

    fun clearQueue() {
        viewModelScope.launch {
            engine.clearQueue()
            scheduler.enqueueRefill(replace = true)
            say(tr("Готовите снимки са изтрити – ще изтегля нови"))
        }
    }

    private suspend fun report(r: ChangeResult) {
        when (r) {
            is ChangeResult.Changed -> Unit
            ChangeResult.NoImage -> _messages.emit(
                UiMessage(
                    when {
                        settings.value.photoMode == PhotoMode.MINE -> tr("Още нямаш добавени снимки. Добави ги от „Моите снимки“.")
                        settings.value.wifiOnly -> tr("Няма готови снимки. Ще изтегля, когато има Wi-Fi.")
                        else -> tr("Няма готови снимки и интернет. Тапетът остава същият.")
                    },
                ),
            )
            is ChangeResult.Failed -> _messages.emit(UiMessage(tr("Неуспешна смяна: {0}", r.message)))
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
        _browseOnline.value = false
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
                        append(if (r.added == 1) tr("Добавена 1 снимка") else tr("Добавени {0} снимки", r.added))
                        if (r.failed > 0) append(tr(", {0} не можаха да се отворят", r.failed))
                        if (r.skipped > 0) append(tr(", {0} над лимита от {1}", r.skipped, MyPhotos.MAX_PHOTOS))
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
            say(tr("Снимката е махната от приложението (в галерията си остава)"))
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
                    say(tr("Тапетът е сменен"))
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
            if (holdNow) tr("Тапетът е задържан – няма да се сменя сам, докато не натиснеш „Пусни“.")
            else tr("Автоматичната смяна е пусната отново."),
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
                if (!source.fullPicture) say(tr("Няма връзка с източника – можеш само да приближиш в рамките на сегашния тапет."))
            } catch (e: Exception) {
                _crop.value = CropState.Error(e.message ?: tr("Снимката не може да се отвори"))
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
                        if (library.value.current?.key == ready.image.key) tr("Тапетът е обновен с новото изрязване")
                        else tr("Изрязването е запазено – сложи снимката като тапет, когато поискаш"),
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

        /** "Пауза за изкуство" after this many pictures browsed. */
        private const val ART_EVERY = 20

        /** Back in the app after at least this long counts as a new opening (the entrance plays). */
        private const val OPEN_GAP_MS = 20_000L

        /** Openings closer together than this reuse the last answer from GitHub (it allows 60 questions an hour). */
        private const val UPDATE_CHECK_GAP_MS = 2 * 60_000L
    }
}

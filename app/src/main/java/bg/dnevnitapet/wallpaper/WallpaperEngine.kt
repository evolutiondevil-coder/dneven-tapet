package bg.dnevnitapet.wallpaper

import android.content.Context
import bg.dnevnitapet.data.JsonFileStore
import bg.dnevnitapet.image.ImageProcessor
import bg.dnevnitapet.image.VariantChooser
import bg.dnevnitapet.model.Candidate
import bg.dnevnitapet.model.LibraryState
import bg.dnevnitapet.model.LogEntry
import bg.dnevnitapet.model.PhotoMode
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.SourceStatus
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.net.Http
import bg.dnevnitapet.sources.CategoryClassifier
import bg.dnevnitapet.sources.FetchRequest
import bg.dnevnitapet.sources.ImageSource
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.sources.unsplash.UnsplashSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

enum class Trigger { MANUAL, SCHEDULED }

/** Short human-readable reason, including the root cause of wrapped errors. */
internal fun describe(e: Throwable): String {
    e.message?.takeIf { it.isNotBlank() && e.cause == null }?.let { return it }
    val root = generateSequence(e) { it.cause }.last()
    val rootMessage = root.message?.takeIf { it.isNotBlank() }
    return when {
        rootMessage != null && root !== e -> root.javaClass.simpleName + ": " + rootMessage.take(200)
        rootMessage != null -> rootMessage.take(200)
        else -> root.javaClass.simpleName
    }
}

sealed class ChangeResult {
    data class Changed(val image: StoredImage) : ChangeResult()
    /** No prepared picture and no allowed internet connection: the old wallpaper stays. */
    data object NoImage : ChangeResult()
    data class Failed(val message: String) : ChangeResult()
}

/**
 * The heart of the app: keeps a queue of prepared pictures so that "Next" is instant and works
 * offline, refills it in the background from all enabled sources, and keeps history and favourites.
 */
class WallpaperEngine(
    private val context: Context,
    val library: JsonFileStore<LibraryState>,
    val settings: JsonFileStore<Settings>,
    private val http: Http,
    private val setter: WallpaperSetter,
    private val processor: ImageProcessor = ImageProcessor(),
    private val sources: List<ImageSource> = SourceRegistry.all,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val changeMutex = Mutex()
    private val refillMutex = Mutex()

    private val imagesDir = File(context.filesDir, "images")
    private val favoritesDir = File(context.filesDir, "favorites")
    private val downloadsDir = File(context.cacheDir, "downloads")
    /** The user's own photos: "<id>.jpg" (screen-sized) and "<id>.src.jpg" (upright copy for re-cutting). */
    private val mineDir = File(context.filesDir, "mine")

    /** Called after every wallpaper change (widget and tile refresh themselves). */
    var onChanged: (() -> Unit)? = null

    // ---------------------------------------------------------------- changing the wallpaper

    suspend fun next(trigger: Trigger, allowDownload: Boolean = true): ChangeResult = changeMutex.withLock {
        withContext(Dispatchers.IO) {
            val mode = settings.value.photoMode
            val st0 = library.value
            if (MyPhotos.useMineNext(mode, st0.current?.key, usableMine(st0).isNotEmpty())) {
                nextMineLocked(trigger)?.let { return@withContext it }
                if (mode == PhotoMode.MINE) {
                    log("Няма добавени собствени снимки – тапетът остава същият")
                    return@withContext ChangeResult.NoImage
                }
            }
            var item = takeFromQueue(preferFresh = trigger == Trigger.SCHEDULED)
            if (item == null && allowDownload && DeviceInfo.isNetworkAllowed(context, settings.value)) {
                runCatching { withTimeout(60_000) { refill(minQueue = 1) } }
                item = takeFromQueue(preferFresh = false)
            }
            if (item == null) {
                log("Няма готова снимка – тапетът остава същият")
                return@withContext ChangeResult.NoImage
            }
            try {
                setter.apply(File(item.file), settings.value.target)
            } catch (e: Exception) {
                library.update { it.copy(queue = listOf(item) + it.queue) }
                log("Грешка при смяна на тапета: ${describe(e)}")
                return@withContext ChangeResult.Failed(describe(e))
            }
            val shown = commitShown(item, trigger)
            pingUnsplash(item.candidate)
            onChanged?.invoke()
            ChangeResult.Changed(shown)
        }
    }

    /** Sets a picture from the history or the favourites. */
    suspend fun applyStored(image: StoredImage): ChangeResult = changeMutex.withLock {
        withContext(Dispatchers.IO) { applyCopyLocked(image, Trigger.MANUAL) }
    }

    /**
     * Sets a copy of [image] as the wallpaper (history, favourites and own photos keep their own
     * files; the current wallpaper gets a separate copy). Caller holds [changeMutex].
     */
    private suspend fun applyCopyLocked(image: StoredImage, trigger: Trigger): ChangeResult {
        val src = File(image.file)
        if (!src.exists()) return ChangeResult.Failed("Файлът на снимката липсва")
        val copy = File(imagesDir, fileName(image.key) + "-" + clock() + ".jpg")
        imagesDir.mkdirs()
        src.copyTo(copy, overwrite = true)
        try {
            setter.apply(copy, settings.value.target)
        } catch (e: Exception) {
            copy.delete()
            log("Грешка при смяна на тапета: ${describe(e)}")
            return ChangeResult.Failed(describe(e))
        }
        val shown = commitShown(image.copy(file = copy.path), trigger)
        onChanged?.invoke()
        return ChangeResult.Changed(shown)
    }

    // ---------------------------------------------------------------- own photos

    private fun usableMine(st: LibraryState) = st.myPhotos.filter { it.key !in st.blocked && File(it.file).exists() }

    /** Own photos in the order they will come. */
    fun upcomingMine(st: LibraryState = library.value): List<StoredImage> {
        val album = usableMine(st)
        val keys = MyPhotos.upcoming(album.map { it.key }, st.myRotation, st.current?.key)
        return keys.mapNotNull { k -> album.firstOrNull { it.key == k } }
    }

    private suspend fun nextMineLocked(trigger: Trigger): ChangeResult? {
        val st = library.value
        val album = usableMine(st)
        val (key, rest) = MyPhotos.nextKey(album.map { it.key }, st.myRotation, st.current?.key) ?: return null
        val item = album.first { it.key == key }
        val r = applyCopyLocked(item, trigger)
        if (r is ChangeResult.Changed) library.update { it.copy(myRotation = rest) }
        return r
    }

    /** "Избери" on one of the user's own photos. */
    suspend fun chooseMine(key: String): ChangeResult = changeMutex.withLock {
        withContext(Dispatchers.IO) {
            val item = library.value.myPhotos.firstOrNull { it.key == key }
                ?: return@withContext ChangeResult.Failed("Снимката вече не е в „Моите снимки“")
            val r = applyCopyLocked(item, Trigger.MANUAL)
            if (r is ChangeResult.Changed) library.update { it.copy(myRotation = it.myRotation - key) }
            r
        }
    }

    data class ImportResult(val added: Int, val failed: Int, val skipped: Int)

    /**
     * Copies photos picked in the phone's gallery into the app: an upright copy (for re-cutting)
     * and a screen-sized wallpaper cut "smartly". The originals in the gallery are not touched.
     */
    suspend fun importMine(
        uris: List<android.net.Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val room = (MyPhotos.MAX_PHOTOS - library.value.myPhotos.size).coerceAtLeast(0)
        val todo = uris.take(room)
        val (tw, th) = DeviceInfo.screenSize(context)
        mineDir.mkdirs()
        downloadsDir.mkdirs()
        var added = 0
        var failed = 0
        todo.forEachIndexed { i, uri ->
            onProgress(i, todo.size)
            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val tmp = File(downloadsDir, "import-$id.tmp")
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    ?: error("снимката не може да се прочете")
                val taken = runCatching {
                    androidx.exifinterface.media.ExifInterface(tmp).getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                }.getOrNull()
                val date = runCatching {
                    LocalDate.parse(taken!!.take(10).replace(':', '-'))
                }.getOrNull()
                val src = File(mineDir, "$id.src.jpg")
                processor.saveUprightCopy(tmp, src, MINE_SOURCE_SIDE)
                val wall = File(mineDir, "$id.jpg")
                val out = processor.process(src, wall, tw, th, composedForPortrait = false, allowUpscale = true)
                val bg = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("bg"))
                val candidate = Candidate(
                    key = "${MyPhotos.SOURCE_ID}:$id",
                    sourceId = MyPhotos.SOURCE_ID,
                    title = "Моя снимка",
                    description = date?.let { "Заснета на " + it.format(bg) }.orEmpty(),
                    date = date?.toString().orEmpty(),
                    variants = emptyList(),
                )
                val item = StoredImage(
                    candidate, wall.path, out.width, out.height, addedAt = clock(),
                    sourceUrl = "file:" + src.path,
                    cropLeft = out.crop.left, cropTop = out.crop.top, cropRight = out.crop.right, cropBottom = out.crop.bottom,
                )
                library.update { st -> st.copy(myPhotos = st.myPhotos + item) }
                added++
            } catch (e: Exception) {
                failed++
                log("Моя снимка не се добави: ${describe(e)}")
            } finally {
                tmp.delete()
            }
        }
        onProgress(todo.size, todo.size)
        if (added > 0) log("Добавени собствени снимки: $added")
        ImportResult(added, failed, skipped = uris.size - todo.size)
    }

    /** Removes an own photo from the app (the original in the gallery stays). */
    suspend fun removeMine(key: String) {
        val item = library.value.myPhotos.firstOrNull { it.key == key } ?: return
        library.update { st -> st.copy(myPhotos = st.myPhotos.filter { it.key != key }, myRotation = st.myRotation - key) }
        withContext(Dispatchers.IO) {
            File(item.file).delete()
            if (item.sourceUrl.startsWith("file:")) File(item.sourceUrl.removePrefix("file:")).delete()
        }
    }

    fun myPhotosBytes(): Long = mineDir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * "Избери": sets a prepared picture the user found while browsing with "Следваща".
     * The prepared pictures browsed past before it were passed over, so they are dropped
     * and not offered again.
     */
    suspend fun chooseFromQueue(key: String): ChangeResult = changeMutex.withLock {
        withContext(Dispatchers.IO) {
            val queue = library.value.queue
            val index = queue.indexOfFirst { it.key == key }
            if (index < 0) return@withContext ChangeResult.Failed("Снимката вече не е в списъка")
            val item = queue[index]
            if (!File(item.file).exists()) return@withContext ChangeResult.Failed("Файлът на снимката липсва")
            try {
                setter.apply(File(item.file), settings.value.target)
            } catch (e: Exception) {
                log("Грешка при смяна на тапета: ${describe(e)}")
                return@withContext ChangeResult.Failed(describe(e))
            }
            val skipped = queue.take(index).map { it.key }.toSet()
            library.update { st ->
                st.copy(
                    queue = st.queue.filter { it.key != key && it.key !in skipped },
                    seen = (st.seen + skipped).distinct().takeLast(SEEN_LIMIT),
                )
            }
            val shown = commitShown(item, Trigger.MANUAL)
            pingUnsplash(item.candidate)
            onChanged?.invoke()
            ChangeResult.Changed(shown)
        }
    }

    private suspend fun takeFromQueue(preferFresh: Boolean): StoredImage? {
        var taken: StoredImage? = null
        val today = LocalDate.now()
        val recent = setOf(today.toString(), today.minusDays(1).toString())
        library.update { st ->
            val valid = st.queue.filter { File(it.file).exists() && it.key !in st.blocked }
            val pick = (if (preferFresh) valid.firstOrNull { it.candidate.date in recent } else null) ?: valid.firstOrNull()
            taken = pick
            st.copy(queue = valid.filter { it !== pick })
        }
        return taken
    }

    private suspend fun commitShown(item: StoredImage, trigger: Trigger): StoredImage {
        val now = clock()
        val shown = item.copy(shownAt = now)
        library.update { st ->
            val history = (listOfNotNull(st.current) + st.history)
                .filter { it.key != shown.key && it.key !in st.blocked }
                .take(HISTORY_SIZE)
            st.copy(
                current = shown,
                history = history,
                seen = (st.seen + shown.key).distinct().takeLast(SEEN_LIMIT),
                lastChangeAt = now,
                lastAutoChangeAt = if (trigger == Trigger.SCHEDULED) now else st.lastAutoChangeAt,
                log = appendLog(st.log, (if (trigger == Trigger.SCHEDULED) "Автоматична смяна: " else "Смяна: ") + shown.candidate.title),
            )
        }
        cleanupFiles()
        return shown
    }

    // ---------------------------------------------------------------- favourites, blocking

    suspend fun toggleFavorite(key: String): Boolean = withContext(Dispatchers.IO) {
        val st = library.value
        val existing = st.favorites.firstOrNull { it.key == key }
        if (existing != null) {
            library.update { it.copy(favorites = it.favorites.filter { f -> f.key != key }) }
            File(existing.file).delete()
            return@withContext false
        }
        val image = (listOfNotNull(st.current) + st.history + st.queue + st.myPhotos).firstOrNull { it.key == key }
            ?: return@withContext false
        val src = File(image.file)
        if (!src.exists()) return@withContext false
        favoritesDir.mkdirs()
        val dest = File(favoritesDir, fileName(key) + ".jpg")
        src.copyTo(dest, overwrite = true)
        library.update { it.copy(favorites = listOf(image.copy(file = dest.path)) + it.favorites.filter { f -> f.key != key }) }
        onChanged?.invoke()
        true
    }

    /** "Never show again": remembers the picture and, if it is on screen now, moves on. */
    suspend fun block(key: String): ChangeResult? {
        library.update { st ->
            st.copy(
                blocked = st.blocked + key,
                queue = st.queue.filter { it.key != key },
                history = st.history.filter { it.key != key },
                log = appendLog(st.log, "Скрита завинаги: " + (st.current?.takeIf { it.key == key }?.candidate?.title ?: key)),
            )
        }
        return if (library.value.current?.key == key) next(Trigger.MANUAL) else null
    }

    suspend fun unblock(key: String) {
        library.update { it.copy(blocked = it.blocked - key) }
    }

    /** Last time the user browsed ahead in the app; extra prepared pictures are kept meanwhile. */
    @Volatile
    var lastBrowseAt: Long = 0

    /**
     * Browsing can prepare up to 100 pictures. When the user has not browsed for a while, the
     * extra ones beyond [QUEUE_SIZE] x 2 are removed to free space; they go back to the list of
     * candidates (not "seen"), so they can still come later.
     */
    suspend fun trimQueue(idleMs: Long = 30 * 60_000L) {
        if (clock() - lastBrowseAt < idleMs) return
        val keep = QUEUE_SIZE * 2
        if (library.value.queue.size <= keep) return
        library.update { st ->
            val extra = st.queue.drop(keep)
            st.copy(
                queue = st.queue.take(keep),
                pool = (extra.map { it.candidate } + st.pool).distinctBy { it.key }.take(POOL_LIMIT),
            )
        }
        cleanupFiles()
    }

    // ---------------------------------------------------------------- crop editor

    /** A picture file for the crop editor plus the URL it came from ("" = the wallpaper file itself). */
    class EditSource(val file: File, val url: String, val fullPicture: Boolean)

    /**
     * Gets the uncut picture for the crop editor (for Bing/Spotlight the wide 3840 px one).
     * Without an allowed connection the wallpaper file itself is used: then one can only zoom in.
     */
    suspend fun editSource(image: StoredImage): EditSource = withContext(Dispatchers.IO) {
        if (image.sourceUrl.startsWith("file:")) {
            val own = File(image.sourceUrl.removePrefix("file:"))
            if (own.exists()) return@withContext EditSource(own, image.sourceUrl, fullPicture = true)
        }
        val variant = VariantChooser.editSource(image.candidate.variants)
        if (variant != null && DeviceInfo.isNetworkAllowed(context, settings.value)) {
            downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName(image.key) + ".edit")
            try {
                if (!file.exists() || file.length() == 0L) http.downloadImage(variant.url, file)
                return@withContext EditSource(file, variant.url, fullPicture = true)
            } catch (e: Exception) {
                file.delete()
                log("Нагласяне: оригиналът не се изтегли (${describe(e)}) – ползвам тапета")
            }
        }
        EditSource(File(image.file), "", fullPicture = false)
    }

    /**
     * Cuts [key]'s picture again with the user's [crop] (fractions of the edit source) and, if it is
     * the current wallpaper, sets it right away.
     */
    suspend fun recrop(key: String, source: EditSource, crop: ImageProcessor.CropFraction): ChangeResult = changeMutex.withLock {
        withContext(Dispatchers.IO) {
            val st = library.value
            val image = (listOfNotNull(st.current) + st.queue + st.history + st.favorites + st.myPhotos).firstOrNull { it.key == key }
                ?: return@withContext ChangeResult.Failed("Снимката вече не е в списъка")
            val (tw, th) = DeviceInfo.screenSize(context)
            imagesDir.mkdirs()
            val dest = File(imagesDir, fileName(key) + "-" + clock() + ".jpg")
            val out = try {
                processor.process(source.file, dest, tw, th, composedForPortrait = false, manualCrop = crop)
            } catch (e: Exception) {
                dest.delete()
                return@withContext ChangeResult.Failed(describe(e))
            }
            val isCurrent = st.current?.key == key
            if (isCurrent) {
                try {
                    setter.apply(dest, settings.value.target)
                } catch (e: Exception) {
                    return@withContext ChangeResult.Failed(describe(e))
                }
            }
            fun edited(old: StoredImage, file: String) = old.copy(
                file = file, width = out.width, height = out.height,
                sourceUrl = source.url.ifBlank { old.sourceUrl },
                cropLeft = if (source.fullPicture) out.crop.left else old.cropLeft,
                cropTop = if (source.fullPicture) out.crop.top else old.cropTop,
                cropRight = if (source.fullPicture) out.crop.right else old.cropRight,
                cropBottom = if (source.fullPicture) out.crop.bottom else old.cropBottom,
                userCrop = true,
            )
            // A favourite and an own photo keep their own copy: update them too.
            st.favorites.firstOrNull { it.key == key }?.let { fav -> dest.copyTo(File(fav.file), overwrite = true) }
            st.myPhotos.firstOrNull { it.key == key }?.let { own -> dest.copyTo(File(own.file), overwrite = true) }
            library.update { s ->
                s.copy(
                    current = s.current?.let { if (it.key == key) edited(it, dest.path) else it },
                    queue = s.queue.map { if (it.key == key) edited(it, dest.path) else it },
                    history = s.history.map { if (it.key == key) edited(it, dest.path) else it },
                    favorites = s.favorites.map { if (it.key == key) edited(it, it.file) else it },
                    myPhotos = s.myPhotos.map { if (it.key == key) edited(it, it.file) else it },
                    log = appendLog(s.log, "Ново изрязване: " + image.candidate.title),
                )
            }
            if (isCurrent) onChanged?.invoke()
            ChangeResult.Changed(edited(image, dest.path))
        }
    }

    suspend fun clearQueue() {
        library.update { it.copy(queue = emptyList()) }
        cleanupFiles()
    }

    // ---------------------------------------------------------------- preparing pictures

    /**
     * Downloads and prepares pictures until the queue holds [minQueue] of them.
     * @return number of pictures added.
     */
    suspend fun refill(
        minQueue: Int = QUEUE_SIZE,
        budgetMs: Long = 8 * 60_000L,
        minFetchGapMs: Long = MIN_FETCH_GAP,
    ): Int = refillMutex.withLock {
        withContext(Dispatchers.IO) {
            if (settings.value.photoMode == PhotoMode.MINE) return@withContext 0
            val started = clock()
            var added = 0
            var attempts = 0
            var fetches = 0
            while (library.value.queue.size < minQueue && clock() - started < budgetMs && attempts < minQueue + 30) {
                if (!DeviceInfo.isNetworkAllowed(context, settings.value)) break
                attempts++
                var candidate = pickCandidate()
                // Browsing far ahead can empty the pool several times: ask the sources again.
                if (candidate == null && fetches < 4) {
                    fetchSources(minFetchGapMs)
                    fetches++
                    candidate = pickCandidate()
                }
                if (candidate == null) break
                try {
                    val stored = prepare(candidate)
                    library.update { st ->
                        st.copy(
                            queue = st.queue + stored,
                            pool = st.pool.filter { it.key != candidate.key },
                            lastSourceUsed = candidate.sourceId,
                        )
                    }
                    added++
                } catch (e: Exception) {
                    library.update { st ->
                        st.copy(
                            pool = st.pool.filter { it.key != candidate.key },
                            seen = (st.seen + candidate.key).takeLast(SEEN_LIMIT),
                            lastSourceUsed = candidate.sourceId,
                            log = appendLog(st.log, "Пропусната снимка (${SourceRegistry.name(candidate.sourceId)}): ${describe(e)}"),
                        )
                    }
                }
            }
            cleanupFiles()
            added
        }
    }

    private suspend fun prepare(candidate: Candidate): StoredImage {
        val (tw, th) = DeviceInfo.screenSize(context)
        val choice = VariantChooser.choose(candidate.variants, tw, th)
            ?: throw IllegalStateException("няма достатъчно голям вариант")
        downloadsDir.mkdirs()
        val download = File(downloadsDir, fileName(candidate.key) + ".download")
        try {
            http.downloadImage(choice.url, download)
            imagesDir.mkdirs()
            val dest = File(imagesDir, fileName(candidate.key) + ".jpg")
            val composed = choice.variant.portrait || choice.variant.sizedTemplate
            val out = processor.process(download, dest, tw, th, composed)
            return StoredImage(
                candidate, dest.path, out.width, out.height, addedAt = clock(),
                sourceUrl = choice.url,
                cropLeft = out.crop.left, cropTop = out.crop.top, cropRight = out.crop.right, cropBottom = out.crop.bottom,
            )
        } finally {
            download.delete()
        }
    }

    private fun eligible(c: Candidate, st: LibraryState, s: Settings): Boolean {
        val source = sources.firstOrNull { it.id == c.sourceId } ?: return false
        return c.sourceId in s.enabledSources && source.isConfigured(s) &&
            c.key !in st.blocked && CategoryClassifier.isAllowed(c, s.enabledCategories)
    }

    /** Takes turns between sources; within a source, newest pictures first. */
    private fun pickCandidate(): Candidate? {
        val st = library.value
        val s = settings.value
        val pool = st.pool.filter { eligible(it, st, s) }
        if (pool.isEmpty()) return null
        val order = sources.map { it.id }
        val start = (order.indexOf(st.lastSourceUsed) + 1).coerceAtLeast(0)
        for (k in order.indices) {
            val id = order[(start + k) % order.size]
            val fromSource = pool.filter { it.sourceId == id }
            if (fromSource.isNotEmpty()) return fromSource.maxBy { it.date }
        }
        return pool.first()
    }

    /** Asks every enabled source that is running low for new pictures, all in parallel. */
    suspend fun fetchSources(minGapMs: Long = MIN_FETCH_GAP) {
        val s = settings.value
        val now = clock()
        val st0 = library.value
        val active = sources.filter { it.id in s.enabledSources && it.isConfigured(s) }
        val due = active.filter { src ->
            val status = st0.sourceStatus[src.id] ?: SourceStatus()
            val inPool = st0.pool.count { it.sourceId == src.id && eligible(it, st0, s) }
            val fresh = now - status.lastFreshFetchAt > FRESH_INTERVAL
            (inPool < 3 || fresh) && now - status.lastAttemptAt > minGapMs
        }
        if (due.isEmpty()) return
        val results = coroutineScope {
            due.map { src ->
                async {
                    val status = st0.sourceStatus[src.id] ?: SourceStatus()
                    val wantFresh = now - status.lastFreshFetchAt > FRESH_INTERVAL
                    src to runCatching {
                        withTimeout(45_000) {
                            src.fetch(FetchRequest(http, s, st0.cursors[src.id], wantFresh, LocalDate.now())) to wantFresh
                        }
                    }
                }
            }.awaitAll()
        }
        library.update { st ->
            var pool = st.pool
            val statuses = st.sourceStatus.toMutableMap()
            val cursors = st.cursors.toMutableMap()
            var log = st.log
            val known = HashSet<String>().apply {
                addAll(st.seen); addAll(st.blocked); addAll(pool.map { it.key })
                addAll(st.queue.map { it.key }); addAll(st.history.map { it.key }); st.current?.let { add(it.key) }
            }
            for ((src, outcome) in results) {
                val old = statuses[src.id] ?: SourceStatus()
                outcome.onSuccess { (result, wasFresh) ->
                    val newOnes = result.candidates.filter { known.add(it.key) }
                    pool = pool + newOnes
                    result.cursor?.let { cursors[src.id] = it }
                    statuses[src.id] = old.copy(
                        lastSuccessAt = now, lastAttemptAt = now, lastCount = result.candidates.size,
                        lastFreshFetchAt = if (wasFresh) now else old.lastFreshFetchAt,
                        lastError = if (result.candidates.isEmpty()) "Източникът не върна снимки" else "",
                        lastErrorAt = if (result.candidates.isEmpty()) now else old.lastErrorAt,
                    )
                }.onFailure { e ->
                    statuses[src.id] = old.copy(lastAttemptAt = now, lastErrorAt = now, lastError = describe(e))
                    log = appendLog(log, "${SourceRegistry.name(src.id)}: грешка – ${describe(e)}")
                }
            }
            st.copy(pool = pool.takeLast(POOL_LIMIT), sourceStatus = statuses, cursors = cursors, log = log)
        }
    }

    private suspend fun pingUnsplash(c: Candidate) {
        if (c.sourceId != "unsplash" || c.downloadPingUrl.isBlank()) return
        val key = settings.value.unsplashKey
        if (key.isBlank() || !DeviceInfo.isNetworkAllowed(context, settings.value)) return
        // Required by the Unsplash API terms whenever a photo is used.
        runCatching { http.ping(c.downloadPingUrl, UnsplashSource.headers(key)) }
    }

    // ---------------------------------------------------------------- housekeeping

    /**
     * Deletes picture files nothing refers to any more. Files younger than a few minutes are kept:
     * another operation (refill, favourite) may have just written one and not recorded it yet.
     */
    private fun cleanupFiles() {
        val st = library.value
        val now = clock()
        fun old(f: File) = now - f.lastModified() > 10 * 60_000L
        val used = (st.queue + st.history + listOfNotNull(st.current)).map { File(it.file).name }.toSet()
        imagesDir.listFiles()?.forEach { if (it.name !in used && old(it)) it.delete() }
        val fav = st.favorites.map { File(it.file).name }.toSet()
        favoritesDir.listFiles()?.forEach { if (it.name !in fav && old(it)) it.delete() }
        downloadsDir.listFiles()?.forEach { if (now - it.lastModified() > 3_600_000L) it.delete() }
    }

    fun cacheBytes(): Long =
        listOf(imagesDir, favoritesDir, mineDir).sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }

    suspend fun log(message: String) {
        library.update { it.copy(log = appendLog(it.log, message)) }
    }

    private fun appendLog(log: List<LogEntry>, message: String) = (log + LogEntry(clock(), message)).takeLast(LOG_LIMIT)

    private fun fileName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(20)
    }

    companion object {
        /** Prepared pictures kept ready: instant "Next", also offline. ~1.5 MB each. */
        const val QUEUE_SIZE = 5
        const val HISTORY_SIZE = 30
        /** Longest side of the kept copy of an own photo (enough for re-cutting, small on disk). */
        private const val MINE_SOURCE_SIDE = 2560
        private const val SEEN_LIMIT = 5000
        private const val POOL_LIMIT = 250
        private const val LOG_LIMIT = 80
        private const val FRESH_INTERVAL = 12 * 3_600_000L
        private const val MIN_FETCH_GAP = 10 * 60_000L
    }
}

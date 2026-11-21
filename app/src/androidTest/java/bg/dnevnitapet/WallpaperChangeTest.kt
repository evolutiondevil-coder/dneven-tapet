package bg.dnevnitapet

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import bg.dnevnitapet.model.WallpaperTarget
import bg.dnevnitapet.wallpaper.ChangeResult
import bg.dnevnitapet.wallpaper.DeviceInfo
import bg.dnevnitapet.wallpaper.Trigger
import bg.dnevnitapet.wallpaper.WallpaperSetter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Runs on an emulator in GitHub Actions. The last test downloads real pictures from the live
 * sources and leaves one as the wallpaper; the workflow then screenshots the home screen.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WallpaperChangeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun solidFile(color: Int): File {
        val (w, h) = DeviceInfo.screenSize(context)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        val f = File(context.cacheDir, "solid-$color.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    @Test
    fun a_setterChangesTheSystemWallpaper() {
        val setter = WallpaperSetter(context)
        val wm = WallpaperManager.getInstance(context)
        val first = setter.apply(solidFile(Color.RED), WallpaperTarget.HOME)
        assertTrue(first > 0)
        assertEquals(first, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        val second = setter.apply(solidFile(Color.BLUE), WallpaperTarget.HOME)
        assertNotEquals(first, second)
        assertEquals(second, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        Log.i(TAG, "wallpaper ids $first -> $second")
    }

    @Test
    fun b_livePipelineDownloadsCropsAndSets() = runBlocking {
        val graph = Graph.get(context)
        graph.settings.update { it.copy(onboarded = true, autoChange = false) }
        val wm = WallpaperManager.getInstance(context)
        val before = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)

        // A background refill (WorkManager) may already be running and fill the queue first;
        // refill() then waits for it, so only the result counts, not who downloaded.
        val added = graph.engine.refill(minQueue = 4)
        val st = graph.library.value
        Log.i(TAG, "refill added $added, queue now ${st.queue.size}")
        for (id in listOf("bing", "spotlight", "wikimedia", "apod")) {
            val s = st.sourceStatus[id]
            Log.i(TAG, "source $id: ok=${s?.lastSuccessAt} count=${s?.lastCount} error='${s?.lastError}'")
        }
        st.log.forEach { Log.i(TAG, "log: ${it.message}") }
        assertTrue("nothing prepared, log=${st.log.map { it.message }}", st.queue.size >= 2)

        // Every keyless source must have answered with pictures.
        for (id in listOf("bing", "spotlight", "wikimedia", "apod")) {
            val s = st.sourceStatus[id]
            assertTrue("$id did not answer: ${s?.lastError}", s != null && s.lastSuccessAt > 0 && s.lastCount > 0)
        }

        // Prepared pictures are exactly screen-sized.
        val (w, h) = DeviceInfo.screenSize(context)
        for (item in st.queue) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(item.file, o)
            Log.i(TAG, "queued ${item.candidate.sourceId} '${item.candidate.title}' ${o.outWidth}x${o.outHeight} (${File(item.file).length() / 1024} KB)")
            assertEquals(w, o.outWidth)
            assertEquals(h, o.outHeight)
        }

        val result = graph.engine.next(Trigger.MANUAL)
        assertTrue("change failed: $result", result is ChangeResult.Changed)
        val after = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        assertNotEquals(before, after)
        Log.i(TAG, "wallpaper now: ${(result as ChangeResult.Changed).image.candidate.title} (id $before -> $after)")

        // "Next" again is instant: it comes from the prepared queue, no download.
        val t0 = System.currentTimeMillis()
        val again = graph.engine.next(Trigger.MANUAL, allowDownload = false)
        val ms = System.currentTimeMillis() - t0
        Log.i(TAG, "second next took $ms ms: $again")
        assertTrue(again is ChangeResult.Changed)
        assertNotEquals(after, wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
    }

    /** A "photo from the gallery": coloured picture with a subject, as a JPEG file. */
    private fun fakePhoto(name: String, w: Int, h: Int, color: Int): android.net.Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(color)
        canvas.drawCircle(w * 0.7f, h * 0.5f, h * 0.2f, android.graphics.Paint().apply { this.color = Color.YELLOW })
        val f = File(context.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return android.net.Uri.fromFile(f)
    }

    @Test
    fun c_ownPhotosRotateInsteadOfInternetPictures(): Unit = runBlocking {
        val graph = Graph.get(context)
        val engine = graph.engine
        val wm = WallpaperManager.getInstance(context)
        val (w, h) = DeviceInfo.screenSize(context)

        // Two photos, one of them smaller than the screen (own photos are used anyway).
        val result = engine.importMine(listOf(fakePhoto("p1.jpg", 3000, 2000, Color.BLUE), fakePhoto("p2.jpg", 800, 600, Color.GREEN)))
        Log.i(TAG, "import: $result")
        assertEquals(2, result.added)
        val album = graph.library.value.myPhotos
        assertEquals(2, album.size)
        for (photo in album) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photo.file, o)
            assertEquals(w, o.outWidth)
            assertEquals(h, o.outHeight)
            assertTrue(photo.sourceUrl.startsWith("file:"))
        }

        // "Моите снимки": both photos come, one after the other, no internet needed.
        graph.settings.update { it.copy(photoMode = bg.dnevnitapet.model.PhotoMode.MINE) }
        val first = engine.next(Trigger.MANUAL, allowDownload = false)
        val id1 = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val second = engine.next(Trigger.MANUAL, allowDownload = false)
        val id2 = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        Log.i(TAG, "own photos: $first / $second")
        assertTrue(first is ChangeResult.Changed && (first as ChangeResult.Changed).image.key.startsWith("mine:"))
        assertTrue(second is ChangeResult.Changed && (second as ChangeResult.Changed).image.key.startsWith("mine:"))
        assertNotEquals((first as ChangeResult.Changed).image.key, (second as ChangeResult.Changed).image.key)
        assertNotEquals(id1, id2)

        // "Смесено": after an own photo comes an internet picture (prepared by the previous test).
        graph.settings.update { it.copy(photoMode = bg.dnevnitapet.model.PhotoMode.MIXED) }
        val mixed = engine.next(Trigger.MANUAL, allowDownload = false)
        Log.i(TAG, "mixed: $mixed")
        if (graph.library.value.queue.isNotEmpty() || (mixed is ChangeResult.Changed && !mixed.image.key.startsWith("mine:"))) {
            assertTrue(mixed is ChangeResult.Changed && !(mixed as ChangeResult.Changed).image.key.startsWith("mine:"))
            val back = engine.next(Trigger.MANUAL, allowDownload = false)
            assertTrue(back is ChangeResult.Changed && (back as ChangeResult.Changed).image.key.startsWith("mine:"))
        }

        // Removing a photo deletes the app's copies only.
        val key = album.first().key
        engine.removeMine(key)
        assertTrue(graph.library.value.myPhotos.none { it.key == key })
        assertTrue(!File(album.first().file).exists())

        graph.settings.update { it.copy(photoMode = bg.dnevnitapet.model.PhotoMode.ONLINE) }
    }

    private fun shell(cmd: String): String {
        val pfd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
    }

    private fun size(path: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun d_panoramaPreparesWidePicturesAndSlides(): Unit = runBlocking {
        val graph = Graph.get(context)
        val engine = graph.engine
        val (w, h) = DeviceInfo.screenSize(context)
        graph.settings.update { it.copy(panorama = true, panoramaScreens = 2f, target = WallpaperTarget.HOME, photoMode = bg.dnevnitapet.model.PhotoMode.ONLINE) }
        engine.refitForPanorama(wasLive = false)
        engine.refill(minQueue = 3)
        val queue = graph.library.value.queue
        assertTrue("nothing prepared for the panorama", queue.isNotEmpty())
        for (item in queue) {
            val (fw, fh) = size(item.file)
            Log.i(TAG, "panorama queued ${item.candidate.sourceId} '${item.candidate.title}' ${fw}x$fh")
            assertEquals(h, fh)
            assertTrue("width $fw outside ${w}..${2 * w}", fw in w..2 * w)
        }
        assertTrue("no wide picture among ${queue.map { size(it.file) }}", queue.any { size(it.file).first > w })

        // Without the live wallpaper: a plain (wide) wallpaper is set.
        val plain = engine.next(Trigger.MANUAL, allowDownload = false)
        assertTrue("panorama change failed: $plain", plain is ChangeResult.Changed)
        assertEquals((plain as ChangeResult.Changed).image.file, bg.dnevnitapet.wallpaper.Panorama.file(context))

        // Switch the live wallpaper on as the user would in the system's preview ("Задай тапет").
        val component = android.content.ComponentName(context, bg.dnevnitapet.wallpaper.PanoramaWallpaperService::class.java)
        val wm = WallpaperManager.getInstance(context)
        val activated = if (android.os.Build.VERSION.SDK_INT >= 29) {
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            try {
                automation.adoptShellPermissionIdentity("android.permission.SET_WALLPAPER_COMPONENT")
                WallpaperManager::class.java.getMethod("setWallpaperComponent", android.content.ComponentName::class.java).invoke(wm, component)
                true
            } catch (e: Throwable) {
                Log.i(TAG, "live wallpaper cannot be set from a test here: $e")
                false
            } finally {
                automation.dropShellPermissionIdentity()
            }
        } else {
            Log.i(TAG, "live wallpaper is not set from tests on Android ${android.os.Build.VERSION.RELEASE}")
            false
        }
        if (!activated) return@runBlocking
        assertTrue("panorama live wallpaper not active", bg.dnevnitapet.wallpaper.Panorama.isActive(context))

        // Next picture goes to the live wallpaper without replacing it.
        val live = engine.next(Trigger.MANUAL, allowDownload = true)
        assertTrue("change with live wallpaper failed: $live", live is ChangeResult.Changed)
        assertTrue(bg.dnevnitapet.wallpaper.Panorama.isActive(context))
        val file = (live as ChangeResult.Changed).image.file
        assertEquals(file, bg.dnevnitapet.wallpaper.Panorama.file(context))
        Log.i(TAG, "live panorama shows ${size(file)}")

        // Home screen, then swipes: the picture moves (with the launcher's position or following the finger).
        val stats = bg.dnevnitapet.wallpaper.PanoramaWallpaperService.Stats
        shell("input keyevent KEYCODE_HOME")
        Thread.sleep(3000)
        shell("screencap -p /sdcard/Download/panorama-1-home.png")
        Log.i(TAG, "panorama on the home screen: $stats")
        assertTrue("the live wallpaper never drew the picture: $stats", stats.draws > 0)
        // Left, left, right: the picture must take more than one position.
        val seen = mutableListOf(stats.offset)
        for ((i, toLeft) in listOf(true, true, false).withIndex()) {
            val (from, to) = if (toLeft) w * 85 / 100 to w * 15 / 100 else w * 15 / 100 to w * 85 / 100
            shell("input swipe $from ${h / 2} $to ${h / 2} 300")
            Thread.sleep(2000)
            shell("screencap -p /sdcard/Download/panorama-${i + 2}-after-swipe.png")
            seen += stats.offset
            Log.i(TAG, "panorama after swipe ${i + 1} (${if (toLeft) "left" else "right"}): $stats")
        }
        assertTrue(
            "the picture did not slide (offsets $seen; $stats)",
            seen.maxOrNull()!! - seen.minOrNull()!! > 0.05f,
        )

        // Effects: tilt, "По-четливи икони" (darker and blurred) and "Вечер по-тъмно".
        graph.settings.update { it.copy(tilt = true, readableIcons = bg.dnevnitapet.model.ReadableMode.DIM_BLUR, eveningDim = true) }
        Thread.sleep(2500)
        shell("input swipe ${w / 2} ${h / 2} ${w / 2 + 5} ${h / 2} 100")
        Thread.sleep(1500)
        shell("screencap -p /sdcard/Download/panorama-5-effects.png")
        Log.i(TAG, "panorama with effects: $stats")
        assertTrue("no darkening with readable icons: $stats", stats.dim >= 0.17f)
        assertTrue("picture not softened: $stats", stats.blurred)
        assertTrue("tilt sensor not listening: $stats", stats.tiltListening)

        // Switching the panorama and the effects off puts a plain wallpaper back.
        graph.settings.update {
            it.copy(panorama = false, tilt = false, readableIcons = bg.dnevnitapet.model.ReadableMode.OFF, eveningDim = false)
        }
        engine.refitForPanorama(wasLive = true)
        assertTrue("live wallpaper still on after switching the panorama off", !bg.dnevnitapet.wallpaper.Panorama.isActive(context))
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            assertTrue("lock screen still shows the live wallpaper", wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK) == null)
        }
        // Leave plain wallpapers behind (the app is uninstalled after the tests).
        wm.clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    @Test
    fun e_lockScreenGetsADifferentPicture(): Unit = runBlocking {
        val graph = Graph.get(context)
        val engine = graph.engine
        val wm = WallpaperManager.getInstance(context)
        graph.settings.update {
            it.copy(target = WallpaperTarget.BOTH, lockDifferent = true, photoMode = bg.dnevnitapet.model.PhotoMode.ONLINE, panorama = false)
        }
        engine.refill(minQueue = 3)
        val r = engine.next(Trigger.MANUAL, allowDownload = false)
        assertTrue("change failed: $r", r is ChangeResult.Changed)
        val st = graph.library.value
        Log.i(TAG, "home ${st.current?.key} / lock ${st.lockCurrent?.key}")
        assertTrue("no separate lock picture", st.lockCurrent != null)
        assertNotEquals(st.current?.key, st.lockCurrent?.key)
        val home = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lock = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        Log.i(TAG, "wallpaper ids home=$home lock=$lock")
        assertTrue("lock screen has no own wallpaper", lock > 0 && lock != home)
        graph.settings.update { it.copy(target = WallpaperTarget.HOME, lockDifferent = false) }
    }

    @Test
    fun f_shareSendsThePictureWithItsName(): Unit = runBlocking {
        val graph = Graph.get(context)
        val image = graph.library.value.current ?: graph.library.value.history.first()
        val chooser = bg.dnevnitapet.ui.Share.intent(context, image)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        Log.i(TAG, "share: $uri ${bytes.size} bytes, text='${send.getStringExtra(android.content.Intent.EXTRA_TEXT)}'")
        assertTrue(bytes.size > 1000)
        assertTrue(send.getStringExtra(android.content.Intent.EXTRA_TEXT)!!.contains(image.candidate.title))
    }

    @Test
    fun g_updateDownloadsTheLatestReleaseFromGitHub(): Unit = runBlocking {
        val http = bg.dnevnitapet.net.OkHttpHttp()
        val text = try {
            http.getText(bg.dnevnitapet.update.Updater.LATEST_URL, mapOf("Accept" to "application/vnd.github+json"))
        } catch (e: bg.dnevnitapet.net.HttpStatusException) {
            // GitHub limits unauthenticated requests per address; shared CI machines can hit it.
            org.junit.Assume.assumeTrue("GitHub answered ${e.code}", e.code != 403 && e.code != 429)
            throw e
        }
        val release = bg.dnevnitapet.update.ReleaseParser.parse(text)
        Log.i(TAG, "latest release: $release")
        assertTrue("no installable release on GitHub", release != null)
        assertTrue(bg.dnevnitapet.update.Versions.isNewer(release!!.tag, "1.2.0"))
        // A test build never asks to be replaced.
        assertTrue(!bg.dnevnitapet.update.Versions.isNewer(release.tag, BuildConfig.VERSION_NAME) || BuildConfig.VERSION_NAME.matches(Regex("\\d+\\.\\d+\\.\\d+")))
        val updater = bg.dnevnitapet.update.Updater(context, http)
        val file = updater.download(release)
        Log.i(TAG, "downloaded ${file.length()} bytes")
        assertTrue("downloaded file is not this app", updater.isThisApp(file))
        file.delete()
    }

    @Test
    fun h_appOpensWhenAnOwnPhotoIsInHistoryAndComesAgain(): Unit = runBlocking {
        val graph = Graph.get(context)
        val engine = graph.engine
        graph.settings.update { it.copy(onboarded = true, photoMode = bg.dnevnitapet.model.PhotoMode.MINE, target = WallpaperTarget.HOME) }
        engine.importMine(listOf(fakePhoto("h1.jpg", 2000, 1500, Color.RED), fakePhoto("h2.jpg", 2000, 1500, Color.CYAN)))
        // Own photos take turns: the first one is now in the history and also among the upcoming ones.
        repeat(3) { engine.next(Trigger.MANUAL, allowDownload = false) }
        val st = graph.library.value
        Log.i(TAG, "history ${st.history.map { it.key }} current ${st.current?.key} upcoming ${engine.upcomingMine(st).map { it.key }}")
        androidx.test.core.app.ActivityScenario.launch(bg.dnevnitapet.ui.MainActivity::class.java).use { scenario ->
            Thread.sleep(4000)
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, scenario.state)
        }
        graph.settings.update { it.copy(photoMode = bg.dnevnitapet.model.PhotoMode.ONLINE) }
    }

    @Test
    fun i_internetPicturesCanBeChosenWhileOnlyOwnPhotosAreInUse(): Unit = runBlocking {
        val graph = Graph.get(context)
        val engine = graph.engine
        graph.settings.update { it.copy(onboarded = true, photoMode = bg.dnevnitapet.model.PhotoMode.MINE, target = WallpaperTarget.HOME) }
        engine.importMine(listOf(fakePhoto("i1.jpg", 2000, 1500, Color.GREEN), fakePhoto("i2.jpg", 2000, 1500, Color.MAGENTA)))
        engine.next(Trigger.MANUAL, allowDownload = false)
        graph.library.update { it.copy(queue = emptyList()) }
        // In the background nothing is downloaded with only own photos …
        assertEquals(0, engine.refill(minQueue = 1, budgetMs = 60_000L))
        // … but browsing "От интернет" on the main screen downloads internet pictures.
        val added = engine.refill(minQueue = 2, budgetMs = 120_000L, minFetchGapMs = 0L, forBrowsing = true)
        Log.i(TAG, "browsing internet with own photos: $added added")
        assertTrue("no internet picture prepared", added > 0)
        val online = graph.library.value.queue.first()
        val chosen = engine.chooseFromQueue(online.key)
        assertTrue("choosing an internet picture failed: $chosen", chosen is ChangeResult.Changed)
        assertEquals(online.key, graph.library.value.current?.key)
        // The automatic change goes on with the own photos.
        engine.next(Trigger.SCHEDULED, allowDownload = false)
        assertTrue("next change is not an own photo", bg.dnevnitapet.wallpaper.MyPhotos.isMine(graph.library.value.current?.key))
        graph.settings.update { it.copy(photoMode = bg.dnevnitapet.model.PhotoMode.ONLINE) }
    }

    companion object {
        const val TAG = "DnevenTapetTest"
    }
}

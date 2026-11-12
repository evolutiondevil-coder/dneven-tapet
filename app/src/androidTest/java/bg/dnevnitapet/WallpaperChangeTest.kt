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
    fun c_ownPhotosRotateInsteadOfInternetPictures() = runBlocking {
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

    companion object {
        const val TAG = "DnevenTapetTest"
    }
}

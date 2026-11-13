package bg.dnevnitapet.wallpaper

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import bg.dnevnitapet.Graph
import bg.dnevnitapet.image.WallpaperSize
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * "Панорама": the app's live wallpaper. Draws the current picture (wider than the screen) and
 * slides it while the home screens are swiped, like the scrolling wallpapers of old.
 *
 * Launchers tell wallpapers where they are ("offsets"). Some launchers do not, or always report
 * the middle; then the picture follows horizontal swipes on the home screen instead.
 */
class PanoramaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PanoramaEngine()

    private inner class PanoramaEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private val loader = Executors.newSingleThreadExecutor()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()

        private var bitmap: Bitmap? = null
        private var loadedPath: String? = null
        private var loadedForHeight = 0
        private var surfaceW = 0
        private var surfaceH = 0
        private var visible = false

        /** 0 = left edge of the picture, 1 = right edge. */
        private var offset = 0.5f

        /** The launcher reports real positions: follow them, ignore touches. */
        private var launcherScrolls = false
        private var firstLauncherOffset = Float.NaN

        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L

        private var animFrom = 0f
        private var animTo = 0f
        private var animStart = 0L
        private val animate = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - animStart).toFloat() / ANIM_MS).coerceIn(0f, 1f)
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                offset = animFrom + (animTo - animFrom) * eased
                draw()
                if (t < 1f) handler.postDelayed(this, FRAME_MS)
            }
        }

        /** In the system's preview the picture pans slowly, so one sees what the panorama does. */
        private var previewStart = 0L
        private val previewPan = object : Runnable {
            override fun run() {
                if (!visible || !isPreview) return
                val t = (SystemClock.uptimeMillis() - previewStart) / PREVIEW_PERIOD_MS.toDouble()
                offset = (0.5 + 0.5 * sin(2 * PI * t)).toFloat().coerceIn(0f, 1f)
                draw()
                handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            Panorama.prefs(this@PanoramaWallpaperService).registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            Panorama.prefs(this@PanoramaWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
            handler.removeCallbacksAndMessages(null)
            loader.shutdownNow()
            bitmap = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width
            surfaceH = height
            load()
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(previewPan)
            if (visible) {
                load()
                draw()
                if (isPreview) {
                    previewStart = SystemClock.uptimeMillis()
                    handler.postDelayed(previewPan, 600)
                }
            }
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            if (isPreview) return
            Stats.offsetEvents++
            Stats.lastLauncherOffset = xOffset
            if (Stats.offsetEvents <= 3) Log.i(TAG, "offsets x=$xOffset step=$xOffsetStep")
            if (!launcherScrolls) {
                // Only a launcher that reports different positions really slides; one that always
                // says "middle" (or nothing) is handled by following the finger. A launcher that
                // says it has several screens is believed for where it starts (e.g. the first one).
                if (firstLauncherOffset.isNaN()) {
                    firstLauncherOffset = xOffset
                    if (xOffsetStep > 0f && xOffsetStep < 1f) {
                        offset = xOffset.coerceIn(0f, 1f)
                        draw()
                    }
                    return
                }
                if (abs(xOffset - firstLauncherOffset) < 0.01f) return
                launcherScrolls = true
                Stats.launcherScrolls = true
                Log.i(TAG, "launcher reports positions (step $xOffsetStep)")
            }
            handler.removeCallbacks(animate)
            offset = xOffset.coerceIn(0f, 1f)
            draw()
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!isPreview) Stats.touchEvents++
            if (launcherScrolls || isPreview || surfaceW <= 0) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = event.eventTime
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val quick = event.eventTime - downAt < 1200
                    if (quick && abs(dx) > surfaceW * 0.15f && abs(dx) > 2 * abs(dy)) {
                        // Finger to the left = the next screen to the right.
                        val step = 1f / (FALLBACK_SCREENS - 1)
                        slideTo((offset + if (dx < 0) step else -step).coerceIn(0f, 1f))
                    }
                }
            }
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (key == null || key == Panorama.KEY_FILE) handler.post { load(); draw() }
        }

        private fun slideTo(target: Float) {
            if (abs(target - offset) < 0.001f) return
            handler.removeCallbacks(animate)
            animFrom = offset
            animTo = target
            animStart = SystemClock.uptimeMillis()
            handler.post(animate)
        }

        /** Loads the current picture in the background (big pictures are reduced to the screen's height). */
        private fun load() {
            val ctx = this@PanoramaWallpaperService
            val path = Panorama.file(ctx)?.takeIf { File(it).exists() }
                ?: runCatching { Graph.get(ctx).library.value.current?.file }.getOrNull()?.takeIf { File(it).exists() }
                ?: return
            val height = if (surfaceH > 0) surfaceH else DeviceInfo.screenSize(ctx).second
            if (path == loadedPath && bitmap != null && loadedForHeight == height) return
            loadedPath = path
            loadedForHeight = height
            loader.execute {
                val decoded = runCatching { decode(path, height) }.onFailure { Log.w(TAG, "cannot load $path", it) }.getOrNull()
                handler.post {
                    if (decoded != null && loadedPath == path) {
                        bitmap = decoded
                        draw()
                    } else if (decoded == null && loadedPath == path) {
                        loadedPath = null
                    }
                }
            }
        }

        private fun decode(path: String, height: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= height) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            if (surfaceW <= 0 || surfaceH <= 0) return
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas == null) return
                canvas.drawColor(Color.BLACK)
                val bmp = bitmap ?: return
                val p = WallpaperSize.placement(bmp.width, bmp.height, surfaceW, surfaceH, offset)
                matrix.setScale(p.scale, p.scale)
                matrix.postTranslate(p.left, p.top)
                canvas.drawBitmap(bmp, matrix, paint)
                if (!isPreview) {
                    Stats.draws++
                    Stats.offset = offset
                }
            } catch (e: Exception) {
                Log.w(TAG, "draw failed", e)
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }

    /** What the panorama did (read by the emulator test; the app itself does not use it). */
    object Stats {
        @Volatile var draws = 0
        @Volatile var offset = 0.5f
        @Volatile var offsetEvents = 0
        @Volatile var lastLauncherOffset = Float.NaN
        @Volatile var touchEvents = 0
        @Volatile var launcherScrolls = false

        override fun toString() =
            "draws=$draws offset=$offset offsetEvents=$offsetEvents launcherOffset=$lastLauncherOffset touches=$touchEvents launcherScrolls=$launcherScrolls"
    }

    companion object {
        private const val TAG = "DnevenTapet"
        private const val ANIM_MS = 350f
        private const val FRAME_MS = 16L
        private const val PREVIEW_PERIOD_MS = 7000L
        /** Without positions from the launcher: a swipe moves by one of this many screens. */
        private const val FALLBACK_SCREENS = 3
    }
}

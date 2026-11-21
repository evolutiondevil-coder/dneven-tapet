package bg.dnevnitapet.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import bg.dnevnitapet.Graph
import bg.dnevnitapet.image.Effects
import bg.dnevnitapet.image.WallpaperSize
import bg.dnevnitapet.model.ReadableMode
import bg.dnevnitapet.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar
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

    /** Time of the last quick change (unlock or double tap), to keep them apart. */
    @Volatile private var lastQuickAt = 0L

    /**
     * "Нова снимка при всяко отключване": when the screen goes off another picture is put, so the
     * phone wakes up with it. Registered while the live wallpaper runs (it only runs as the wallpaper).
     */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val graph = runCatching { Graph.get(this@PanoramaWallpaperService) }.getOrNull() ?: return
            val s = graph.settings.value
            if (!s.changeOnUnlock || !s.liveOn) return
            quickChange(graph, Trigger.UNLOCK, MIN_UNLOCK_GAP_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    /** Changes the picture right away from what is already on the phone (nothing is downloaded now). */
    private fun quickChange(graph: bg.dnevnitapet.AppGraph, trigger: Trigger, minGapMs: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastQuickAt < minGapMs) return
        lastQuickAt = now
        graph.appScope.launch {
            val r = runCatching { graph.engine.quickChange(trigger) }.getOrNull()
            bg.dnevnitapet.util.Diag.i("quick change ($trigger): ${(r as? ChangeResult.Changed)?.image?.key ?: r}")
            if (r is ChangeResult.Changed) {
                if (trigger == Trigger.UNLOCK) Stats.unlockChanges++ else Stats.doubleTaps++
            }
            // A double tap used a prepared picture: get the next one ready (Wi-Fi rules apply).
            if (trigger == Trigger.DOUBLE_TAP) runCatching { graph.scheduler.enqueueRefill() }
        }
    }

    private inner class PanoramaEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, SensorEventListener {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var settings = Settings()

        /** Smaller, softened copy of the picture for "Затъмни и размажи". */
        private var blurred: Bitmap? = null
        private val dimPaint = Paint()

        // Tilt: the phone's tilt relative to how it is usually held (a slowly following baseline).
        private val sensors = getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        private val gravity: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private var listening = false
        private var baseSet = false
        private var baseX = 0f
        private var baseY = 0f
        private var tiltX = 0f
        private var tiltY = 0f

        /** Evening dimming follows the clock while the home screen is visible. */
        private val clockTick = object : Runnable {
            override fun run() {
                if (!visible || !settings.eveningDim) return
                draw()
                handler.postDelayed(this, 5 * 60_000L)
            }
        }
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

        /** Double tap on an empty spot: the launcher's "tap" commands, or the touches themselves. */
        private val commandTaps = DoubleTap()
        private val touchTaps = DoubleTap()

        /** The previous picture fades out over the new one. */
        private var fadingFrom: Bitmap? = null
        private var fadeStart = 0L
        private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val fade = object : Runnable {
            override fun run() {
                draw()
                if (fadingFrom != null) handler.postDelayed(this, FRAME_MS)
            }
        }

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
            val store = runCatching { Graph.get(this@PanoramaWallpaperService).settings }.getOrNull()
            if (store != null) {
                settings = store.value
                scope.launch {
                    store.state.collect { s ->
                        val blurChanged = (s.readableIcons == ReadableMode.DIM_BLUR) != (settings.readableIcons == ReadableMode.DIM_BLUR)
                        settings = s
                        if (blurChanged) makeBlurred()
                        updateSensor()
                        updateClock()
                        draw()
                    }
                }
            }
        }

        override fun onDestroy() {
            Panorama.prefs(this@PanoramaWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
            scope.cancel()
            if (listening) sensors?.unregisterListener(this)
            listening = false
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
            updateSensor()
            updateClock()
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
            if (!isPreview && settings.doubleTapNext && event.actionMasked == MotionEvent.ACTION_UP) {
                val still = abs(event.x - downX) < 24f && abs(event.y - downY) < 24f && event.eventTime - downAt < 300
                if (still && touchTaps.tap(event.eventTime, event.x, event.y)) onDoubleTap()
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downX = event.x
                downY = event.y
                downAt = event.eventTime
            }
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

        /** Launchers send "tap" for a touch on an empty spot of the home screen. */
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: android.os.Bundle?, resultRequested: Boolean): android.os.Bundle? {
            if (!isPreview && action == WallpaperManager.COMMAND_TAP && settings.doubleTapNext) {
                if (commandTaps.tap(SystemClock.uptimeMillis(), x.toFloat(), y.toFloat())) onDoubleTap()
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun onDoubleTap() {
            val graph = runCatching { Graph.get(this@PanoramaWallpaperService) }.getOrNull() ?: return
            quickChange(graph, Trigger.DOUBLE_TAP, MIN_DOUBLE_TAP_GAP_MS)
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (key == null || key == Panorama.KEY_FILE) handler.post { load(); draw() }
        }

        // ------------------------------------------------------------ tilt

        private fun updateSensor() {
            val want = visible && settings.tilt && gravity != null
            if (want && !listening) {
                baseSet = false
                listening = sensors?.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME) == true
                Stats.tiltListening = listening
            } else if (!want && listening) {
                sensors?.unregisterListener(this)
                listening = false
                tiltX = 0f
                tiltY = 0f
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            val gx = event.values[0]
            val gy = event.values[1]
            if (!baseSet) {
                baseX = gx
                baseY = gy
                baseSet = true
            }
            // The baseline follows slowly, so only movements count, not how the phone is held.
            baseX += (gx - baseX) * 0.015f
            baseY += (gy - baseY) * 0.015f
            val tx = ((gx - baseX) / TILT_RANGE).coerceIn(-1f, 1f)
            val ty = (-(gy - baseY) / TILT_RANGE).coerceIn(-1f, 1f)
            val nx = tiltX + (tx - tiltX) * 0.25f
            val ny = tiltY + (ty - tiltY) * 0.25f
            if (abs(nx - tiltX) > 0.002f || abs(ny - tiltY) > 0.002f) {
                tiltX = nx
                tiltY = ny
                Stats.tiltEvents++
                draw()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun updateClock() {
            handler.removeCallbacks(clockTick)
            if (visible && settings.eveningDim) handler.postDelayed(clockTick, 5 * 60_000L)
        }

        /** The darkening drawn over the picture now (readable icons and/or evening). */
        private fun dimNow(): Float {
            val readable = Effects.readableDim(settings.readableIcons)
            if (!settings.eveningDim) return readable
            val now = Calendar.getInstance()
            val evening = Effects.eveningDim(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
            return Effects.combine(readable, evening)
        }

        /** Softened copy: reduced a lot, enlarged again when drawn (cheap and smooth). */
        private fun makeBlurred() {
            val src = bitmap
            if (src == null || settings.readableIcons != ReadableMode.DIM_BLUR) {
                blurred = null
                return
            }
            loader.execute {
                val soft = runCatching {
                    val tiny = Bitmap.createScaledBitmap(src, (src.width / 14).coerceAtLeast(1), (src.height / 14).coerceAtLeast(1), true)
                    val mid = Bitmap.createScaledBitmap(tiny, (src.width / 4).coerceAtLeast(1), (src.height / 4).coerceAtLeast(1), true)
                    if (mid !== tiny) tiny.recycle()
                    mid
                }.getOrNull()
                handler.post {
                    if (bitmap === src) {
                        blurred = soft
                        draw()
                    }
                }
            }
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
                        // A new picture while the home screen is visible fades in over the old one.
                        val old = bitmap
                        if (old != null && visible && !isPreview) {
                            fadingFrom = old
                            fadeStart = SystemClock.uptimeMillis()
                            handler.removeCallbacks(fade)
                            handler.post(fade)
                        }
                        bitmap = decoded
                        blurred = null
                        makeBlurred()
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
                val zoom = if (settings.tilt) Effects.TILT_ZOOM else 1f
                val old = fadingFrom
                val fadeT = if (old == null) 1f else ((SystemClock.uptimeMillis() - fadeStart).toFloat() / FADE_MS).coerceIn(0f, 1f)
                if (old != null && fadeT < 1f) {
                    val po = WallpaperSize.placement(old.width, old.height, surfaceW, surfaceH, offset, zoom, tiltX, tiltY)
                    matrix.setScale(po.scale, po.scale)
                    matrix.postTranslate(po.left, po.top)
                    canvas.drawBitmap(old, matrix, paint)
                } else if (old != null) {
                    fadingFrom = null
                }
                val p = WallpaperSize.placement(bmp.width, bmp.height, surfaceW, surfaceH, offset, zoom, tiltX, tiltY)
                val soft = blurred?.takeIf { settings.readableIcons == ReadableMode.DIM_BLUR }
                val drawn = soft ?: bmp
                // The softened copy is smaller: scale it up to the picture's size.
                matrix.setScale(p.scale * bmp.width / drawn.width, p.scale * bmp.height / drawn.height)
                matrix.postTranslate(p.left, p.top)
                fadePaint.alpha = (fadeT * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(drawn, matrix, if (fadeT < 1f) fadePaint else paint)
                val dim = dimNow()
                if (dim > 0f) {
                    dimPaint.color = Color.argb((dim * 255).toInt().coerceIn(0, 255), 0, 0, 0)
                    canvas.drawRect(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat(), dimPaint)
                }
                if (!isPreview) {
                    Stats.draws++
                    Stats.offset = offset
                    Stats.dim = dim
                    Stats.blurred = soft != null
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
        @Volatile var dim = 0f
        @Volatile var blurred = false
        @Volatile var tiltListening = false
        @Volatile var tiltEvents = 0
        @Volatile var unlockChanges = 0
        @Volatile var doubleTaps = 0

        override fun toString() =
            "draws=$draws offset=$offset offsetEvents=$offsetEvents launcherOffset=$lastLauncherOffset touches=$touchEvents " +
                "launcherScrolls=$launcherScrolls dim=$dim blurred=$blurred tilt=$tiltListening/$tiltEvents " +
                "unlockChanges=$unlockChanges doubleTaps=$doubleTaps"
    }

    companion object {
        private const val TAG = "DnevenTapet"
        private const val ANIM_MS = 350f
        private const val FRAME_MS = 16L
        private const val PREVIEW_PERIOD_MS = 7000L
        /** Without positions from the launcher: a swipe moves by one of this many screens. */
        private const val FALLBACK_SCREENS = 3
        /** Tilt (m/s² of gravity) that moves the picture to the edge of its margin. */
        private const val TILT_RANGE = 2.5f
        /** How long the old picture takes to fade into the new one. */
        private const val FADE_MS = 450f
        /** Screen off again within this time: no new picture (e.g. a quick look at the time). */
        private const val MIN_UNLOCK_GAP_MS = 8_000L
        private const val MIN_DOUBLE_TAP_GAP_MS = 1_200L
    }
}

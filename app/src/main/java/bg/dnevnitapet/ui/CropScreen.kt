package bg.dnevnitapet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.image.ImageProcessor
import bg.dnevnitapet.image.VariantChooser
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Нагласи": the whole picture with a frame the shape of the phone screen. Drag to move the picture,
 * pinch to zoom; the part inside the frame becomes the wallpaper.
 */
@Composable
fun CropScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.crop.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val close = {
        vm.closeCrop()
        onClose()
    }
    BackHandler(onBack = close)
    // Opened without a picture (e.g. the app was restarted by the system): go back.
    if (state is MainViewModel.CropState.Idle) LaunchedEffect(Unit) { onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is MainViewModel.CropState.Ready -> CropEditor(s, busy, onCancel = close, onApply = { vm.applyCrop(it, onClose) })
            is MainViewModel.CropState.Error -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = close) { Text("Назад", color = Color.White) }
            }
            else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Изтеглям снимката в пълен размер…", color = Color.White)
            }
        }
    }
}

@Composable
private fun CropEditor(
    s: MainViewModel.CropState.Ready,
    busy: Boolean,
    onCancel: () -> Unit,
    onApply: (ImageProcessor.CropFraction) -> Unit,
) {
    val density = LocalDensity.current
    val image = remember(s.preview) { s.preview.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        // Room for the title and hint above the frame and the buttons below it, plus the system bars.
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val topSpace = statusTop + with(density) { 120.dp.toPx() }
        val bottomSpace = navBottom + with(density) { 150.dp.toPx() }

        // The frame has the phone screen's shape and fills the free space.
        val aspect = s.screenWidth.toFloat() / s.screenHeight
        var frameH = (boxH - topSpace - bottomSpace).coerceAtLeast(100f)
        var frameW = frameH * aspect
        if (frameW > boxW * 0.9f) {
            frameW = boxW * 0.9f
            frameH = frameW / aspect
        }
        val frameLeft = (boxW - frameW) / 2
        val frameTop = topSpace + (boxH - topSpace - bottomSpace - frameH) / 2

        val iw = image.width.toFloat()
        val ih = image.height.toFloat()
        val minScale = max(frameW / iw, frameH / ih)
        // Do not allow zooming so far that the wallpaper would look blurry.
        val sourcePerPreviewPx = s.sourceHeight / ih
        val maxScale = max(minScale, frameH * sourcePerPreviewPx * VariantChooser.MAX_UPSCALE.toFloat() / s.screenHeight)

        fun clamp(scale: Float, o: Offset) = Offset(
            o.x.coerceIn(frameLeft + frameW - iw * scale, frameLeft),
            o.y.coerceIn(frameTop + frameH - ih * scale, frameTop),
        )

        fun initial(): Pair<Float, Offset> {
            val c = s.initial
            return if (c != null) {
                val sc = (frameH / ((c.bottom - c.top) * ih)).coerceIn(minScale, maxScale)
                sc to clamp(sc, Offset(frameLeft - c.left * iw * sc, frameTop - c.top * ih * sc))
            } else {
                minScale to clamp(minScale, Offset(frameLeft + (frameW - iw * minScale) / 2, frameTop + (frameH - ih * minScale) / 2))
            }
        }

        val start = remember(s, boxW, boxH) { initial() }
        var scale by remember(start) { mutableFloatStateOf(start.first) }
        var offset by remember(start) { mutableStateOf(start.second) }

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(start) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        val f = newScale / scale
                        val moved = Offset(
                            centroid.x - (centroid.x - offset.x) * f + pan.x,
                            centroid.y - (centroid.y - offset.y) * f + pan.y,
                        )
                        scale = newScale
                        offset = clamp(newScale, moved)
                    }
                }
                .pointerInput(start) {
                    // Double tap: back to the whole picture.
                    detectTapGestures(onDoubleTap = {
                        scale = minScale
                        offset = clamp(minScale, Offset(frameLeft + (frameW - iw * minScale) / 2, frameTop + (frameH - ih * minScale) / 2))
                    })
                },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                dstSize = IntSize((iw * scale).roundToInt(), (ih * scale).roundToInt()),
                filterQuality = FilterQuality.High,
            )
            // Darken everything outside the frame.
            val dim = Color.Black.copy(alpha = 0.62f)
            drawRect(dim, Offset.Zero, Size(size.width, frameTop))
            drawRect(dim, Offset(0f, frameTop + frameH), Size(size.width, size.height - frameTop - frameH))
            drawRect(dim, Offset(0f, frameTop), Size(frameLeft, frameH))
            drawRect(dim, Offset(frameLeft + frameW, frameTop), Size(size.width - frameLeft - frameW, frameH))
            // Frame and rule-of-thirds lines.
            drawRect(Color.White, Offset(frameLeft, frameTop), Size(frameW, frameH), style = Stroke(width = 2.dp.toPx()))
            val thin = Color.White.copy(alpha = 0.35f)
            for (k in 1..2) {
                val x = frameLeft + frameW * k / 3
                val y = frameTop + frameH * k / 3
                drawLine(thin, Offset(x, frameTop), Offset(x, frameTop + frameH), strokeWidth = 1.dp.toPx())
                drawLine(thin, Offset(frameLeft, y), Offset(frameLeft + frameW, y), strokeWidth = 1.dp.toPx())
            }
        }

        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Нагласи изрязването", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                "Плъзни, за да преместиш. С два пръста – приближи. Двойно докосване – цялата снимка.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Отказ", maxLines = 1) }
            Button(
                onClick = {
                    val w = iw * scale
                    val h = ih * scale
                    onApply(
                        ImageProcessor.CropFraction(
                            ((frameLeft - offset.x) / w).coerceIn(0f, 1f),
                            ((frameTop - offset.y) / h).coerceIn(0f, 1f),
                            ((frameLeft + frameW - offset.x) / w).coerceIn(0f, 1f),
                            ((frameTop + frameH - offset.y) / h).coerceIn(0f, 1f),
                        ),
                    )
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black) else Text("Готово", maxLines = 1)
            }
        }
        TextButton(
            onClick = {
                val (sc, o) = initial()
                scale = sc
                offset = o
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
        ) { Text("Върни както беше", color = Color.White.copy(alpha = 0.85f)) }
    }
}

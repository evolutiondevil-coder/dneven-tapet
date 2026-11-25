package bg.dnevnitapet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Screen { HOME, HISTORY, FAVORITES, SETTINGS, CROP, MY_PHOTOS, ARCHIVE }

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                // The crop editor returns to the screen it was opened from.
                var cropFrom by rememberSaveable { mutableStateOf(Screen.HOME) }
                // Android's "predictive back": while the back gesture is held, the screen shrinks
                // towards the edge, showing that letting go goes back.
                var backProgress by remember { mutableFloatStateOf(0f) }
                var backFromLeft by remember { mutableStateOf(true) }
                PredictiveBackHandler(enabled = screen != Screen.HOME) { events ->
                    try {
                        events.collect { e ->
                            backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                            backProgress = e.progress
                        }
                        screen = if (screen == Screen.CROP) cropFrom else Screen.HOME
                    } finally {
                        backProgress = 0f
                    }
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        // Deeper screens come in from the right; going back they leave to the right.
                        val back = targetState == Screen.HOME || initialState == Screen.CROP
                        val enter = slideInHorizontally(tween(340, easing = EaseOutCubic)) { w -> if (back) -w / 6 else w / 4 } +
                            fadeIn(tween(240, delayMillis = 40))
                        val exit = slideOutHorizontally(tween(340, easing = EaseOutCubic)) { w -> if (back) w / 4 else -w / 6 } +
                            fadeOut(tween(200))
                        (enter togetherWith exit).apply { targetContentZIndex = if (back) -1f else 1f }
                    },
                    label = "screens",
                ) { shown ->
                    val p = if (shown == screen) backProgress else 0f
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            if (p > 0f) {
                                val scale = 1f - 0.08f * p
                                scaleX = scale
                                scaleY = scale
                                translationX = (if (backFromLeft) 1f else -1f) * 24.dp.toPx() * p
                                shape = RoundedCornerShape(28.dp * p)
                                clip = true
                            }
                        },
                    ) {
                        when (shown) {
                            Screen.HOME -> HomeScreen(vm, onNavigate = {
                                if (it == Screen.CROP) cropFrom = Screen.HOME
                                screen = it
                            })
                            Screen.MY_PHOTOS -> MyPhotosScreen(
                                vm,
                                onBack = { screen = Screen.HOME },
                                onOpenCrop = {
                                    cropFrom = Screen.MY_PHOTOS
                                    screen = Screen.CROP
                                },
                            )
                            Screen.HISTORY -> ImageGridScreen(vm, favorites = false, onBack = { screen = Screen.HOME }, onOpenCrop = {
                                cropFrom = Screen.HISTORY
                                screen = Screen.CROP
                            })
                            Screen.FAVORITES -> ImageGridScreen(vm, favorites = true, onBack = { screen = Screen.HOME }, onOpenCrop = {
                                cropFrom = Screen.FAVORITES
                                screen = Screen.CROP
                            })
                            Screen.SETTINGS -> SettingsScreen(vm, onBack = { screen = Screen.HOME }, onOpenMyPhotos = { screen = Screen.MY_PHOTOS })
                            Screen.CROP -> CropScreen(vm, onClose = { screen = cropFrom })
                            Screen.ARCHIVE -> ArchiveScreen(vm, onBack = { screen = Screen.HOME })
                        }
                    }
                }
                // A newer version on GitHub: asked at every start until it is installed.
                val release by vm.updateAvailable.collectAsStateWithLifecycle()
                val dismissed by vm.updatePromptDismissed.collectAsStateWithLifecycle()
                val updateState by vm.updateState.collectAsStateWithLifecycle()
                release?.let { r ->
                    if (!dismissed && screen != Screen.CROP) {
                        UpdateDialog(
                            r,
                            downloading = updateState == MainViewModel.UpdateState.Downloading,
                            onUpdate = vm::installUpdate,
                            onLater = vm::dismissUpdatePrompt,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onPause() {
        vm.onPause()
        super.onPause()
    }
}

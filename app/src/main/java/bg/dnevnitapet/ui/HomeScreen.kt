package bg.dnevnitapet.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.model.PhotoMode
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.util.Power
import bg.dnevnitapet.wallpaper.MyPhotos
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

/**
 * Main screen: the pictures as a swipeable gallery (history ← current wallpaper → upcoming),
 * with the controls on top. Swiping only previews; "Избери" sets the picture as wallpaper.
 * A tap on the picture hides everything (full screen); another tap brings it all back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onNavigate: (Screen) -> Unit) {
    val st by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pickPhotos = rememberPhotoPicker(vm)
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val previewKey by vm.previewKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showInfo by remember { mutableStateOf(false) }
    var immersive by rememberSaveable { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val unrestricted = remember(resumeTick) { Power.isUnrestricted(context) }
    val saveToGallery = rememberGallerySave(vm)

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, actionLabel = m.actionLabel, withDismissAction = m.actionLabel == null)
            if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val current = st.current
    val list = remember(st, settings.photoMode) { vm.browseList(st, settings) }
    val latestList by rememberUpdatedState(list)
    val onboarded by rememberUpdatedState(settings.onboarded)
    val desiredKey = previewKey ?: current?.key
    // One extra page after the last picture: reaching it downloads more.
    val pagerState = rememberPagerState(
        initialPage = list.indexOfFirst { it.key == desiredKey }.coerceAtLeast(0),
        pageCount = { latestList.size + 1 },
    )

    // The gallery decides what is on screen. The app only moves it on explicit requests
    // ("към сегашния", after "Избери", after setup, back after a while) – never in reaction to the
    // gallery's own movement, which would make the two fight each other.
    suspend fun scrollToKey(key: String) {
        val target = withTimeoutOrNull(3_000) {
            snapshotFlow { latestList.indexOfFirst { it.key == key } }.first { it >= 0 }
        } ?: return
        bg.dnevnitapet.util.Diag.i("scroll to $key: page ${pagerState.currentPage} -> $target")
        if (target == pagerState.currentPage) return
        if (abs(target - pagerState.currentPage) <= 3) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
    }
    LaunchedEffect(Unit) {
        vm.scrollRequests.collect { key -> scrollToKey(key) }
    }
    // The wallpaper changed (widget, tile, automatic change) while nothing is being browsed.
    LaunchedEffect(current?.key) {
        val key = current?.key ?: return@LaunchedEffect
        if (vm.previewKey.value == null) scrollToKey(key)
    }
    // Pager → ViewModel: the picture the user swiped to.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val item = latestList.getOrNull(page)
            if (item != null) vm.setPreview(item.key) else if (onboarded) vm.loadMore()
        }
    }

    // Full screen also hides the status and navigation bars.
    val view = LocalView.current
    DisposableEffect(immersive) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    BackHandler(enabled = immersive) { immersive = false }

    val page = pagerState.currentPage
    val shown: StoredImage? = list.getOrNull(page)
    val isCurrent = shown != null && shown.key == current?.key
    val shownIsMine = MyPhotos.isMine(shown?.key)
    // Pages after the current wallpaper are the upcoming ones; before it, the history.
    val currentIndex = list.indexOfFirst { it.key == current?.key }
    val historyCount = st.history.size
    val upcomingNumber = page - (if (currentIndex >= 0) currentIndex else historyCount - 1)
    val position = when {
        shown == null -> ""
        isCurrent -> if (shownIsMine) "Сегашният ти тапет · моя снимка" else "Сегашният ти тапет"
        page < historyCount -> "От историята · " + Format.moment(shown.shownAt)
        else -> "Предстояща $upcomingNumber" + if (shownIsMine) " · моя снимка" else ""
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { p -> latestList.getOrNull(p)?.key ?: "loading-${latestList.size}" },
        ) { p ->
            val item = latestList.getOrNull(p)
            if (item != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(File(item.file)).build(),
                    contentDescription = item.candidate.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(onTap = { immersive = !immersive })
                    },
                )
            } else {
                EndPage(
                    loading = loadingMore || busy,
                    onboarded = settings.onboarded,
                    empty = latestList.isEmpty(),
                    onlyMine = settings.photoMode == PhotoMode.MINE,
                    onRetry = vm::loadMore,
                    onAddPhotos = pickPhotos,
                )
            }
        }

        AnimatedVisibility(visible = !immersive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Light scrims only behind the text: most of the picture stays clear (they do not block swipes or taps).
                Box(
                    Modifier.fillMaxWidth().height(110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent))),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (settings.onboarded) 300.dp else 480.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
                )

                Row(
                    Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Дневен тапет",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.titleMedium.copy(shadow = TextShadow),
                        modifier = Modifier.padding(start = 16.dp).weight(1f),
                    )
                    IconButton(onClick = { onNavigate(Screen.HISTORY) }) {
                        Icon(Icons.Filled.History, contentDescription = "История", tint = Color.White)
                    }
                    IconButton(onClick = { onNavigate(Screen.MY_PHOTOS) }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Моите снимки", tint = Color.White)
                    }
                    IconButton(onClick = { onNavigate(Screen.FAVORITES) }) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Любими", tint = Color.White)
                    }
                    IconButton(onClick = { onNavigate(Screen.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Color.White)
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                ) {
                    if (!settings.onboarded) {
                        Onboarding(unrestricted, busy, onAllow = { Power.requestUnrestricted(context) }, onStart = vm::startOnboarding)
                        return@Column
                    }
                    if (!unrestricted && settings.autoChange) {
                        Surface(
                            onClick = { onNavigate(Screen.SETTINGS) },
                            shape = RoundedCornerShape(50),
                            color = Color(0xCC5A3B00),
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFD58A), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Телефонът може да спира смяната – настрой", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Line 1: which picture this is, and "Задръж" / "Пусни".
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).padding(end = 8.dp)) {
                            if (shown != null && !isCurrent) {
                                Surface(onClick = vm::showCurrent, shape = RoundedCornerShape(50), color = Color(0xCC2D5DA8)) {
                                    Text(
                                        "$position  ·  към сегашния",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                            } else if (shown != null) {
                                Text(
                                    position,
                                    color = Color.White.copy(alpha = 0.88f),
                                    style = MaterialTheme.typography.labelLarge.copy(shadow = TextShadow),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HoldChip(held = !settings.autoChange, onClick = vm::toggleHold)
                    }

                    if (shown != null) {
                        // Line 2: the title, "любима" and everything else behind "⋯".
                        var menuOpen by remember { mutableStateOf(false) }
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                shown.candidate.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(shadow = TextShadow),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            val isFavorite = st.isFavorite(shown.key)
                            IconButton(onClick = { vm.toggleFavorite(shown.key) }) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Махни от любими" else "Добави в любими",
                                    tint = if (isFavorite) Color(0xFFFF8A95) else Color.White,
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Още", tint = Color.White)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Информация за снимката") },
                                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            showInfo = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Нагласи изрязването") },
                                        leadingIcon = { Icon(Icons.Filled.Crop, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = {
                                            menuOpen = false
                                            vm.openCrop(shown)
                                            onNavigate(Screen.CROP)
                                        },
                                    )
                                    if (!shownIsMine) {
                                        DropdownMenuItem(
                                            text = { Text("Запази в галерията") },
                                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                saveToGallery(shown)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Не показвай повече") },
                                        leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                        enabled = !busy,
                                        onClick = {
                                            menuOpen = false
                                            vm.block(shown.key)
                                        },
                                    )
                                    HorizontalDivider()
                                    val next = remember(st.lastChangeAt, settings, resumeTick) { vm.nextPlanned() }
                                    Text(
                                        when {
                                            !settings.autoChange -> "Тапетът е задържан – не се сменя сам"
                                            next != null -> "Следваща смяна: " + Format.moment(next)
                                            else -> "Готови снимки: ${st.queue.size}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                        // Line 3: where it comes from.
                        Text(
                            listOf(SourceRegistry.name(shown.candidate.sourceId), shown.candidate.author)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            if (loadingMore || busy) "Изтеглям снимки…" else "Няма повече готови снимки",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = TextShadow),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    // Line 4: ‹  Избери / Това е тапетът ти  ›
                    Row(
                        Modifier.padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RoundNav(Icons.AutoMirrored.Filled.ArrowBack, "Предишна", loading = false) {
                            bg.dnevnitapet.util.Diag.i("tap Предишна page=${pagerState.currentPage} size=${list.size}")
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            } else {
                                vm.hint("Това е най-старата снимка в историята")
                            }
                        }
                        Button(
                            onClick = {
                                bg.dnevnitapet.util.Diag.i("tap Избери page=${pagerState.currentPage} shown=${shown?.key}")
                                shown?.let { vm.choose(it.key) }
                            },
                            enabled = shown != null && !isCurrent && !busy,
                            modifier = Modifier.weight(1f).height(52.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.White.copy(alpha = 0.18f),
                                disabledContentColor = Color.White.copy(alpha = 0.9f),
                            ),
                        ) {
                            when {
                                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = if (isCurrent) Color.White else Color.Black)
                                isCurrent -> {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Това е тапетът ти", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                }
                                else -> Text("Избери", maxLines = 1, softWrap = false, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        RoundNav(Icons.AutoMirrored.Filled.ArrowForward, "Следваща", loading = loadingMore) {
                            bg.dnevnitapet.util.Diag.i("tap Следваща page=${pagerState.currentPage} size=${list.size} scrolling=${pagerState.isScrollInProgress}")
                            if (pagerState.currentPage < list.size) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                vm.loadMore()
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
    }

    if (showInfo && shown != null) {
        InfoSheet(shown, onDismiss = { showInfo = false }, onSave = { saveToGallery(shown) })
    }
}

/** The page after the last prepared picture: shows the download progress or "no more". */
@Composable
private fun EndPage(
    loading: Boolean,
    onboarded: Boolean,
    empty: Boolean,
    onlyMine: Boolean,
    onRetry: () -> Unit,
    onAddPhotos: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1B2A41), Color(0xFF324A5F), Color(0xFF0C1821))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (!onboarded && empty) return@Box
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp).padding(bottom = 200.dp)) {
            if (loading) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Изтеглям още снимки…", color = Color.White, style = MaterialTheme.typography.titleMedium)
            } else if (onboarded && onlyMine) {
                Text(
                    if (empty) "Още нямаш добавени снимки." else "Това са всичките ти снимки.\nПлъзни назад или добави още.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAddPhotos,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Добави снимки") }
            } else if (onboarded) {
                Text(
                    "Няма повече готови снимки.\nПлъзни назад или опитай пак.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Опитай пак") }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Soft shadow under white text, so it stays readable on light pictures without a dark band. */
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 6f)

/** "Предишна" / "Следваща": round arrow buttons beside the main button. */
@Composable
private fun RoundNav(icon: ImageVector, description: String, loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.42f),
        modifier = Modifier.size(52.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
        }
    }
}

/** "Задръж": keep the wallpaper you like (the automatic change pauses); "Пусни" resumes it. */
@Composable
private fun HoldChip(held: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (held) Color(0xCC2D5DA8) else Color.Black.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (held) 0.6f else 0.4f)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (held) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (held) "Пусни" else "Задръж",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun Onboarding(unrestricted: Boolean, busy: Boolean, onAllow: () -> Unit, onStart: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        // Explicit: with a translucent colour Compose cannot pick the matching text colour itself.
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Добре дошъл!", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Всеки ден ще слагам нова снимка на началния екран – от Bing, Windows Spotlight, " +
                    "Wikimedia и NASA. Плъзгай наляво и надясно, за да разглеждаш, „Избери“ слага снимката " +
                    "за тапет, а докосване на снимката я показва на цял екран.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Text("1. Разреши работа на заден план", style = MaterialTheme.typography.titleSmall)
            Text(
                "Иначе телефонът може да „приспи“ приложението и да пропусне смяната.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            if (unrestricted) {
                Text("✓ Разрешено", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            } else {
                OutlinedButton(onClick = onAllow) { Text("Разреши") }
            }
            Spacer(Modifier.height(14.dp))
            Text("2. Сложи първия тапет", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onStart, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Започни")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(image: StoredImage, onDismiss: () -> Unit, onSave: () -> Unit) {
    val c = image.candidate
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(c.title, style = MaterialTheme.typography.headlineSmall)
            if (c.location.isNotBlank() && c.location != c.title) InfoLine("Къде", c.location)
            if (c.description.isNotBlank() && c.description != c.title) {
                Text(c.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (c.author.isNotBlank()) InfoLine("Автор", c.author)
            if (c.copyright.isNotBlank() && c.copyright.removePrefix("©").trim() != c.author) InfoLine("Права", c.copyright)
            InfoLine("Източник", SourceRegistry.name(c.sourceId) + if (c.date.isNotBlank()) " · " + Format.date(c.date) else "")
            InfoLine("Размер", "${image.width} × ${image.height}")
            if (c.sourceId != MyPhotos.SOURCE_ID) Text(
                "Снимката е собственост на автора си. Използва се само като личен тапет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (c.infoUrl.isNotBlank()) {
                    FilledTonalButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.infoUrl))) }
                    }) { Text("Научи повече") }
                }
                TextButton(onClick = onSave) { Text("Запази в галерията") }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

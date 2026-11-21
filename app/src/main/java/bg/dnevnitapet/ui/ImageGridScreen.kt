package bg.dnevnitapet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.story.StoryText
import bg.dnevnitapet.wallpaper.MyPhotos
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import bg.dnevnitapet.i18n.tr

/** History (last ~30 wallpapers) or favourites, as a grid of thumbnails. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(vm: MainViewModel, favorites: Boolean, onBack: () -> Unit, onOpenCrop: () -> Unit) {
    val st by vm.library.collectAsStateWithLifecycle()
    val translations by vm.translations.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<StoredImage?>(null) }
    var info by remember { mutableStateOf<StoredImage?>(null) }
    val saveToGallery = rememberGallerySave(vm)
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.text) } }

    val items = if (favorites) st.favorites else listOfNotNull(st.current) + st.history
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (favorites) tr("Любими") else tr("История")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (favorites) tr("Още нямаш любими. Докосни ♡ върху снимка, която харесваш.")
                    else tr("Тук ще виждаш последните 30 тапета."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.key + it.shownAt }) { image ->
                    Thumbnail(image, StoryText.title(image.candidate, translations.entries), isCurrent = image.key == st.current?.key, isFavorite = st.isFavorite(image.key)) { selected = image }
                }
            }
        }
    }

    selected?.let { image ->
        val isFavorite = st.isFavorite(image.key)
        PhotoSheet(
            image = image,
            title = StoryText.title(image.candidate, translations.entries),
            subtitle = listOf(
                SourceRegistry.name(image.candidate.sourceId),
                Format.moment(if (image.shownAt > 0) image.shownAt else image.addedAt),
            ).filter { it.isNotBlank() }.joinToString(" · "),
            actions = listOf(
                SheetAction(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (isFavorite) tr("Любима") else tr("Харесай"),
                    tint = if (isFavorite) Color(0xFFFF6B81) else null,
                ) {
                    vm.toggleFavorite(image.key)
                    if (favorites && isFavorite) selected = null
                },
                SheetAction(Icons.Outlined.Crop, tr("Нагласи"), enabled = !busy) {
                    vm.openCrop(image)
                    selected = null
                    onOpenCrop()
                },
                SheetAction(Icons.Outlined.Download, tr("Запази"), enabled = !MyPhotos.isMine(image.key)) { saveToGallery(image) },
                SheetAction(Icons.Outlined.Share, tr("Сподели")) {
                    if (!Share.share(context, image)) vm.hint(tr("Няма приложение, с което да се сподели"))
                },
                SheetAction(Icons.Outlined.Info, tr("Инфо")) {
                    info = image
                    selected = null
                },
            ),
            isCurrent = image.key == st.current?.key,
            busy = busy,
            onApply = {
                vm.apply(image)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
    info?.let { image -> InfoSheet(image, onDismiss = { info = null }, onSave = { saveToGallery(image) }) }
}

@Composable
private fun Thumbnail(image: StoredImage, title: String, isCurrent: Boolean, isFavorite: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(image.file)).size(360, 780).crossfade(true).build(),
                contentDescription = image.candidate.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(14.dp)),
            )
            if (isFavorite) {
                Icon(
                    Icons.Filled.Favorite, null, tint = Color(0xFFFF6B81),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            if (isCurrent) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                ) {
                    Text(
                        tr("Сега"), color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
        Text(
            Format.moment(if (image.shownAt > 0) image.shownAt else image.addedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

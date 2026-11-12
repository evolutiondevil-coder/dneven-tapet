package bg.dnevnitapet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.model.PhotoMode
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.wallpaper.MyPhotos
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * "Моите снимки": photos picked from the phone's gallery. The app keeps its own copies (the
 * originals are not touched) and can rotate them instead of, or together with, internet pictures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPhotosScreen(vm: MainViewModel, onBack: () -> Unit, onOpenCrop: () -> Unit) {
    val st by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<StoredImage?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.text) } }

    // System photo picker: no storage permission needed; the app copies what is picked.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)) { uris ->
        vm.addMyPhotos(uris)
    }
    val room = MyPhotos.MAX_PHOTOS - st.myPhotos.size
    val bytes = remember(st.myPhotos.size) { vm.myPhotosBytes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моите снимки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (importing == null && room > 0) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    icon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                    text = { Text("Добави снимки") },
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PhotoModeCard(settings.photoMode, hasPhotos = st.myPhotos.isNotEmpty(), onSelect = vm::setPhotoMode)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val progress = importing
                    if (progress != null) {
                        Text("Добавям снимки… ${progress.first} от ${progress.second}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(6.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            if (st.myPhotos.isEmpty()) "Още нямаш добавени снимки."
                            else "${st.myPhotos.size} снимки · ${Format.megabytes(bytes)} · до ${MyPhotos.MAX_PHOTOS}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Приложението пази свои копия, нагодени за екрана. Снимките в галерията не се променят и не се трият.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(st.myPhotos, key = { it.key }) { photo ->
                val blocked = photo.key in st.blocked
                Box(Modifier.clickable { selected = photo }) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(File(photo.file)).size(320, 700).crossfade(true).build(),
                        contentDescription = photo.candidate.description.ifBlank { photo.candidate.title },
                        contentScale = ContentScale.Crop,
                        alpha = if (blocked) 0.4f else 1f,
                        modifier = Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1))
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    if (photo.key == st.current?.key) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        ) {
                            Text(
                                "Сега", color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (blocked) {
                        Icon(Icons.Filled.Block, contentDescription = "Скрита", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                }
            }
        }
    }

    selected?.let { photo ->
        val blocked = photo.key in st.blocked
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(photo.candidate.description.ifBlank { photo.candidate.title }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(File(photo.file)).size(720, 1560).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1) * 1.4f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            vm.openCrop(photo)
                            selected = null
                            onOpenCrop()
                        }) { Text("Нагласи") }
                        if (blocked) {
                            TextButton(onClick = { vm.unblock(photo.key); selected = null }) { Text("Покажи пак") }
                        }
                        TextButton(onClick = { vm.removeMine(photo.key); selected = null }) { Text("Махни") }
                    }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = { vm.applyMine(photo.key); selected = null }) { Text("Сложи като тапет") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Затвори") } },
        )
    }
}

/** Choice of where the wallpapers come from; used here and in the settings. */
@Composable
fun PhotoModeCard(mode: PhotoMode, hasPhotos: Boolean, onSelect: (PhotoMode) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Какви снимки да се въртят",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ModeRow("Снимки от интернет", "Bing, Spotlight, Wikimedia, NASA…", mode == PhotoMode.ONLINE) { onSelect(PhotoMode.ONLINE) }
            ModeRow("Моите снимки от телефона", "Само снимките, добавени тук – в разбъркан ред", mode == PhotoMode.MINE) { onSelect(PhotoMode.MINE) }
            ModeRow("Смесено", "Редуват се: една твоя, една от интернет", mode == PhotoMode.MIXED) { onSelect(PhotoMode.MIXED) }
            if (mode != PhotoMode.ONLINE && !hasPhotos) {
                Text(
                    "Добави поне една снимка, иначе няма какво да се върти.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
    }
}

package bg.dnevnitapet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bg.dnevnitapet.model.Album
import bg.dnevnitapet.model.LibraryState
import bg.dnevnitapet.model.PhotoMode
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.WallpaperTarget
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * "Албуми" (optional): own photos in albums; the home and the lock screen can each take their
 * own album. Off until switched on; with it off all own photos take turns as before.
 */
@Composable
fun AlbumsSection(vm: MainViewModel, st: LibraryState, s: Settings) {
    var openAlbum by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().toggleable(value = s.albumsOn, role = Role.Switch, onValueChange = vm::setAlbumsOn)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF5B8DEF).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Folder, contentDescription = null, tint = Color(0xFF5B8DEF), modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Албуми", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Подреди снимките си в албуми и избери кой къде да се показва",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(shape = RoundedCornerShape(10.dp), color = SelectedOrange.copy(alpha = 0.14f), modifier = Modifier.padding(top = 6.dp)) {
                        Text("По избор", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = s.albumsOn, colors = choiceSwitchColors(), onCheckedChange = null)
            }
            if (s.albumsOn) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(st.albums, key = { it.id }) { a ->
                        AlbumTile(
                            album = a,
                            cover = st.myPhotos.firstOrNull { it.key in a.keys }?.file,
                            badge = when {
                                a.id == s.homeAlbum && a.id == s.lockAlbum -> "И двата"
                                a.id == s.homeAlbum -> "Начален"
                                a.id == s.lockAlbum -> "Заключен"
                                else -> null
                            },
                        ) { openAlbum = a.id }
                    }
                    item {
                        Column(Modifier.width(120.dp).clickable { naming = true }) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp)) }
                            Text("Нов албум", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                if (st.albums.isNotEmpty()) {
                    ScreenAlbumRow(Icons.Outlined.Home, "Начален екран", s.homeAlbum, st.albums) { vm.setScreenAlbum(lock = false, it) }
                    ScreenAlbumRow(Icons.Outlined.Lock, "Заключен екран", s.lockAlbum, st.albums) { vm.setScreenAlbum(lock = true, it) }
                    val hint = when {
                        s.photoMode == PhotoMode.ONLINE -> "Албумите важат, когато се въртят твоите снимки („Моите снимки“ или „Смесено“ отгоре)."
                        s.lockAlbum.isNotBlank() && s.target != WallpaperTarget.BOTH ->
                            "За албум на заключения екран избери „И двата“ в Настройки → Къде да се слага."
                        else -> null
                    }
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                } else {
                    Text(
                        "Направи първия си албум с „Нов албум“.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }

    if (naming) {
        NameDialog(title = "Нов албум", initial = "", onDismiss = { naming = false }) { name ->
            naming = false
            vm.createAlbum(name) { openAlbum = it }
        }
    }
    openAlbum?.let { id ->
        val album = st.albums.firstOrNull { it.id == id }
        if (album != null) AlbumSheet(vm, album, st, onDismiss = { openAlbum = null })
    }
}

@Composable
private fun AlbumTile(album: Album, cover: String?, badge: String?, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (badge != null) Modifier.border(3.dp, SelectedOrange, RoundedCornerShape(18.dp)) else Modifier),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(cover)).size(300, 300).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).size(32.dp))
            }
            if (badge != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = SelectedOrange, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Text(badge, color = OnSelectedOrange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("${album.keys.size} ${if (album.keys.size == 1) "снимка" else "снимки"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Начален екран · Пътувания [Смени]" with a menu of the albums. */
@Composable
private fun ScreenAlbumRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, albumId: String, albums: List<Album>, onSelect: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = SelectedOrange)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                albums.firstOrNull { it.id == albumId }?.name ?: "Всички мои снимки",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { menu = true }) { Text("Смени") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Всички мои снимки") }, onClick = {
                    menu = false
                    onSelect("")
                })
                albums.forEach { a ->
                    DropdownMenuItem(text = { Text(a.name) }, onClick = {
                        menu = false
                        onSelect(a.id)
                    })
                }
            }
        }
    }
}

/** An album: tap photos to put them in or take them out; rename or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSheet(vm: MainViewModel, album: Album, st: LibraryState, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(album.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Докосни снимка, за да я сложиш в албума или да я махнеш",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { renaming = true }) { Text("Преименувай") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 460.dp).padding(top = 12.dp),
            ) {
                items(st.myPhotos, key = { it.key }) { photo ->
                    val inside = photo.key in album.keys
                    Box(
                        Modifier.aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1)).clip(RoundedCornerShape(12.dp))
                            .clickable { vm.toggleInAlbum(album.id, photo.key) },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(File(photo.file)).size(280, 600).crossfade(true).build(),
                            contentDescription = photo.candidate.description.ifBlank { photo.candidate.title },
                            contentScale = ContentScale.Crop,
                            alpha = if (inside) 1f else 0.55f,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                                .background(if (inside) SelectedOrange else Color.Black.copy(alpha = 0.35f))
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inside) Icon(Icons.Filled.Check, contentDescription = "В албума", tint = OnSelectedOrange, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { deleting = true }) { Text("Изтрий албума", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Готово") }
            }
        }
    }
    if (renaming) {
        NameDialog(title = "Име на албума", initial = album.name, onDismiss = { renaming = false }) {
            renaming = false
            vm.renameAlbum(album.id, it)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Да изтрия ли „${album.name}“?") },
            text = { Text("Снимките остават в „Моите снимки“ – трие се само албумът.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deleteAlbum(album.id)
                    onDismiss()
                }) { Text("Изтрий") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отказ") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                placeholder = { Text("напр. Пътувания") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отказ") } },
    )
}

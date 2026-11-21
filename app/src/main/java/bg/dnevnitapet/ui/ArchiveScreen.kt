package bg.dnevnitapet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.archive.ArchiveEntry
import bg.dnevnitapet.archive.ArchiveSearch
import bg.dnevnitapet.archive.ColorBucket
import bg.dnevnitapet.archive.Thumbs
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.sources.label
import bg.dnevnitapet.story.StoryText
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * "Архив": every picture seen on this phone (and older ones from NASA and Wikimedia), with search
 * by words, category, colour, country, source and year. Always there, nothing to switch on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArchiveScreen(vm: MainViewModel, onBack: () -> Unit) {
    val archive by vm.archive.collectAsStateWithLifecycle()
    val translations by vm.translations.collectAsStateWithLifecycle()
    val loading by vm.archiveLoading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.text) } }

    var text by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var color by rememberSaveable { mutableStateOf<String?>(null) }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf<ArchiveEntry?>(null) }

    val entries = archive.entries
    val query = ArchiveSearch.Query(text, category, color, country, source, year)
    val found = remember(entries, query, translations) { entries.filter { ArchiveSearch.matches(it, query, translations.entries) } }
    val countries = remember(entries) { ArchiveSearch.countries(entries) }
    val years = remember(entries) { ArchiveSearch.years(entries) }
    val sources = remember(entries) { ArchiveSearch.sources(entries) }
    fun bg(s: String) = translations.entries[s] ?: s

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Търси: море, Норвегия, залез…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, "Изчисти") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { ChoiceChip(selected = category == null, onClick = { category = null }, label = { Text("Всички") }) }
                        items(Category.entries.toList()) { c ->
                            ChoiceChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorBucket.all.forEach { (bucket, argb) ->
                            ColorDot(Color(argb), bucket == "bw", selected = color == bucket, label = ColorBucket.label(bucket)) {
                                color = if (color == bucket) null else bucket
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterMenu(
                            label = country?.let(::bg) ?: "Държава",
                            selected = country != null,
                            options = listOf<String?>(null) + countries,
                            optionLabel = { it?.let(::bg) ?: "Всички държави" },
                            onSelect = { country = it },
                        )
                        FilterMenu(
                            label = source?.let(SourceRegistry::name)?.substringBefore(" –") ?: "Източник",
                            selected = source != null,
                            options = listOf<String?>(null) + sources,
                            optionLabel = { it?.let(SourceRegistry::name) ?: "Всички източници" },
                            onSelect = { source = it },
                        )
                        FilterMenu(
                            label = year?.toString() ?: "Година",
                            selected = year != null,
                            options = listOf<Int?>(null) + years,
                            optionLabel = { it?.toString() ?: "Всички години" },
                            onSelect = { year = it },
                        )
                    }
                    val summary = listOfNotNull(
                        category?.label?.lowercase(), color?.let(ColorBucket::label), country?.let(::bg), year?.toString(),
                    )
                    Text(
                        "${found.size} ${if (found.size == 1) "снимка" else "снимки"}" + summary.joinToString("") { " · $it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }

            if (found.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (entries.isEmpty()) "Тук се събират всички снимки, които видиш. Можеш да заредиш и стари снимки от NASA и Wikimedia."
                            else "Нищо не намерих. Опитай с друга дума или махни някой филтър.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        if (!query.isEmpty) {
                            TextButton(onClick = {
                                text = ""
                                category = null
                                color = null
                                country = null
                                source = null
                                year = null
                            }) { Text("Изчисти търсенето") }
                        }
                    }
                }
            }

            items(found, key = { it.candidate.key }) { e ->
                ArchiveTile(e, StoryText.title(e.candidate, translations.entries)) { open = e }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedButton(onClick = vm::loadMoreArchive, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Зареждам…")
                        } else {
                            Text("Още от архива на NASA и Wikimedia")
                        }
                    }
                    Text(
                        "По-стари снимки на деня – няколко наведнъж.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    open?.let { e -> ArchiveSheet(vm, e, StoryText.title(e.candidate, translations.entries), onDismiss = { open = null }) }
}

@Composable
private fun ColorDot(color: Color, split: Boolean, selected: Boolean, label: String, onClick: () -> Unit) {
    val fill = if (split) Brush.linearGradient(0.5f to Color.White, 0.5f to Color(0xFF222222)) else Brush.linearGradient(listOf(color, color))
    Box(
        Modifier.size(34.dp)
            .then(if (selected) Modifier.border(3.dp, SelectedOrange, CircleShape) else Modifier)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = if (split) SelectedOrange else Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun <T> FilterMenu(label: String, selected: Boolean, options: List<T>, optionLabel: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChoiceChip(selected = selected, onClick = { expanded = true }, label = { Text("$label ▾", maxLines = 1, softWrap = false) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = {
                    expanded = false
                    onSelect(o)
                })
            }
        }
    }
}

@Composable
private fun ArchiveTile(e: ArchiveEntry, title: String, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(File(e.thumb)).crossfade(true).build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A picture opened in the archive: bigger preview, its story, "Сложи като тапет". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArchiveSheet(vm: MainViewModel, e: ArchiveEntry, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val st by vm.library.collectAsStateWithLifecycle()
    val c = e.candidate
    val local = (listOfNotNull(st.current) + st.queue + st.history + st.favorites).firstOrNull { it.key == c.key && File(it.file).exists() }
    val big = c.variants.maxByOrNull { it.width }
    var story by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(local?.file?.let(::File) ?: Thumbs.url(c, 960) ?: File(e.thumb))
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(SourceRegistry.name(c.sourceId), c.date.takeIf { it.isNotBlank() }?.let(Format::date).orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { story = true }) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Историята")
                }
                if (c.infoUrl.isNotBlank()) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(c.infoUrl))) }
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("В браузъра")
                    }
                }
            }
            val isCurrent = st.current?.key == c.key
            Button(
                onClick = { vm.applyArchive(e, onDismiss) },
                enabled = !busy && !isCurrent,
                colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = OnSelectedOrange)
                    isCurrent -> Text("Това е тапетът ти", maxLines = 1, softWrap = false)
                    else -> Text("Сложи като тапет", style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                }
            }
            if (local == null && !isCurrent) {
                Text(
                    "Снимката ще се изтегли в пълен размер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (story) {
        InfoSheet(
            local ?: StoredImage(c, e.thumb, big?.width ?: 0, big?.height ?: 0, addedAt = e.addedAt),
            onDismiss = { story = false },
            onSave = {},
        )
    }
}

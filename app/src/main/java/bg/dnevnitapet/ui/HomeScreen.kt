package bg.dnevnitapet.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.model.StoredImage
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.util.Power
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@Composable
fun HomeScreen(vm: MainViewModel, onNavigate: (Screen) -> Unit) {
    val st by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showInfo by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val unrestricted = remember(resumeTick) { Power.isUnrestricted(context) }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, actionLabel = m.actionLabel, withDismissAction = m.actionLabel == null)
            if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }

    val current = st.current
    Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
        if (current != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(current.file)).crossfade(400).build(),
                contentDescription = current.candidate.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1B2A41), Color(0xFF324A5F), Color(0xFF0C1821))),
                ),
            )
        }
        // Scrims keep the text readable on any picture.
        Box(
            Modifier.fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(420.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Дневен тапет",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            IconButton(onClick = { onNavigate(Screen.HISTORY) }) {
                Icon(Icons.Filled.History, contentDescription = "История", tint = Color.White)
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
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFD58A), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Телефонът може да спира смяната – настрой", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (current != null) {
                Text(
                    current.candidate.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = current.candidate.location.ifBlank { current.candidate.description }
                if (subtitle.isNotBlank() && subtitle != current.candidate.title) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    listOf(SourceRegistry.name(current.candidate.sourceId), current.candidate.author)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    if (busy) "Изтеглям първата снимка…" else "Още няма тапет. Натисни „Следваща“.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isFavorite = current != null && st.isFavorite(current.key)
                RoundAction(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (isFavorite) "Махни от любими" else "Добави в любими",
                    enabled = current != null,
                    tint = if (isFavorite) Color(0xFFFF6B81) else Color.White,
                ) { current?.let { vm.toggleFavorite(it.key) } }
                RoundAction(Icons.Filled.Block, "Не показвай повече", enabled = current != null && !busy) {
                    current?.let { vm.block(it.key) }
                }
                RoundAction(Icons.Filled.Info, "Информация за снимката", enabled = current != null) { showInfo = true }
                RoundAction(Icons.Filled.Download, "Запази в галерията", enabled = current != null) {
                    current?.let { vm.save(it) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = vm::next,
                    enabled = !busy,
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                    } else {
                        Text("Следваща", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            val next = remember(st.lastChangeAt, settings, resumeTick) { vm.nextPlanned() }
            Text(
                buildString {
                    append("Готови снимки: ${st.queue.size}")
                    if (next != null) append(" · следваща смяна: " + Format.moment(next))
                    else if (!settings.autoChange) append(" · автоматичната смяна е изключена")
                },
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
    }

    if (showInfo && current != null) {
        InfoSheet(current, onDismiss = { showInfo = false }, onSave = { vm.save(current) })
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.38f),
        modifier = Modifier.padding(end = 10.dp).size(46.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = 0.4f))
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
                    "Wikimedia и NASA. Ако не ти хареса, „Следваща“ я сменя веднага.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Text("1. Разреши работа на заден план", style = MaterialTheme.typography.titleSmall)
            Text(
                "Иначе Samsung може да „приспи“ приложението и да пропусне смяната.",
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
            Text(
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

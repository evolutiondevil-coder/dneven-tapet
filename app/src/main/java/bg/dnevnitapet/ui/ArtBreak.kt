package bg.dnevnitapet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import bg.dnevnitapet.i18n.tr

/**
 * "Пауза за изкуство": every 30 pictures browsed (once per opening at most), a famous painting in a
 * frame, its title, painter and year, a few lines about it and links to read more. The first one
 * asks whether more should come.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtBreak(
    show: MainViewModel.ArtShow,
    busy: Boolean,
    onApply: () -> Unit,
    onClose: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    BackHandler(onBack = onClose)
    val art = show.art
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF3A3027), Color(0xFF15110D)), radius = 1500f))
            // Taps on the background do nothing (only the buttons act).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top: what this is, and a way out.
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SelectedOrange.copy(alpha = 0.16f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        tr("🎨 Пауза за изкуство"),
                        color = Gold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Затвори"), tint = Color.White.copy(alpha = 0.85f))
                }
            }

            // The painting in a thin golden frame, as large as the space allows.
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.shadow(20.dp, RoundedCornerShape(6.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8A6A3A), Color(0xFFD6B57A), Color(0xFF6E522B))), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .border(3.dp, Color(0xFF231B12)),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(art.imageUrl).crossfade(true).build(),
                        contentDescription = show.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(min = 120.dp),
                    )
                }
            }

            // About it: title, painter and year, a few lines, links. Everything centred.
            Column(
                Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    show.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(art.artist, art.date).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, color = Gold, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                }
                if (show.story.isNotBlank()) {
                    Text(
                        show.story,
                        color = Color(0xFFD9D1C6),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    LinkPill(Icons.Outlined.Info, tr("Повече за картината")) { openLink(context, art.pageUrl) }
                    LinkPill(Icons.AutoMirrored.Outlined.MenuBook, tr("Уикипедия")) {
                        val wiki = if (bg.dnevnitapet.i18n.I18n.language == bg.dnevnitapet.i18n.AppLanguage.BG) "bg" else "en"
                        val q = java.net.URLEncoder.encode(listOf(art.title, art.artist).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
                        openLink(context, "https://$wiki.wikipedia.org/w/index.php?search=$q")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (show.ask) {
                // The first painting: should more come?
                Surface(shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.07f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            tr("Да показвам ли картина на всеки 30 разгледани снимки?"),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            tr("Най-много веднъж, докато приложението е отворено."),
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { onAnswer(false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(tr("Не, благодаря"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Button(
                                onClick = { onAnswer(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(tr("Да, показвай"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            // The two actions, one under the other so their words always fit.
            OutlinedButton(
                onClick = onApply,
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Outlined.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Сложи като тапет"), maxLines = 1)
                }
            }
            if (!show.ask) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(tr("Продължи"), maxLines = 1) }
            }
            Text(
                tr("Wikimedia Commons · обществено достояние"),
                color = Muted.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private val Gold = Color(0xFFFFC27A)
private val Muted = Color(0xFFB3A797)

/** A link to read more: icon and words on one line, in a soft golden outline. */
@Composable
private fun LinkPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
        contentColor = Gold,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

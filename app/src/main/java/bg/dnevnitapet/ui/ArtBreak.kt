package bg.dnevnitapet.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import bg.dnevnitapet.art.Artwork
import coil.compose.AsyncImage
import coil.request.ImageRequest
import bg.dnevnitapet.i18n.tr

/**
 * "Пауза за изкуство": after every 20 pictures browsed, a famous painting in a frame, with the
 * painter, the year and its story in Bulgarian. "Сложи за тапет" or "Към снимките →".
 */
@Composable
fun ArtBreak(show: MainViewModel.ArtShow, busy: Boolean, onApply: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val art = show.art
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF3A3128), Color(0xFF17130F)), radius = 1400f))
            // Taps on the background do nothing (only the buttons act).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = SelectedOrange.copy(alpha = 0.16f)) {
                Text(
                    tr("🎨 Пауза за изкуство"),
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            // The painting in a golden frame, as large as fits.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.shadow(18.dp, RoundedCornerShape(4.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8A6A3A), Color(0xFFC9A56B), Color(0xFF6E522B))), RoundedCornerShape(4.dp))
                        .padding(12.dp)
                        .border(4.dp, Color(0xFF2A2116)),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(art.imageUrl).crossfade(true).build(),
                        contentDescription = show.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(min = 120.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(show.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOf(art.artist, art.date, Artwork.MUSEUM).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color(0xFFE0C9A6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (show.story.isNotBlank()) {
                    Text(show.story, color = Color(0xFFE2DCD3), style = MaterialTheme.typography.bodyLarge)
                }
                // More about the painting: its page, and an encyclopaedia article.
                val context = LocalContext.current
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { openLink(context, art.pageUrl) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFB74D)),
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Повече за картината"))
                    }
                    TextButton(
                        onClick = {
                            val wiki = if (bg.dnevnitapet.i18n.I18n.language == bg.dnevnitapet.i18n.AppLanguage.BG) "bg" else "en"
                            val q = java.net.URLEncoder.encode(listOf(show.title, art.artist).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
                            openLink(context, "https://$wiki.wikipedia.org/w/index.php?search=$q")
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFB74D)),
                    ) {
                        Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Уикипедия"))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onApply,
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(tr("Сложи за тапет"), maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text(tr("Към снимките →"), maxLines = 1, softWrap = false) }
            }
            Text(
                tr("Идва на всеки 20 разгледани снимки · изключва се от Настройки"),
                color = Color(0xFF8E8578),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

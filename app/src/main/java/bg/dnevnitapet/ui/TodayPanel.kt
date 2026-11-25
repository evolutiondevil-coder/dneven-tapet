package bg.dnevnitapet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import bg.dnevnitapet.R
import bg.dnevnitapet.i18n.tr
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.sources.label
import bg.dnevnitapet.story.DayFact
import bg.dnevnitapet.story.DayPage
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** What the "Днес" / "Моят дневник" panel shows (worked out by the caller). */
class TodayInfo(
    val currentTitle: String?,
    val currentSource: String,
    /** The current wallpaper's picture (for the small photo), or null. */
    val currentFile: String?,
    val changedAt: Long,
    /** Off: the wallpaper is on hold ("Задържан"). */
    val autoChange: Boolean,
    /** When the next picture comes (0 when not known). */
    val nextAt: Long,
    val ready: Int,
    val shownCount: Int,
    val favorites: Int,
    val streak: Int,
    val topCategories: List<String>,
    val topSource: String?,
    val firstDay: String,
    /** "На този ден", or null while it is not there (no network, nothing pleasant that day). */
    val fact: DayFact?,
)

private val CardColor = Color(0xF51F1915)
private val BoxColor = Color(0xFF2E2722)
private val Muted = Color(0xFFB8A99D)
private val Accent = Color(0xFFFFB74D)
private val Soft = Color(0xFFD8CBC0)

/**
 * A tap on the logo: a card with two pages, changed by swiping – "Днес" (greeting, an event from
 * history on today's date, the current wallpaper – a tap opens its story – and how long until the
 * next one) and "Моят дневник" (numbers about the wallpapers).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayPanel(
    info: TodayInfo,
    onStory: () -> Unit,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { 2 })
    Surface(shape = RoundedCornerShape(24.dp), color = CardColor, contentColor = Color.White, shadowElevation = 10.dp, modifier = modifier) {
        Column(Modifier.padding(vertical = 16.dp)) {
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page == 0) TodayPage(info, onStory) else DiaryPage(info, onFavorites, onHistory)
                }
            }
            // Which page: two dots, and where the other one is.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(2) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (pager.currentPage == i) SelectedOrange else Color.White.copy(alpha = 0.35f)),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (pager.currentPage == 0) tr("Плъзни за „Моят дневник“") else tr("← Днес"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun TodayPage(info: TodayInfo, onStory: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.mipmap.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(Greeting.date(LocalDate.now()), style = MaterialTheme.typography.bodySmall, color = Muted)
            Text(Greeting.phrase(LocalTime.now().hour), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
    AnimatedVisibility(visible = info.fact != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        info.fact?.let { FactBox(it) }
    }
    CurrentWallpaper(info, onStory)
    NextPicture(info)
}

/** "На този ден": the year, what happened and a link to Wikipedia. */
@Composable
private fun FactBox(fact: DayFact) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BoxColor).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(tr("На този ден"), style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(DayPage.yearLabel(fact.year, fact.lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Accent)
        Text(fact.text, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
        Text(
            tr("Прочети още в Уикипедия ›"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = SelectedOrange,
            modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp))
                .clickable { runCatching { uri.openUri(fact.link) } }
                .padding(vertical = 4.dp),
        )
    }
}

/** The wallpaper on the phone now, with a small photo; a tap opens its story. */
@Composable
private fun CurrentWallpaper(info: TodayInfo, onStory: () -> Unit) {
    val has = info.currentTitle != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BoxColor)
            .then(if (has) Modifier.clickable(onClickLabel = tr("Историята на снимката"), onClick = onStory) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (info.currentFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(info.currentFile)).size(240).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr("Сегашният ти тапет"), style = MaterialTheme.typography.labelMedium, color = Muted)
            Text(
                info.currentTitle ?: tr("Още няма – избери снимка от началния екран"),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (has) {
                Text(
                    info.currentSource + if (info.changedAt > 0) tr(" · сложен {0}", Format.moment(info.changedAt)) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (has) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}

/** How long until the next picture, with a bar that fills up; or "Задържан". */
@Composable
private fun NextPicture(info: TodayInfo) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val left = info.nextAt - now
    val line = when {
        !info.autoChange -> tr("Задържан – не се сменя сам")
        info.nextAt <= 0 -> ""
        left <= 60_000 -> tr("Следващата снимка идва всеки момент")
        left < 24 * 3_600_000L -> tr("Следващата снимка – след {0}", Format.duration(left))
        else -> tr("Следващата снимка – {0}", Format.moment(info.nextAt))
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = Soft, modifier = Modifier.weight(1f))
            Text(tr("Готови: {0}", info.ready), style = MaterialTheme.typography.bodySmall, color = Soft)
        }
        if (info.autoChange && info.nextAt > info.changedAt && info.changedAt > 0) {
            val part = ((now - info.changedAt).toFloat() / (info.nextAt - info.changedAt)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxWidth(part).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(SelectedOrange))
            }
        }
    }
}

@Composable
private fun DiaryPage(info: TodayInfo, onFavorites: () -> Unit, onHistory: () -> Unit) {
    Text(tr("Моят дневник"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(info.shownCount.toString(), tr("тапета досега"), Modifier.weight(1f))
        Stat(info.favorites.toString(), tr("любими"), Modifier.weight(1f))
        Stat(info.streak.toString(), if (info.streak == 1) tr("ден поред") else tr("дни поред"), Modifier.weight(1f))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BoxColor).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (info.topCategories.isEmpty()) {
            Text(tr("Харесвай снимки с ♥ и тук ще видиш какво обичаш най-много."), style = MaterialTheme.typography.bodySmall, color = Soft)
        } else {
            DiaryLine(tr("Най-харесвани: "), info.topCategories.joinToString(tr(" и ")))
        }
        info.topSource?.let { DiaryLine(tr("Любим източник: "), SourceRegistry.name(it)) }
        if (info.firstDay.isNotBlank()) DiaryLine(tr("С приложението си от: "), Format.date(info.firstDay))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onFavorites,
            colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
            modifier = Modifier.weight(1f).height(46.dp),
        ) { Text(tr("Виж любимите"), maxLines = 1, softWrap = false) }
        OutlinedButton(
            onClick = onHistory,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.weight(1f).height(46.dp),
        ) { Text(tr("Виж историята"), maxLines = 1, softWrap = false) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(BoxColor).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Soft, maxLines = 2)
    }
}

@Composable
private fun DiaryLine(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append(label)
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(value) }
        },
        style = MaterialTheme.typography.bodySmall,
        color = Soft,
    )
}

/** Category names for the diary. */
fun categoryNames(categories: List<bg.dnevnitapet.model.Category>): List<String> = categories.map { it.label }

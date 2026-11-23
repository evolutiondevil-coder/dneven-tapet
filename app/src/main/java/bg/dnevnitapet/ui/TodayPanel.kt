package bg.dnevnitapet.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import java.time.LocalDate
import java.time.LocalTime

/** What the "Днес" / "Моят дневник" panel shows (worked out by the caller). */
class TodayInfo(
    val currentTitle: String?,
    val currentSource: String,
    val changedAt: Long,
    /** "Следваща смяна: …" or "Задържан – …". */
    val nextLine: String,
    val ready: Int,
    val shownCount: Int,
    val favorites: Int,
    val streak: Int,
    val topCategories: List<String>,
    val topSource: String?,
    val firstDay: String,
)

private val CardColor = Color(0xF51C1B1F)
private val BoxColor = Color(0xFF26252B)
private val Muted = Color(0xFFA9A7B0)
private val Accent = Color(0xFFFFB74D)

/**
 * A tap on the logo: a card with two pages, changed by swiping – "Днес" (greeting, the current
 * wallpaper, the next change, "Нова снимка сега") and "Моят дневник" (numbers about the wallpapers).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayPanel(
    info: TodayInfo,
    busy: Boolean,
    onNewPicture: () -> Unit,
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
                    if (page == 0) TodayPage(info, busy, onNewPicture, onStory) else DiaryPage(info, onFavorites, onHistory)
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
private fun TodayPage(info: TodayInfo, busy: Boolean, onNewPicture: () -> Unit, onStory: () -> Unit) {
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
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BoxColor).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(tr("Сегашният ти тапет"), style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(
            info.currentTitle ?: tr("Още няма – натисни „Нова снимка сега“"),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (info.currentTitle != null) {
            Text(
                info.currentSource + if (info.changedAt > 0) tr(" · сложен {0}", Format.moment(info.changedAt)) else "",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(info.nextLine, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4C6D0), modifier = Modifier.weight(1f))
        Text(tr("Готови: {0}", info.ready), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4C6D0))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onNewPicture,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = SelectedOrange, contentColor = OnSelectedOrange),
            modifier = Modifier.weight(1f).height(46.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnSelectedOrange)
            else Text(tr("Нова снимка сега"), maxLines = 1, softWrap = false)
        }
        OutlinedButton(
            onClick = onStory,
            enabled = info.currentTitle != null,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier.weight(1f).height(46.dp),
        ) { Text(tr("Историята"), maxLines = 1, softWrap = false) }
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
            Text(tr("Харесвай снимки с ♥ и тук ще видиш какво обичаш най-много."), style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4C6D0))
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
        ) { Text(tr("Историята"), maxLines = 1, softWrap = false) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(BoxColor).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC4C6D0), maxLines = 2)
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
        color = Color(0xFFC4C6D0),
    )
}

/** Category names for the diary. */
fun categoryNames(categories: List<bg.dnevnitapet.model.Category>): List<String> = categories.map { it.label }

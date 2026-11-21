package bg.dnevnitapet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bg.dnevnitapet.R
import bg.dnevnitapet.update.ReleaseNotes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Words of the daily greeting. Pure logic, unit-tested. */
object Greeting {
    fun phrase(hour: Int): String = when (hour) {
        in 5..10 -> "Добро утро"
        in 11..17 -> "Добър ден"
        else -> "Добър вечер"
    }

    /** "Петък, 21 ноември". */
    fun date(day: LocalDate): String =
        day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("bg"))).replaceFirstChar { it.uppercase() }
}

private class WelcomePage(val icon: ImageVector, val title: String, val text: String)

private val pages = listOf(
    WelcomePage(
        Icons.Outlined.Image, "Нов тапет всеки ден",
        "Всяка сутрин слагам нова красива снимка – от Bing, Windows Spotlight, Wikimedia и NASA. Не трябва да правиш нищо.",
    ),
    WelcomePage(
        Icons.Outlined.Swipe, "Разглеждай и избирай",
        "Плъзгай наляво и надясно, за да видиш следващите снимки и историята. „Избери“ слага снимката за тапет, а ♥ я пази в любими.",
    ),
    WelcomePage(
        Icons.Outlined.AutoAwesome, "Твои снимки и ефекти",
        "Добави свои снимки от галерията („Моите снимки“), а в Настройки включи панорама, движение при накланяне или по-четливи икони.",
    ),
    WelcomePage(
        Icons.Outlined.BatteryChargingFull, "Последна стъпка",
        "Разреши работа на заден план, за да не пропускам смяната. После натисни „Започни“ и ще сложа първата снимка.",
    ),
)

/**
 * First start: four short pages that say what the app does; the last one asks for unrestricted
 * battery use and starts. "Пропусни" jumps to the last page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(unrestricted: Boolean, busy: Boolean, onAllow: () -> Unit, onStart: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pages.lastIndex
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            // Touches must not reach the screen below.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(400.dp)) {
                Image(
                    painterResource(R.mipmap.ic_launcher_background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface),
                    ),
                )
                if (pager.currentPage < last) {
                    Surface(
                        onClick = { scope.launch { pager.animateScrollToPage(last) } },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                    ) {
                        Text("Пропусни", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val page = pages[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Стъпка ${i + 1} от ${pages.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(page.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (i == last) {
                        Spacer(Modifier.height(4.dp))
                        if (unrestricted) {
                            Text("✓ Работа на заден план: разрешена", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        } else {
                            OutlinedButton(onClick = onAllow) { Text("Разреши работа на заден план") }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier.height(8.dp).width(if (i == pager.currentPage) 24.dp else 8.dp)
                                .background(
                                    if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
                Button(
                    onClick = { if (pager.currentPage < last) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onStart() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    when {
                        busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        pager.currentPage < last -> Text("Напред", style = MaterialTheme.typography.titleSmall)
                        else -> Text("Започни", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

/** Once after every update: what is new in this version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(version: String, items: List<ReleaseNotes.Item>, onOpenSettings: () -> Unit, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Обновено до версия $version", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Какво ново", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            items.forEach { item ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        if (item.text.isNotBlank()) {
                            Text(item.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f).height(50.dp)) { Text("Настройки") }
                Button(onClick = onDone, modifier = Modifier.weight(1f).height(50.dp)) { Text("Супер") }
            }
        }
    }
}

/**
 * The first opening of the day: date, "Добро утро" and today's picture, for about two seconds.
 * A tap closes it earlier.
 */
@Composable
fun DailyGreeting(greeting: String, date: String, wallpaperLine: String, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 2400, easing = LinearEasing))
        onDone()
    }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.5f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            Modifier.statusBarsPadding().padding(start = 28.dp, end = 28.dp, top = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(date, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
            Text(greeting, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(wallpaperLine, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyLarge)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.width(140.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            TextButton(onClick = onDone) { Text("Докосни, за да продължиш", color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

package bg.dnevnitapet.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bg.dnevnitapet.BuildConfig
import bg.dnevnitapet.model.Category
import bg.dnevnitapet.model.FrequencyMode
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.SourceStatus
import bg.dnevnitapet.model.WallpaperTarget
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.sources.label
import bg.dnevnitapet.util.Power

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st by vm.library.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.text) } }
    val unrestricted = remember(tick) { Power.isUnrestricted(context) }
    val exact = remember(tick) { vm.canScheduleExact() }
    var unusedPause by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(tick) {
        unusedPause = withContext(Dispatchers.IO) { Power.unusedAppPauseActive(context) }
    }
    val cache = remember(tick, st.queue.size, st.history.size, st.favorites.size) { vm.cacheBytes() }
    var showTime by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val update: ((Settings) -> Settings) -> Unit = vm::updateSettings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                SwitchRow("Автоматична смяна", "Сменя тапета сама по график", s.autoChange) { v -> update { it.copy(autoChange = v) } }
            }

            item { Header("Къде да се слага") }
            item {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    RadioRow("Начален екран", s.target == WallpaperTarget.HOME) { update { it.copy(target = WallpaperTarget.HOME) } }
                    RadioRow("Заключен екран", s.target == WallpaperTarget.LOCK) { update { it.copy(target = WallpaperTarget.LOCK) } }
                    RadioRow("И двата", s.target == WallpaperTarget.BOTH) { update { it.copy(target = WallpaperTarget.BOTH) } }
                    if (s.target != WallpaperTarget.HOME) {
                        Hint("Ако ползваш „Динамичен заключен екран“ на Samsung, той ще бъде заменен от тази снимка.")
                    }
                }
            }

            item { Header("Колко често") }
            item {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    RadioRow("Веднъж дневно", s.frequency == FrequencyMode.DAILY) { update { it.copy(frequency = FrequencyMode.DAILY) } }
                    if (s.frequency == FrequencyMode.DAILY) {
                        Row(Modifier.padding(start = 48.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("В ", style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(onClick = { showTime = true }) { Text(Format.minuteOfDay(s.dailyMinuteOfDay)) }
                            Text("  часа", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    RadioRow("На всеки няколко часа", s.frequency == FrequencyMode.INTERVAL) { update { it.copy(frequency = FrequencyMode.INTERVAL) } }
                    if (s.frequency == FrequencyMode.INTERVAL) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 6, 12).forEach { h ->
                                FilterChip(
                                    selected = s.intervalHours == h,
                                    onClick = { update { it.copy(intervalHours = h) } },
                                    label = { Text(if (h == 1) "1 час" else "$h часа") },
                                )
                            }
                        }
                    }
                }
            }

            item { Header("Интернет") }
            item {
                SwitchRow("Само през Wi-Fi", "Снимките се теглят само по Wi-Fi. Готовите снимки работят и без интернет.", s.wifiOnly) { v ->
                    update { it.copy(wifiOnly = v) }
                }
            }

            item { Header("Източници") }
            items(SourceRegistry.all.size) { i ->
                val src = SourceRegistry.all[i]
                SourceRow(
                    name = src.displayName,
                    enabled = src.id in s.enabledSources,
                    configured = src.isConfigured(s),
                    status = st.sourceStatus[src.id],
                ) { v -> update { it.copy(enabledSources = if (v) it.enabledSources + src.id else it.enabledSources - src.id) } }
                if (src.id == "unsplash") {
                    KeyField("Unsplash Access Key", s.unsplashKey, "https://unsplash.com/developers") { k -> update { it.copy(unsplashKey = k) } }
                }
                if (src.id == "pexels") {
                    KeyField("Pexels API key", s.pexelsKey, "https://www.pexels.com/api/") { k -> update { it.copy(pexelsKey = k) } }
                }
            }

            item { Header("Категории") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.forEach { c ->
                            FilterChip(
                                selected = c in s.enabledCategories,
                                onClick = {
                                    update {
                                        val set = if (c in it.enabledCategories) it.enabledCategories - c else it.enabledCategories + c
                                        it.copy(enabledCategories = set.ifEmpty { setOf(c) })
                                    }
                                },
                                label = { Text(c.label) },
                            )
                        }
                    }
                    Hint(
                        "Bing, Spotlight и Wikimedia не посочват категория – тя се разпознава по описанието на " +
                            "снимката и не е 100% точна. Снимки без разпозната категория се показват винаги. " +
                            "NASA е винаги „Космос“; Unsplash и Pexels търсят точно по категория.",
                    )
                }
            }

            item { Header("Надеждна работа на заден план") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusRow(
                        ok = unrestricted,
                        title = if (unrestricted) "Батерия: без ограничения" else "Батерия: оптимизирана",
                        text = if (unrestricted) "Системата няма да спира смяната." else "Samsung може да спре приложението и да пропусне смяната.",
                        action = if (unrestricted) null else ("Разреши" to { Power.requestUnrestricted(context) }),
                    )
                    StatusRow(
                        ok = exact,
                        title = if (exact) "Точни аларми: разрешени" else "Точни аларми: забранени",
                        text = if (exact) "Смяната става точно в избрания час." else "Смяната може да закъснее (наваксва при първа възможност).",
                        action = if (exact) null else ("Разреши" to { Power.openExactAlarmSettings(context) }),
                    )
                    if (unusedPause != null) {
                        StatusRow(
                            ok = unusedPause == false,
                            title = if (unusedPause == false) "Пауза при неизползване: изключена" else "Пауза при неизползване: включена",
                            text = if (unusedPause == false) "Android няма да спре приложението, ако дълго не го отваряш."
                            else "Ако не отваряш приложението няколко месеца, Android може да го спре. Изключи „Пауза на активността, ако не се използва“.",
                            action = if (unusedPause == false) null else ("Изключи" to { Power.openUnusedAppSettings(context) }),
                        )
                    }
                    SamsungSteps(onOpenBattery = { Power.openBatterySettings(context) }, onOpenApp = { Power.openAppDetails(context) })
                    Text("Състояние", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "Последна смяна: ${Format.moment(st.lastChangeAt)}\n" +
                            "Последна автоматична: ${Format.moment(st.lastAutoChangeAt)}\n" +
                            "Следваща планирана: ${vm.nextPlanned()?.let(Format::moment) ?: "—"}\n" +
                            "Готови снимки: ${st.queue.size}  ·  В историята: ${st.history.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showLog = true }) { Text("Дневник на събитията") }
                }
            }

            item { Header("Памет") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Заети: ${Format.megabytes(cache)} (до 5 готови снимки, 30 в историята и любимите).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = vm::prepareNow) { Text("Изтегли нови сега") }
                        TextButton(onClick = vm::clearQueue) { Text("Изчисти готовите") }
                    }
                }
            }

            item { Header("За приложението") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Дневен тапет ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Hint(
                        "Без реклами, акаунти и проследяване. Приложението се свързва само с източниците на снимки. " +
                            "Снимките са собственост на авторите си и се ползват само като личен тапет.",
                    )
                }
            }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(s.dailyMinuteOfDay / 60, s.dailyMinuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Час на смяната") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    update { it.copy(dailyMinuteOfDay = state.hour * 60 + state.minute) }
                    showTime = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отказ") } },
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Дневник") },
            text = {
                LazyColumn(Modifier.height(420.dp)) {
                    val entries = st.log.asReversed()
                    if (entries.isEmpty()) item { Text("Още няма събития.") }
                    items(entries.size) { i ->
                        val e = entries[i]
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(Format.moment(e.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(e.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Затвори") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SourceRow(name: String, enabled: Boolean, configured: Boolean, status: SourceStatus?, onChange: (Boolean) -> Unit) {
    val statusText = when {
        !configured -> "Нужен е безплатен ключ (виж по-долу)"
        !enabled -> "Изключен"
        status == null || (status.lastSuccessAt == 0L && status.lastErrorAt == 0L) -> "Още не е питан"
        status.lastError.isNotBlank() && status.lastErrorAt >= status.lastSuccessAt ->
            "⚠ Грешка (${Format.moment(status.lastErrorAt)}): ${status.lastError}"
        else -> "✓ Работи · ${Format.moment(status.lastSuccessAt)}"
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = configured) { onChange(!enabled) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusText.startsWith("⚠")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled && configured, enabled = configured, onCheckedChange = onChange)
    }
}

@Composable
private fun KeyField(label: String, value: String, helpUrl: String, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.trim() },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl))) } }) {
                Text("Как да взема ключ")
            }
            Spacer(Modifier.weight(1f))
            if (text != value) TextButton(onClick = { onSave(text) }) { Text("Запази") }
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, text: String, action: Pair<String, () -> Unit>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) FilledTonalButton(onClick = action.second) { Text(action.first) }
    }
}

@Composable
private fun SamsungSteps(onOpenBattery: () -> Unit, onOpenApp: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("За Samsung (One UI 6–8)", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. Бутон „Разреши“ горе → „Разреши“. Това слага батерията на „Без ограничения“.\n" +
                    "2. Настройки → Батерия → Ограничения за използване във фонов режим → " +
                    "„Приложения, които никога не заспиват“ → + → Дневен тапет.\n" +
                    "3. В същия екран можеш да изключиш „Приспиване на неизползвани приложения“.\n" +
                    "4. Настройки → Приложения → Дневен тапет → изключи „Пауза на активността, ако не се използва“.\n" +
                    "5. Не натискай „Принудително спиране“ за приложението – това спира и графика " +
                    "(до следващото му отваряне).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Имената на менютата може леко да се различават според версията на One UI.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBattery) { Text("Настройки на батерията") }
                TextButton(onClick = onOpenApp) { Text("За приложението") }
            }
        }
    }
}

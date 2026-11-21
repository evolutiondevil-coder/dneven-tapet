package bg.dnevnitapet.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import bg.dnevnitapet.model.PhotoMode
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
import bg.dnevnitapet.model.ReadableMode
import bg.dnevnitapet.model.Settings
import bg.dnevnitapet.model.SourceStatus
import bg.dnevnitapet.model.WallpaperTarget
import bg.dnevnitapet.sources.SourceRegistry
import bg.dnevnitapet.sources.label
import bg.dnevnitapet.util.Power
import bg.dnevnitapet.wallpaper.Panorama

/** The bottom sheets that hold the details behind a row with ›. */
private enum class Sheet { PHOTOS, FREQUENCY, TARGET, SOURCES, CATEGORIES, NETWORK, RELIABILITY, MEMORY, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenMyPhotos: () -> Unit) {
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
    val dataSaver = remember(tick) { Power.dataSaverBlocksBackground(context) }
    val exact = remember(tick) { vm.canScheduleExact() }
    val liveActive = remember(tick, s.liveOn) { Panorama.isActive(context) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val newVersion by vm.updateAvailable.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val pickPhotos = rememberPhotoPicker(vm)
    val openPanorama = {
        if (!Panorama.openPicker(context)) vm.hint("Телефонът няма екран за живи тапети – ефектите не могат да се включат.")
    }
    val askLive = { if (!Panorama.isActive(context)) openPanorama() }
    var unusedPause by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(tick) {
        unusedPause = withContext(Dispatchers.IO) { Power.unusedAppPauseActive(context) }
    }
    val cache = remember(tick, st.queue.size, st.history.size, st.favorites.size) { vm.cacheBytes() }
    val next = remember(tick, st.lastChangeAt, s) { vm.nextPlanned() }
    var showTime by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val update: ((Settings) -> Settings) -> Unit = vm::updateSettings
    val problems = listOf(!unrestricted, !exact, unusedPause == true, dataSaver && !s.wifiOnly).count { it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ------------------------------------------------ status: automatic change on/off
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Автоматична смяна", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    when {
                                        !s.autoChange -> "Задържан – не се сменя сам"
                                        next != null -> "Следваща смяна: " + Format.moment(next)
                                        else -> "Смяната е включена"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = s.autoChange, onCheckedChange = { v -> update { it.copy(autoChange = v) } })
                        }
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(
                                if (problems == 0) "✓ Работи надеждно" else "⚠ Телефонът може да спира смяната",
                                good = problems == 0,
                            ) { sheet = Sheet.RELIABILITY }
                            StatusChip("Готови снимки: ${st.queue.size}", good = null) { sheet = Sheet.MEMORY }
                        }
                    }
                }
            }

            // ------------------------------------------------ a newer version on GitHub
            newVersion?.let { r ->
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Нова версия ${r.version}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("Настройките и любимите се запазват", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Button(onClick = vm::installUpdate, enabled = updateState != MainViewModel.UpdateState.Downloading) {
                                Text(if (updateState == MainViewModel.UpdateState.Downloading) "Изтеглям…" else "Обнови")
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ pictures
            item {
                Group("Снимки") {
                    LinkRow(Icons.Outlined.Image, Tint.BLUE, "Какви снимки", photoModeLabel(s.photoMode), first = true) { sheet = Sheet.PHOTOS }
                    LinkRow(
                        Icons.Outlined.AddPhotoAlternate, Tint.BLUE, "Моите снимки от телефона",
                        if (st.myPhotos.isEmpty()) "Още няма – добави от галерията" else "${st.myPhotos.size} снимки",
                        onClick = onOpenMyPhotos,
                    )
                    val taste = remember(st.favorites.size, st.blocked.size, s.learnTaste) { if (s.learnTaste) vm.tasteSummary() else "" }
                    ToggleRow(
                        Icons.Outlined.FavoriteBorder, Tint.PINK, "Учи вкуса ми",
                        when {
                            !s.learnTaste -> "Всички източници идват поред"
                            taste.isNotBlank() -> taste
                            else -> "Харесаните (♥) снимки ще идват по-често"
                        },
                        s.learnTaste,
                    ) { v -> update { it.copy(learnTaste = v) } }
                }
            }

            // ------------------------------------------------ change
            item {
                Group("Смяна") {
                    LinkRow(Icons.Outlined.Schedule, Tint.ORANGE, "Колко често", frequencyLabel(s), first = true) { sheet = Sheet.FREQUENCY }
                    LinkRow(Icons.Outlined.Smartphone, Tint.ORANGE, "Къде да се слага", targetLabel(s)) { sheet = Sheet.TARGET }
                }
            }

            // ------------------------------------------------ home screen: panorama and effects
            item {
                Group("Начален екран") {
                    ToggleRow(Icons.Outlined.Panorama, Tint.PURPLE, "Панорама", "Снимката продължава между екраните", s.panorama, first = true) { v ->
                        vm.setPanorama(v, askLive)
                    }
                    if (s.panorama) {
                        ChipLine {
                            listOf(1.5f to "1,5 екрана", 2f to "2 екрана", 3f to "3 екрана").forEach { (w, label) ->
                                FilterChip(selected = s.panoramaScreens == w, enabled = !busy, onClick = { update { it.copy(panoramaScreens = w) } }, label = { Text(label) })
                            }
                        }
                    }
                    ToggleRow(Icons.Outlined.ScreenRotation, Tint.PURPLE, "Движение при накланяне", "Снимката се отмества, когато наклониш телефона", s.tilt) { v ->
                        vm.setLiveOption({ it.copy(tilt = v) }, askLive)
                    }
                    ToggleRow(
                        Icons.Outlined.Visibility, Tint.PURPLE, "По-четливи икони",
                        "Леко затъмнен тапет, за да се виждат иконите", s.readableIcons != ReadableMode.OFF,
                    ) { v -> vm.setLiveOption({ it.copy(readableIcons = if (v) ReadableMode.DIM else ReadableMode.OFF) }, askLive) }
                    if (s.readableIcons != ReadableMode.OFF) {
                        ChipLine {
                            FilterChip(selected = s.readableIcons == ReadableMode.DIM, onClick = { update { it.copy(readableIcons = ReadableMode.DIM) } }, label = { Text("Затъмни") })
                            FilterChip(selected = s.readableIcons == ReadableMode.DIM_BLUR, onClick = { update { it.copy(readableIcons = ReadableMode.DIM_BLUR) } }, label = { Text("Затъмни и размажи") })
                        }
                    }
                    ToggleRow(Icons.Outlined.DarkMode, Tint.PURPLE, "Вечер по-тъмно", "От 19:30 до 07:00, за да не блести", s.eveningDim) { v ->
                        vm.setLiveOption({ it.copy(eveningDim = v) }, askLive)
                    }
                    if (s.panorama || s.tilt || s.readableIcons != ReadableMode.OFF || s.eveningDim) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
                            if (s.target == WallpaperTarget.LOCK) {
                                Hint("Ефектите са за началния екран – в „Къде да се слага“ избери „Начален екран“ или „И двата“.")
                            } else {
                                StatusRow(
                                    ok = liveActive,
                                    title = if (liveActive) "Ефектите работят" else "Остава една стъпка",
                                    text = if (liveActive) "Тапетът се рисува от приложението."
                                    else "„Включи“ → „Задай тапет“ → „Начален екран“. Само веднъж.",
                                    action = if (liveActive) null else ("Включи" to openPanorama),
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ sources
            item {
                Group("Източници") {
                    val names = SourceRegistry.all.filter { it.id in s.enabledSources && it.isConfigured(s) }.map { it.displayName }
                    LinkRow(Icons.Outlined.Public, Tint.TEAL, "Източници на снимки", names.joinToString(", ").ifBlank { "Няма включени" }, first = true) { sheet = Sheet.SOURCES }
                    LinkRow(
                        Icons.AutoMirrored.Outlined.Label, Tint.TEAL, "Категории",
                        if (s.enabledCategories.size == Category.entries.size) "Всички ${Category.entries.size}"
                        else s.enabledCategories.joinToString(", ") { it.label },
                    ) { sheet = Sheet.CATEGORIES }
                    LinkRow(
                        Icons.Outlined.Wifi, Tint.TEAL, "Изтегляне",
                        (if (s.wifiOnly) "Само Wi-Fi" else "Wi-Fi и мобилни данни") + if (dataSaver && !s.wifiOnly) " · ⚠ пестене на данни" else "",
                    ) { sheet = Sheet.NETWORK }
                }
            }

            // ------------------------------------------------ system
            item {
                Group("Система") {
                    LinkRow(
                        Icons.Outlined.Shield, if (problems == 0) Tint.GREEN else Tint.ORANGE, "Надеждна работа на заден план",
                        if (problems == 0) "✓ Всичко е наред" else "⚠ $problems ${if (problems == 1) "нещо за настройка" else "неща за настройка"}",
                        first = true,
                    ) { sheet = Sheet.RELIABILITY }
                    ToggleRow(
                        Icons.Outlined.AutoAwesome, Tint.BLUE, "Поздрав при отваряне",
                        "Снимката се появява плавно с кратка фраза", s.openGreeting,
                    ) { v -> update { it.copy(openGreeting = v) } }
                    LinkRow(Icons.Outlined.Storage, Tint.GRAY, "Памет", "${Format.megabytes(cache)} · готови снимки: ${st.queue.size}") { sheet = Sheet.MEMORY }
                    LinkRow(
                        Icons.Outlined.Info, Tint.GRAY, "За приложението",
                        "Версия ${BuildConfig.VERSION_NAME}" + (newVersion?.let { " · има нова ${it.version}" } ?: ""),
                    ) { sheet = Sheet.ABOUT }
                }
            }
        }
    }

    sheet?.let { which ->
        SettingsSheet(
            title = when (which) {
                Sheet.PHOTOS -> "Какви снимки"
                Sheet.FREQUENCY -> "Колко често"
                Sheet.TARGET -> "Къде да се слага"
                Sheet.SOURCES -> "Източници на снимки"
                Sheet.CATEGORIES -> "Категории"
                Sheet.NETWORK -> "Изтегляне"
                Sheet.RELIABILITY -> "Надеждна работа на заден план"
                Sheet.MEMORY -> "Памет"
                Sheet.ABOUT -> "За приложението"
            },
            onDismiss = { sheet = null },
        ) {
            when (which) {
                Sheet.PHOTOS -> {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoModeCard(s.photoMode, hasPhotos = st.myPhotos.isNotEmpty(), onSelect = vm::setPhotoMode, onAddPhotos = pickPhotos, importing = importing)
                        OutlinedButton(onClick = {
                            sheet = null
                            onOpenMyPhotos()
                        }) { Text("Моите снимки от телефона (${st.myPhotos.size})") }
                    }
                }
                Sheet.FREQUENCY -> {
                    RadioRow("Веднъж дневно", s.frequency == FrequencyMode.DAILY) { update { it.copy(frequency = FrequencyMode.DAILY) } }
                    if (s.frequency == FrequencyMode.DAILY) {
                        Row(Modifier.padding(start = 48.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("В ", style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(onClick = { showTime = true }) { Text(Format.minuteOfDay(s.dailyMinuteOfDay)) }
                            Text("  часа", style = MaterialTheme.typography.bodyLarge)
                        }
                        Text("На всеки:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 48.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(start = 48.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 7, 14).forEach { d ->
                                FilterChip(selected = s.everyDays == d, onClick = { update { it.copy(everyDays = d) } }, label = { Text(if (d == 1) "1 ден" else "$d дни") })
                            }
                        }
                    }
                    RadioRow("На всеки няколко часа", s.frequency == FrequencyMode.INTERVAL) { update { it.copy(frequency = FrequencyMode.INTERVAL) } }
                    if (s.frequency == FrequencyMode.INTERVAL) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 6, 12).forEach { h ->
                                FilterChip(selected = s.intervalHours == h, onClick = { update { it.copy(intervalHours = h) } }, label = { Text(if (h == 1) "1 час" else "$h часа") })
                            }
                        }
                    }
                    Hint("„Задръж“ на главния екран (или ключът „Автоматична смяна“ горе) спира смяната, докато не я пуснеш пак.")
                }
                Sheet.TARGET -> {
                    RadioRow("Начален екран", s.target == WallpaperTarget.HOME) { update { it.copy(target = WallpaperTarget.HOME) } }
                    RadioRow("Заключен екран", s.target == WallpaperTarget.LOCK) { update { it.copy(target = WallpaperTarget.LOCK) } }
                    RadioRow("И двата", s.target == WallpaperTarget.BOTH) { update { it.copy(target = WallpaperTarget.BOTH) } }
                    if (s.target == WallpaperTarget.BOTH) {
                        SwitchRow(
                            "Различни снимки на двата екрана",
                            "При всяка смяна заключеният екран получава друга снимка",
                            s.lockDifferent,
                        ) { v -> update { it.copy(lockDifferent = v) } }
                    }
                    if (s.target != WallpaperTarget.HOME) {
                        Hint("Ако телефонът ти сам сменя заключения екран (напр. „Динамичен заключен екран“ на Samsung или „Карусел“ на Xiaomi), той ще бъде заменен от тази снимка.")
                    }
                }
                Sheet.SOURCES -> {
                    SourceRegistry.all.forEach { src ->
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
                }
                Sheet.CATEGORIES -> {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Hint(
                            "Bing, Spotlight и Wikimedia не посочват категория – тя се разпознава по описанието на " +
                                "снимката и не е 100% точна. Снимки без разпозната категория се показват винаги. " +
                                "NASA е винаги „Космос“; Unsplash и Pexels търсят точно по категория.",
                        )
                    }
                }
                Sheet.NETWORK -> {
                    RadioRow("Wi-Fi и мобилни данни", !s.wifiOnly) { update { it.copy(wifiOnly = false) } }
                    RadioRow("Само Wi-Fi", s.wifiOnly) { update { it.copy(wifiOnly = true) } }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Hint("Изтеглят се по 1–2 MB на снимка. Готовите снимки се сменят и без интернет.")
                        if (dataSaver && !s.wifiOnly) {
                            StatusRow(
                                ok = false,
                                title = "„Пестене на данни“ е включено",
                                text = "Телефонът спира мобилните данни за приложенията на заден план. Разреши ги за Дневен тапет.",
                                action = "Разреши" to { Power.openDataSaverSettings(context) },
                            )
                        }
                    }
                }
                Sheet.RELIABILITY -> {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusRow(
                            ok = unrestricted,
                            title = if (unrestricted) "Батерия: без ограничения" else "Батерия: оптимизирана",
                            text = if (unrestricted) "Системата няма да спира смяната." else "Телефонът може да спре приложението и да пропусне смяната.",
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
                        VendorSteps(
                            onOpenBattery = { Power.openBatterySettings(context) },
                            onOpenAutoStart = { if (!Power.openAutoStartSettings(context)) Power.openAppDetails(context) },
                            onOpenApp = { Power.openAppDetails(context) },
                            onMore = { Power.openUrl(context, "https://dontkillmyapp.com/") },
                        )
                        Text(
                            "Последна смяна: ${Format.moment(st.lastChangeAt)}\n" +
                                "Последна автоматична: ${Format.moment(st.lastAutoChangeAt)}\n" +
                                "Следваща планирана: ${next?.let(Format::moment) ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { showLog = true }) { Text("Дневник на събитията") }
                    }
                }
                Sheet.MEMORY -> {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Заети: ${Format.megabytes(cache)} (до 5 готови снимки, 30 в историята и любимите).\n" +
                                "Готови снимки: ${st.queue.size} · в историята: ${st.history.size} · любими: ${st.favorites.size}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = vm::prepareNow) { Text("Изтегли нови сега") }
                            TextButton(onClick = vm::clearQueue) { Text("Изчисти готовите") }
                        }
                    }
                }
                Sheet.ABOUT -> {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Дневен тапет ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                        Hint(
                            "Без реклами, акаунти и проследяване. Приложението се свързва само с източниците на снимки " +
                                "и с GitHub, за да провери за нова версия. Снимките са собственост на авторите си и се ползват само като личен тапет.",
                        )
                    }
                    SwitchRow(
                        "Проверявай за нова версия",
                        "При всяко отваряне – ако има, приложението пита дали да се обнови",
                        s.checkUpdates,
                    ) { v -> update { it.copy(checkUpdates = v) } }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val r = newVersion
                        if (r != null) {
                            Button(onClick = vm::installUpdate, enabled = updateState != MainViewModel.UpdateState.Downloading) {
                                Text(if (updateState == MainViewModel.UpdateState.Downloading) "Изтеглям…" else "Обнови до ${r.version}")
                            }
                        } else {
                            OutlinedButton(onClick = { vm.checkForUpdate(manual = true) }) { Text("Провери сега") }
                        }
                        TextButton(onClick = { showLog = true }) { Text("Дневник") }
                    }
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

private fun photoModeLabel(mode: PhotoMode) = when (mode) {
    PhotoMode.ONLINE -> "Снимки от интернет"
    PhotoMode.MINE -> "Само моите снимки"
    PhotoMode.MIXED -> "Смесено: мои и от интернет"
}

private fun frequencyLabel(s: Settings): String = when (s.frequency) {
    FrequencyMode.DAILY ->
        (if (s.everyDays == 1) "Всеки ден" else "На всеки ${s.everyDays} дни") + " в " + Format.minuteOfDay(s.dailyMinuteOfDay)
    FrequencyMode.INTERVAL -> if (s.intervalHours == 1) "Всеки час" else "На всеки ${s.intervalHours} часа"
}

private fun targetLabel(s: Settings): String = when (s.target) {
    WallpaperTarget.HOME -> "Начален екран"
    WallpaperTarget.LOCK -> "Заключен екран"
    WallpaperTarget.BOTH -> if (s.lockDifferent) "И двата · различни снимки" else "Начален и заключен екран"
}

/** Colour of a row's icon square. */
private enum class Tint(val color: Color) {
    BLUE(Color(0xFF5B8DEF)),
    PURPLE(Color(0xFF9A7BE0)),
    TEAL(Color(0xFF2FAE98)),
    ORANGE(Color(0xFFE8912D)),
    PINK(Color(0xFFE5566A)),
    GREEN(Color(0xFF3FA65B)),
    GRAY(Color(0xFF8E9099)),
}

/** A titled card holding rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun IconSquare(icon: ImageVector, tint: Tint) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint.color, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun RowTexts(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A row that opens its details (›). */
@Composable
private fun LinkRow(icon: ImageVector, tint: Tint, title: String, subtitle: String, first: Boolean = false, onClick: () -> Unit) {
    if (!first) RowDivider()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquare(icon, tint)
        Spacer(Modifier.width(14.dp))
        RowTexts(title, subtitle, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A row with a switch (the whole row toggles it). */
@Composable
private fun ToggleRow(icon: ImageVector, tint: Tint, title: String, subtitle: String, checked: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    if (!first) RowDivider()
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquare(icon, tint)
        Spacer(Modifier.width(14.dp))
        RowTexts(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Choices that belong to the row above (e.g. the panorama's width). */
@Composable
private fun ChipLine(content: @Composable () -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Small rounded label in the status card; [good] null = neutral. */
@Composable
private fun StatusChip(text: String, good: Boolean?, onClick: () -> Unit) {
    val color = when (good) {
        true -> Color(0xFF3FA65B)
        false -> Color(0xFFE8912D)
        null -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.16f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (good == null) color else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** The details behind a row, as a sheet from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
            content()
        }
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
private fun VendorSteps(onOpenBattery: () -> Unit, onOpenAutoStart: () -> Unit, onOpenApp: () -> Unit, onMore: () -> Unit) {
    val vendor = remember { Power.vendor() }
    var showAll by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("За твоя телефон: ${vendor.label}", style = MaterialTheme.typography.titleSmall)
            Text(Power.steps(vendor), style = MaterialTheme.typography.bodySmall)
            Text(
                "Не натискай „Принудително спиране“ за приложението – това спира и графика до следващото му отваряне. " +
                    "Имената на менютата се различават според марката и версията.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBattery) { Text("Батерия") }
                if (Power.hasAutoStartScreen()) OutlinedButton(onClick = onOpenAutoStart) { Text("Автоматично стартиране") }
                TextButton(onClick = onOpenApp) { Text("За приложението") }
            }
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Скрий другите марки" else "Други марки телефони") }
            if (showAll) {
                Power.Vendor.entries.filter { it != vendor }.forEach { v ->
                    Text(v.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Text(Power.steps(v), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onMore) { Text("Още съвети: dontkillmyapp.com") }
            }
        }
    }
}

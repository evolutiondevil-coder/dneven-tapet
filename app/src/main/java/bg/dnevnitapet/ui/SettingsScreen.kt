package bg.dnevnitapet.ui

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.LockOpen
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import bg.dnevnitapet.i18n.tr

/** The bottom sheets that hold the details behind a row with ›. */
private enum class Sheet { PHOTOS, FREQUENCY, TARGET, RULES, SOURCES, CATEGORIES, NETWORK, RELIABILITY, MEMORY, ABOUT, LANGUAGE }

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
        if (!Panorama.openPicker(context)) vm.hint(tr("Телефонът няма екран за живи тапети – ефектите не могат да се включат."))
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

    // A large title that shrinks into the bar while scrolling (the modern Android look).
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Настройки")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) } },
                scrollBehavior = bar,
                colors = TopAppBarDefaults.largeTopAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                                Text(tr("Автоматична смяна"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    when {
                                        !s.autoChange -> tr("Задържан – не се сменя сам")
                                        next != null -> tr("Следваща смяна: ") + Format.moment(next)
                                        else -> tr("Смяната е включена")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = s.autoChange, colors = choiceSwitchColors(), onCheckedChange = { v -> update { it.copy(autoChange = v) } })
                        }
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(
                                if (problems == 0) tr("✓ Работи надеждно") else tr("⚠ Телефонът може да спира смяната"),
                                good = problems == 0,
                            ) { sheet = Sheet.RELIABILITY }
                            StatusChip(tr("Готови снимки: {0}", st.queue.size), good = null) { sheet = Sheet.MEMORY }
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
                                Text(tr("Нова версия {0}", r.version), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(tr("Настройките и любимите се запазват"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Button(onClick = vm::installUpdate, enabled = updateState != MainViewModel.UpdateState.Downloading) {
                                Text(if (updateState == MainViewModel.UpdateState.Downloading) tr("Изтеглям…") else tr("Обнови"))
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ pictures
            item {
                Group(tr("Снимки")) {
                    LinkRow(Icons.Outlined.Image, Tint.BLUE, tr("Какви снимки"), photoModeLabel(s.photoMode), first = true) { sheet = Sheet.PHOTOS }
                    LinkRow(
                        Icons.Outlined.AddPhotoAlternate, Tint.BLUE, tr("Моите снимки от телефона"),
                        if (st.myPhotos.isEmpty()) tr("Още няма – добави от галерията") else tr("{0} снимки", st.myPhotos.size),
                        onClick = onOpenMyPhotos,
                    )
                    val taste = remember(st.favorites.size, st.blocked.size, s.learnTaste) { if (s.learnTaste) vm.tasteSummary() else "" }
                    ToggleRow(
                        Icons.Outlined.FavoriteBorder, Tint.PINK, tr("Учи вкуса ми"),
                        when {
                            !s.learnTaste -> tr("Всички източници идват поред")
                            taste.isNotBlank() -> taste
                            else -> tr("Харесаните (♥) снимки ще идват по-често")
                        },
                        s.learnTaste,
                    ) { v -> update { it.copy(learnTaste = v) } }
                    ToggleRow(
                        Icons.Outlined.Palette, Tint.PINK, tr("Пауза за изкуство"),
                        tr("На всеки 20 разгледани снимки – известна картина и историята ѝ"),
                        s.artBreaks,
                    ) { v -> update { it.copy(artBreaks = v) } }
                    // Translating the stories is only for Bulgarian (in English they are original).
                    if (bg.dnevnitapet.i18n.I18n.language == bg.dnevnitapet.i18n.AppLanguage.BG) {
                        ToggleRow(
                            Icons.Outlined.Translate, Tint.BLUE, tr("Описания на български"),
                            tr("Заглавията и историите се превеждат на телефона · еднократно ~30 MB при Wi-Fi"),
                            s.translateStories,
                        ) { v -> update { it.copy(translateStories = v) } }
                    }
                }
            }

            // ------------------------------------------------ change
            item {
                Group(tr("Смяна")) {
                    LinkRow(Icons.Outlined.Schedule, Tint.ORANGE, tr("Колко често"), frequencyLabel(s), first = true) { sheet = Sheet.FREQUENCY }
                    LinkRow(Icons.Outlined.Smartphone, Tint.ORANGE, tr("Къде да се слага"), targetLabel(s)) { sheet = Sheet.TARGET }
                    LinkRow(
                        Icons.Outlined.WbSunny, Tint.ORANGE, tr("Според часа, сезона и времето"),
                        listOfNotNull(
                            tr("според часа").takeIf { s.timeRules },
                            tr("сезона").takeIf { s.seasonRules },
                            tr("времето").takeIf { s.weatherRules },
                        ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifBlank { tr("Изключено · по избор") },
                    ) { sheet = Sheet.RULES }
                    ToggleRow(
                        Icons.Outlined.LockOpen, Tint.ORANGE, tr("Нова снимка при всяко отключване"),
                        tr("Друга снимка всеки път, когато събудиш телефона"), s.changeOnUnlock,
                        note = tr("По избор · малко повече батерия"),
                    ) { v -> vm.setLiveOption({ it.copy(changeOnUnlock = v) }, askLive) }
                    ToggleRow(
                        Icons.Outlined.TouchApp, Tint.ORANGE, tr("Двойно докосване на началния екран"),
                        tr("Докосни два пъти празно място – идва следващата снимка"), s.doubleTapNext,
                        note = tr("По избор"),
                    ) { v -> vm.setLiveOption({ it.copy(doubleTapNext = v) }, askLive) }
                    if (s.changeOnUnlock || s.doubleTapNext) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (s.target == WallpaperTarget.LOCK) {
                                Hint(tr("Работи, когато тапетът е и на началния екран – в „Къде да се слага“ избери „Начален екран“ или „И двата“."))
                            } else {
                                StatusRow(
                                    ok = liveActive,
                                    title = if (liveActive) tr("Готово – работи") else tr("Остава една стъпка"),
                                    text = if (liveActive) tr("Снимките се вземат от вече изтеглените – смяната е мигновена и не хаби интернет.")
                                    else tr("„Включи“ → „Задай тапет“ → „Начален екран“. Само веднъж."),
                                    action = if (liveActive) null else (tr("Включи") to openPanorama),
                                )
                            }
                            if (s.changeOnUnlock && android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                                Hint(tr("На Samsung има и вграден „Динамичен заключен екран“ – ползвай само едното от двете."))
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ home screen: panorama and effects
            item {
                Group(tr("Начален екран")) {
                    ToggleRow(Icons.Outlined.Panorama, Tint.PURPLE, tr("Панорама"), tr("Снимката продължава между екраните"), s.panorama, first = true) { v ->
                        vm.setPanorama(v, askLive)
                    }
                    if (s.panorama) {
                        ChipLine {
                            listOf(1.5f to tr("1,5 екрана"), 2f to tr("2 екрана"), 3f to tr("3 екрана")).forEach { (w, label) ->
                                ChoiceChip(selected = s.panoramaScreens == w, enabled = !busy, onClick = { update { it.copy(panoramaScreens = w) } }, label = { Text(label) })
                            }
                        }
                    }
                    ToggleRow(Icons.Outlined.ScreenRotation, Tint.PURPLE, tr("Движение при накланяне"), tr("Снимката се отмества, когато наклониш телефона"), s.tilt) { v ->
                        vm.setLiveOption({ it.copy(tilt = v) }, askLive)
                    }
                    ToggleRow(
                        Icons.Outlined.Visibility, Tint.PURPLE, tr("По-четливи икони"),
                        tr("Леко затъмнен тапет, за да се виждат иконите"), s.readableIcons != ReadableMode.OFF,
                    ) { v -> vm.setLiveOption({ it.copy(readableIcons = if (v) ReadableMode.DIM else ReadableMode.OFF) }, askLive) }
                    if (s.readableIcons != ReadableMode.OFF) {
                        ChipLine {
                            ChoiceChip(selected = s.readableIcons == ReadableMode.DIM, onClick = { update { it.copy(readableIcons = ReadableMode.DIM) } }, label = { Text(tr("Затъмни")) })
                            ChoiceChip(selected = s.readableIcons == ReadableMode.DIM_BLUR, onClick = { update { it.copy(readableIcons = ReadableMode.DIM_BLUR) } }, label = { Text(tr("Затъмни и размажи")) })
                        }
                    }
                    ToggleRow(Icons.Outlined.DarkMode, Tint.PURPLE, tr("Вечер по-тъмно"), tr("От 19:30 до 07:00, за да не блести"), s.eveningDim) { v ->
                        vm.setLiveOption({ it.copy(eveningDim = v) }, askLive)
                    }
                    if (s.panorama || s.tilt || s.readableIcons != ReadableMode.OFF || s.eveningDim) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
                            if (s.target == WallpaperTarget.LOCK) {
                                Hint(tr("Ефектите са за началния екран – в „Къде да се слага“ избери „Начален екран“ или „И двата“."))
                            } else {
                                StatusRow(
                                    ok = liveActive,
                                    title = if (liveActive) tr("Ефектите работят") else tr("Остава една стъпка"),
                                    text = if (liveActive) tr("Тапетът се рисува от приложението.")
                                    else tr("„Включи“ → „Задай тапет“ → „Начален екран“. Само веднъж."),
                                    action = if (liveActive) null else (tr("Включи") to openPanorama),
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ sources
            item {
                Group(tr("Източници")) {
                    val names = SourceRegistry.all.filter { it.id in s.enabledSources && it.isConfigured(s) }.map { it.displayName }
                    LinkRow(Icons.Outlined.Public, Tint.TEAL, tr("Източници на снимки"), names.joinToString(", ").ifBlank { tr("Няма включени") }, first = true) { sheet = Sheet.SOURCES }
                    LinkRow(
                        Icons.AutoMirrored.Outlined.Label, Tint.TEAL, tr("Категории"),
                        if (s.enabledCategories.size == Category.entries.size) tr("Всички {0}", Category.entries.size)
                        else s.enabledCategories.joinToString(", ") { it.label },
                    ) { sheet = Sheet.CATEGORIES }
                    LinkRow(
                        Icons.Outlined.Wifi, Tint.TEAL, tr("Изтегляне"),
                        (if (s.wifiOnly) tr("Само Wi-Fi") else tr("Wi-Fi и мобилни данни")) + if (dataSaver && !s.wifiOnly) tr(" · ⚠ пестене на данни") else "",
                    ) { sheet = Sheet.NETWORK }
                }
            }

            // ------------------------------------------------ system
            item {
                Group(tr("Система")) {
                    LinkRow(
                        Icons.Outlined.Shield, if (problems == 0) Tint.GREEN else Tint.ORANGE, tr("Надеждна работа на заден план"),
                        if (problems == 0) tr("✓ Всичко е наред") else "⚠ $problems ${if (problems == 1) tr("нещо за настройка") else tr("неща за настройка")}",
                        first = true,
                    ) { sheet = Sheet.RELIABILITY }
                    LinkRow(
                        Icons.Outlined.Language, Tint.BLUE, tr("Език") + " · Language", bg.dnevnitapet.i18n.I18n.language.nativeName,
                    ) { sheet = Sheet.LANGUAGE }
                    LinkRow(Icons.Outlined.Storage, Tint.GRAY, tr("Памет"), tr("{0} · готови снимки: {1}", Format.megabytes(cache), st.queue.size)) { sheet = Sheet.MEMORY }
                    LinkRow(
                        Icons.Outlined.Info, Tint.GRAY, tr("За приложението"),
                        tr("Версия {0}", BuildConfig.VERSION_NAME) + (newVersion?.let { tr(" · има нова {0}", it.version) } ?: ""),
                    ) { sheet = Sheet.ABOUT }
                }
            }
        }
    }

    sheet?.let { which ->
        SettingsSheet(
            title = when (which) {
                Sheet.PHOTOS -> tr("Какви снимки")
                Sheet.FREQUENCY -> tr("Колко често")
                Sheet.TARGET -> tr("Къде да се слага")
                Sheet.RULES -> tr("Според часа, сезона и времето")
                Sheet.SOURCES -> tr("Източници на снимки")
                Sheet.CATEGORIES -> tr("Категории")
                Sheet.NETWORK -> tr("Изтегляне")
                Sheet.RELIABILITY -> tr("Надеждна работа на заден план")
                Sheet.MEMORY -> tr("Памет")
                Sheet.ABOUT -> tr("За приложението")
                Sheet.LANGUAGE -> tr("Език") + " · Language"
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
                        }) { Text(tr("Моите снимки от телефона ({0})", st.myPhotos.size)) }
                    }
                }
                Sheet.FREQUENCY -> {
                    RadioRow(tr("Веднъж дневно"), s.frequency == FrequencyMode.DAILY) { update { it.copy(frequency = FrequencyMode.DAILY) } }
                    if (s.frequency == FrequencyMode.DAILY) {
                        Row(Modifier.padding(start = 48.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("В "), style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(onClick = { showTime = true }) { Text(Format.minuteOfDay(s.dailyMinuteOfDay)) }
                            Text(tr("  часа"), style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(tr("На всеки:"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 48.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(start = 48.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 7, 14).forEach { d ->
                                ChoiceChip(selected = s.everyDays == d, onClick = { update { it.copy(everyDays = d) } }, label = { Text(if (d == 1) tr("1 ден") else tr("{0} дни", d)) })
                            }
                        }
                    }
                    RadioRow(tr("На всеки няколко часа"), s.frequency == FrequencyMode.INTERVAL) { update { it.copy(frequency = FrequencyMode.INTERVAL) } }
                    if (s.frequency == FrequencyMode.INTERVAL) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 6, 12).forEach { h ->
                                ChoiceChip(selected = s.intervalHours == h, onClick = { update { it.copy(intervalHours = h) } }, label = { Text(if (h == 1) tr("1 час") else tr("{0} часа", h)) })
                            }
                        }
                    }
                    Hint(tr("„Задръж“ на главния екран (или ключът „Автоматична смяна“ горе) спира смяната, докато не я пуснеш пак."))
                }
                Sheet.TARGET -> {
                    RadioRow(tr("Начален екран"), s.target == WallpaperTarget.HOME) { update { it.copy(target = WallpaperTarget.HOME) } }
                    RadioRow(tr("Заключен екран"), s.target == WallpaperTarget.LOCK) { update { it.copy(target = WallpaperTarget.LOCK) } }
                    RadioRow(tr("И двата"), s.target == WallpaperTarget.BOTH) { update { it.copy(target = WallpaperTarget.BOTH) } }
                    if (s.target == WallpaperTarget.BOTH) {
                        SwitchRow(
                            tr("Различни снимки на двата екрана"),
                            tr("При всяка смяна заключеният екран получава друга снимка"),
                            s.lockDifferent,
                        ) { v -> update { it.copy(lockDifferent = v) } }
                    }
                    if (s.target != WallpaperTarget.HOME) {
                        Hint(tr("Ако телефонът ти сам сменя заключения екран (напр. „Динамичен заключен екран“ на Samsung или „Карусел“ на Xiaomi), той ще бъде заменен от тази снимка."))
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
                            ChoiceChip(
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
                            tr("Bing, Spotlight и Wikimedia не посочват категория – тя се разпознава по описанието на ") +
                                tr("снимката и не е 100% точна. Снимки без разпозната категория се показват винаги. ") +
                                tr("NASA е винаги „Космос“; Unsplash и Pexels търсят точно по категория."),
                        )
                    }
                }
                Sheet.NETWORK -> {
                    RadioRow(tr("Wi-Fi и мобилни данни"), !s.wifiOnly) { update { it.copy(wifiOnly = false) } }
                    RadioRow(tr("Само Wi-Fi"), s.wifiOnly) { update { it.copy(wifiOnly = true) } }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Hint(tr("Изтеглят се по 1–2 MB на снимка. Готовите снимки се сменят и без интернет."))
                        if (dataSaver && !s.wifiOnly) {
                            StatusRow(
                                ok = false,
                                title = tr("„Пестене на данни“ е включено"),
                                text = tr("Телефонът спира мобилните данни за приложенията на заден план. Разреши ги за Дневен тапет."),
                                action = tr("Разреши") to { Power.openDataSaverSettings(context) },
                            )
                        }
                    }
                }
                Sheet.RELIABILITY -> {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusRow(
                            ok = unrestricted,
                            title = if (unrestricted) tr("Батерия: без ограничения") else tr("Батерия: оптимизирана"),
                            text = if (unrestricted) tr("Системата няма да спира смяната.") else tr("Телефонът може да спре приложението и да пропусне смяната."),
                            action = if (unrestricted) null else (tr("Разреши") to { Power.requestUnrestricted(context) }),
                        )
                        StatusRow(
                            ok = exact,
                            title = if (exact) tr("Точни аларми: разрешени") else tr("Точни аларми: забранени"),
                            text = if (exact) tr("Смяната става точно в избрания час.") else tr("Смяната може да закъснее (наваксва при първа възможност)."),
                            action = if (exact) null else (tr("Разреши") to { Power.openExactAlarmSettings(context) }),
                        )
                        if (unusedPause != null) {
                            StatusRow(
                                ok = unusedPause == false,
                                title = if (unusedPause == false) tr("Пауза при неизползване: изключена") else tr("Пауза при неизползване: включена"),
                                text = if (unusedPause == false) tr("Android няма да спре приложението, ако дълго не го отваряш.")
                                else tr("Ако не отваряш приложението няколко месеца, Android може да го спре. Изключи „Пауза на активността, ако не се използва“."),
                                action = if (unusedPause == false) null else (tr("Изключи") to { Power.openUnusedAppSettings(context) }),
                            )
                        }
                        VendorSteps(
                            onOpenBattery = { Power.openBatterySettings(context) },
                            onOpenAutoStart = { if (!Power.openAutoStartSettings(context)) Power.openAppDetails(context) },
                            onOpenApp = { Power.openAppDetails(context) },
                            onMore = { Power.openUrl(context, "https://dontkillmyapp.com/") },
                        )
                        Text(
                            tr("Последна смяна: {0}\n", Format.moment(st.lastChangeAt)) +
                                tr("Последна автоматична: {0}\n", Format.moment(st.lastAutoChangeAt)) +
                                tr("Следваща планирана: {0}", next?.let(Format::moment) ?: "—"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { showLog = true }) { Text(tr("Дневник на събитията")) }
                    }
                }
                Sheet.RULES -> RulesSheetContent(vm, s, update)
                Sheet.LANGUAGE -> {
                    bg.dnevnitapet.i18n.AppLanguage.entries.forEach { lang ->
                        RadioRow(lang.nativeName, lang == bg.dnevnitapet.i18n.I18n.language) {
                            sheet = null
                            context.findActivityForLanguage()?.let { vm.setLanguage(lang, it) }
                        }
                    }
                    Hint(tr("Приложението се рестартира, за да смени езика."))
                }
                Sheet.MEMORY -> {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            tr("Заети: {0} (до 5 готови снимки, 30 в историята и любимите).\n", Format.megabytes(cache)) +
                                tr("Готови снимки: {0} · в историята: {1} · любими: {2}", st.queue.size, st.history.size, st.favorites.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = vm::prepareNow) { Text(tr("Изтегли нови сега")) }
                            TextButton(onClick = vm::clearQueue) { Text(tr("Изчисти готовите")) }
                        }
                    }
                }
                Sheet.ABOUT -> {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(tr("Дневен тапет {0}", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleSmall)
                        Hint(
                            tr("Без реклами, акаунти и проследяване. Приложението се свързва само с източниците на снимки ") +
                                tr("и с GitHub, за да провери за нова версия. Снимките са собственост на авторите си и се ползват само като личен тапет."),
                        )
                    }
                    SwitchRow(
                        tr("Проверявай за нова версия"),
                        tr("Проверява при всяко отваряне и пита, ако има нова"),
                        s.checkUpdates,
                    ) { v -> update { it.copy(checkUpdates = v) } }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val r = newVersion
                        if (r != null) {
                            Button(onClick = vm::installUpdate, enabled = updateState != MainViewModel.UpdateState.Downloading) {
                                Text(if (updateState == MainViewModel.UpdateState.Downloading) tr("Изтеглям…") else tr("Обнови до {0}", r.version))
                            }
                        } else {
                            OutlinedButton(onClick = { vm.checkForUpdate(manual = true) }) { Text(tr("Провери сега")) }
                        }
                        TextButton(onClick = { showLog = true }) { Text(tr("Събития")) }
                    }
                }
            }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(s.dailyMinuteOfDay / 60, s.dailyMinuteOfDay % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(tr("Час на смяната")) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    update { it.copy(dailyMinuteOfDay = state.hour * 60 + state.minute) }
                    showTime = false
                }) { Text(tr("Готово")) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(tr("Отказ")) } },
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(tr("Събития")) },
            text = {
                LazyColumn(Modifier.height(420.dp)) {
                    val entries = st.log.asReversed()
                    if (entries.isEmpty()) item { Text(tr("Още няма събития.")) }
                    items(entries.size) { i ->
                        val e = entries[i]
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(Format.moment(e.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(e.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text(tr("Затвори")) } },
        )
    }
}

private fun photoModeLabel(mode: PhotoMode) = when (mode) {
    PhotoMode.ONLINE -> tr("Снимки от интернет")
    PhotoMode.MINE -> tr("Само моите снимки")
    PhotoMode.MIXED -> tr("Смесено: мои и от интернет")
}

private fun frequencyLabel(s: Settings): String = when (s.frequency) {
    FrequencyMode.DAILY ->
        (if (s.everyDays == 1) tr("Всеки ден") else tr("На всеки {0} дни", s.everyDays)) + tr(" в ") + Format.minuteOfDay(s.dailyMinuteOfDay)
    FrequencyMode.INTERVAL -> if (s.intervalHours == 1) tr("Всеки час") else tr("На всеки {0} часа", s.intervalHours)
}

private fun targetLabel(s: Settings): String = when (s.target) {
    WallpaperTarget.HOME -> tr("Начален екран")
    WallpaperTarget.LOCK -> tr("Заключен екран")
    WallpaperTarget.BOTH -> if (s.lockDifferent) tr("И двата · различни снимки") else tr("Начален и заключен екран")
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
private fun ToggleRow(
    icon: ImageVector,
    tint: Tint,
    title: String,
    subtitle: String,
    checked: Boolean,
    first: Boolean = false,
    note: String? = null,
    onChange: (Boolean) -> Unit,
) {
    if (!first) RowDivider()
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquare(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            RowTexts(title, subtitle, Modifier)
            // "По избор …": options that use a bit more battery or need a permission.
            if (note != null) OptionalNote(note, Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, colors = choiceSwitchColors(), onCheckedChange = null)
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
        Switch(checked = checked, colors = choiceSwitchColors(), onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = choiceRadioColors(), modifier = Modifier.padding(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SourceRow(name: String, enabled: Boolean, configured: Boolean, status: SourceStatus?, onChange: (Boolean) -> Unit) {
    val statusText = when {
        !configured -> tr("Нужен е безплатен ключ (виж по-долу)")
        !enabled -> tr("Изключен")
        status == null || (status.lastSuccessAt == 0L && status.lastErrorAt == 0L) -> tr("Още не е питан")
        status.lastError.isNotBlank() && status.lastErrorAt >= status.lastSuccessAt ->
            tr("⚠ Грешка ({0}): {1}", Format.moment(status.lastErrorAt), status.lastError)
        else -> tr("✓ Работи · {0}", Format.moment(status.lastSuccessAt))
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
        Switch(checked = enabled && configured, enabled = configured, colors = choiceSwitchColors(), onCheckedChange = onChange)
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
                Text(tr("Как да взема ключ"))
            }
            Spacer(Modifier.weight(1f))
            if (text != value) TextButton(onClick = { onSave(text) }) { Text(tr("Запази")) }
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
            Text(tr("За твоя телефон: {0}", vendor.label), style = MaterialTheme.typography.titleSmall)
            Text(Power.steps(vendor), style = MaterialTheme.typography.bodySmall)
            Text(
                tr("Не натискай „Принудително спиране“ за приложението – това спира и графика до следващото му отваряне. ") +
                    tr("Имената на менютата се различават според марката и версията."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBattery) { Text(tr("Батерия")) }
                if (Power.hasAutoStartScreen()) OutlinedButton(onClick = onOpenAutoStart) { Text(tr("Автоматично стартиране")) }
                TextButton(onClick = onOpenApp) { Text(tr("За приложението")) }
            }
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) tr("Скрий другите марки") else tr("Други марки телефони")) }
            if (showAll) {
                Power.Vendor.entries.filter { it != vendor }.forEach { v ->
                    Text(v.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    Text(Power.steps(v), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onMore) { Text(tr("Още съвети: dontkillmyapp.com")) }
            }
        }
    }
}

/** Small orange label under an optional setting ("По избор · малко повече батерия"). */
@Composable
private fun OptionalNote(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = SelectedOrange.copy(alpha = 0.14f), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFF8A4B00),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** "Според часа, сезона и времето": all three optional; the usual picture comes when nothing suits. */
@Composable
private fun RulesSheetContent(vm: MainViewModel, s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Hint(
        tr("Подходяща снимка идва първа при всяка смяна. Ако няма подходяща, идва обикновената. Любимите и скритите се спазват както досега."),
    )
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            ToggleRow(
                Icons.Outlined.Schedule, Tint.BLUE, tr("Снимки според часа"), tr("Сутрин едни, вечер други"),
                s.timeRules, first = true, note = tr("По избор"),
            ) { v -> update { it.copy(timeRules = v) } }
            if (s.timeRules) {
                bg.dnevnitapet.wallpaper.PictureRules.DayPart.entries.forEach { part ->
                    DayPartRow(part, bg.dnevnitapet.wallpaper.PictureRules.categoryFor(s, part)) { c ->
                        update {
                            when (part) {
                                bg.dnevnitapet.wallpaper.PictureRules.DayPart.MORNING -> it.copy(morningCategory = c)
                                bg.dnevnitapet.wallpaper.PictureRules.DayPart.DAY -> it.copy(dayCategory = c)
                                bg.dnevnitapet.wallpaper.PictureRules.DayPart.EVENING -> it.copy(eveningCategory = c)
                                bg.dnevnitapet.wallpaper.PictureRules.DayPart.NIGHT -> it.copy(nightCategory = c)
                            }
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth()) {
            ToggleRow(
                Icons.Outlined.CalendarMonth, Tint.BLUE, tr("Според сезона"), tr("Зиме – сняг и планини, лете – море и плажове"),
                s.seasonRules, first = true, note = tr("По избор"),
            ) { v -> update { it.copy(seasonRules = v) } }
            ToggleRow(
                Icons.Outlined.Thermostat, Tint.BLUE, tr("Според времето навън"), tr("Дъжд – уютни снимки, слънце – ярки, сняг – зимни"),
                s.weatherRules, note = tr("По избор · ползва интернет и твоя град"),
            ) { v -> update { it.copy(weatherRules = v) } }
            if (s.weatherRules) {
                var city by remember { mutableStateOf(s.weatherCity) }
                var now by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(s.weatherCity) {
                    if (s.weatherCity.isNotBlank()) vm.checkWeather(s.weatherCity) { now = it ?: tr("не намирам града") }
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it.take(40) },
                        label = { Text(tr("Твоят град")) },
                        placeholder = { Text(tr("напр. София")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                s.weatherCity.isBlank() -> tr("Напиши града си и натисни „Запази“.")
                                now == null -> tr("Проверявам времето…")
                                else -> tr("Сега в {0}: {1}", s.weatherCity, now)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            now = null
                            update { it.copy(weatherCity = city.trim()) }
                        }, enabled = city.trim() != s.weatherCity) { Text(tr("Запази")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPartRow(part: bg.dnevnitapet.wallpaper.PictureRules.DayPart, category: Category?, onSelect: (Category?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    RowDivider()
    Row(Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(part.label, style = MaterialTheme.typography.bodyLarge)
            Text(part.hours, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            ChoiceChip(selected = category != null, onClick = { menu = true }, label = { Text((category?.label ?: tr("Без значение")) + " ▾", maxLines = 1, softWrap = false) })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Без значение")) }, onClick = {
                    menu = false
                    onSelect(null)
                })
                Category.entries.forEach { c ->
                    DropdownMenuItem(text = { Text(c.label) }, onClick = {
                        menu = false
                        onSelect(c)
                    })
                }
            }
        }
    }
}

private tailrec fun android.content.Context.findActivityForLanguage(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivityForLanguage()
    else -> null
}

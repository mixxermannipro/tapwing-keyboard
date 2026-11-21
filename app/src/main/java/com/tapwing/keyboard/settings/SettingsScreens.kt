package com.tapwing.keyboard.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tapwing.keyboard.BuildConfig
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.CleanupPromptBuilder
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.CloudAiResult
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelKind
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.LocalModelSpec
import com.tapwing.keyboard.ai.ModelInstallState
import com.tapwing.keyboard.voice.VoiceSelfTest
import com.tapwing.keyboard.voice.CloudDictation
import com.tapwing.keyboard.voice.CloudSpeechProvider
import com.tapwing.keyboard.voice.TapwingVoiceController
import com.tapwing.keyboard.voice.WavAudioRecorder
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.ClipboardRetention
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.SplitKeyboardMode
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.data.TypingStats
import com.tapwing.keyboard.data.TypingStatsStore
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.data.VoiceEngineMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import com.tapwing.keyboard.dictionary.OfflinePackSpec
import com.tapwing.keyboard.dictionary.PackInstallState
import com.tapwing.keyboard.ocr.ScanTextActivity
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.isHighlighted
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class SettingsRoute {
    HOME,
    APPEARANCE,
    QUICK_BAR,
    LANGUAGES,
    DICTIONARY,
    CORRECTIONS,
    VOICE,
    WRITING_TOOLS,
    CLIPBOARD,
    EMOJI,
    STATS,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapwingSettingsApp(
    prefs: TapwingPrefs,
    secureKeyStorage: SecureKeyStorage,
    dictionaryStore: AdaptiveDictionaryStore,
    modelManager: LocalModelManager,
    packManager: OfflineDictionaryPackManager,
    statsStore: TypingStatsStore,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backStack = remember { mutableStateListOf(SettingsRoute.HOME) }
    val route = backStack.last()
    var navigatingBack by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var testInputText by remember { mutableStateOf("") }
    var dictRevision by remember { mutableIntStateOf(0) }

    var imeEnabled by remember { mutableStateOf(false) }
    var imeSelected by remember { mutableStateOf(false) }

    fun navigateTo(target: SettingsRoute) {
        if (route != target) {
            navigatingBack = false
            backStack.add(target)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            navigatingBack = true
            backStack.removeAt(backStack.lastIndex)
        } else if (route == SettingsRoute.HOME && searchQuery.isNotEmpty()) {
            searchQuery = ""
        }
    }

    BackHandler(
        enabled = backStack.size > 1 || (route == SettingsRoute.HOME && searchQuery.isNotEmpty())
    ) {
        navigateBack()
    }

    fun refreshImeStatus() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val pkg = context.packageName
        imeEnabled = imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
        val defaultIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ).orEmpty()
        imeSelected = defaultIme.startsWith("$pkg/")
    }

    DisposableEffect(lifecycleOwner) {
        refreshImeStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
                dictionaryStore.reloadIfModifiedOnDisk()
                statsStore.reloadIfModifiedOnDisk()
                packManager.refreshStates()
                dictRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val effectiveLang = if (prefs.appUiLocale == "system") {
        KeyboardLocales.baseLang(java.util.Locale.getDefault().language)
    } else {
        KeyboardLocales.baseLang(prefs.appUiLocale)
    }
    val de = effectiveLang == "de"

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (route != SettingsRoute.HOME) {
                val title = when (route) {
                    SettingsRoute.HOME -> ""
                    SettingsRoute.APPEARANCE -> stringResource(R.string.nav_appearance)
                    SettingsRoute.QUICK_BAR -> stringResource(R.string.nav_quick_bar)
                    SettingsRoute.LANGUAGES -> stringResource(R.string.nav_languages)
                    SettingsRoute.DICTIONARY -> stringResource(R.string.nav_dictionary)
                    SettingsRoute.CORRECTIONS -> stringResource(R.string.nav_corrections)
                    SettingsRoute.VOICE -> stringResource(R.string.nav_voice)
                    SettingsRoute.WRITING_TOOLS -> stringResource(R.string.nav_writing_tools)
                    SettingsRoute.CLIPBOARD -> stringResource(R.string.nav_clipboard)
                    SettingsRoute.EMOJI -> stringResource(R.string.nav_emoji)
                    SettingsRoute.STATS -> if (de) "Tastatur-Statistik" else "Typing Statistics"
                    SettingsRoute.ABOUT -> stringResource(R.string.nav_about)
                }
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                    ),
                    title = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { navigateBack() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = if (de) "Zurück" else "Back",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (de) "Zurück" else "Back",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val liveStats by statsStore.stats.collectAsState()
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    val forward = !navigatingBack
                    val slideIn = slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { width -> if (forward) width / 3 else -width / 3 } + fadeIn(tween(170))
                    val slideOut = slideOutHorizontally(tween(140)) { width ->
                        if (forward) -width / 5 else width / 5
                    } + fadeOut(tween(120))
                    slideIn togetherWith slideOut
                },
                label = "settingsNavigation"
            ) { activeRoute ->
                when (activeRoute) {
                    SettingsRoute.HOME -> HomeSettingsScreen(
                        prefs = prefs,
                        de = de,
                        imeEnabled = imeEnabled,
                        imeSelected = imeSelected,


                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        testInputText = testInputText,
                        onTestInputChange = { testInputText = it },
                        onOpenImeSystemSettings = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onShowImePicker = {
                            val imm = context.getSystemService(InputMethodManager::class.java)
                            imm?.showInputMethodPicker()
                        },
                        onToggleAppleMode = { enabled ->
                            onUpdatePrefs { it.copy(appleModeEnabled = enabled) }
                        },
                        onNavigate = { navigateTo(it) }
                    )
                    SettingsRoute.APPEARANCE -> AppearanceScreen(prefs, de, onUpdatePrefs)
                    SettingsRoute.QUICK_BAR -> QuickBarSettingsScreen(prefs, de, onUpdatePrefs)
                    SettingsRoute.LANGUAGES -> LanguagesScreen(
                        prefs = prefs,
                        de = de,
                        onOpenImeSystemSettings = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onShowImePicker = {
                            val imm = context.getSystemService(InputMethodManager::class.java)
                            imm?.showInputMethodPicker()
                        },
                        onUpdatePrefs = onUpdatePrefs
                    )
                    SettingsRoute.DICTIONARY -> DictionaryLearningScreen(
                        prefs = prefs,
                        de = de,
                        dictionaryStore = dictionaryStore,
                        packManager = packManager,
                        onDictionaryChanged = { dictRevision++ },
                        onUpdatePrefs = onUpdatePrefs
                    )
                    SettingsRoute.CORRECTIONS -> CorrectionsScreen(
                        prefs = prefs,
                        de = de,
                        onOpenQuickBar = { navigateTo(SettingsRoute.QUICK_BAR) },
                        onUpdatePrefs = onUpdatePrefs
                    )
                    SettingsRoute.VOICE -> VoiceSettingsScreen(
                        prefs, de, modelManager, secureKeyStorage,
                        onOpenWritingTools = { navigateTo(SettingsRoute.WRITING_TOOLS) },
                        onUpdatePrefs = onUpdatePrefs
                    )
                    SettingsRoute.WRITING_TOOLS -> WritingToolsSettingsScreen(prefs, de, secureKeyStorage, modelManager, onUpdatePrefs)
                    SettingsRoute.CLIPBOARD -> ClipboardSettingsScreen(prefs, de, onUpdatePrefs)
                    SettingsRoute.EMOJI -> EmojiSettingsScreen(prefs, de, onUpdatePrefs)
                    SettingsRoute.STATS -> TypingStatsScreen(
                        stats = liveStats,
                        de = de,
                        onReset = { statsStore.reset() }
                    )
                    SettingsRoute.ABOUT -> AboutSettingsScreen(
                        de = de,
                        onOpenImeSystemSettings = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onShowImePicker = {
                            val imm = context.getSystemService(InputMethodManager::class.java)
                            imm?.showInputMethodPicker()
                        }
                    )
                }
            }
        }
    }
}

private sealed interface HomeRowItem {
    val keywords: String
    val title: String
}

private data class HomeNavItem(
    val route: SettingsRoute,
    override val title: String,
    val icon: ImageVector,
    val badgeColor: Color,
    override val keywords: String
) : HomeRowItem

private data class HomeToggleItem(
    override val title: String,
    val checked: Boolean,
    val icon: ImageVector,
    val badgeColor: Color,
    override val keywords: String,
    val onCheckedChange: (Boolean) -> Unit
) : HomeRowItem

@Composable
private fun HomeSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onToggleAppleMode: (Boolean) -> Unit,
    onNavigate: (SettingsRoute) -> Unit
) {
    val appearanceItems: List<HomeRowItem> = listOf(
        HomeToggleItem(
            title = if (de) "Apple-Stil" else "Apple style",
            checked = prefs.appleModeEnabled,
            icon = Icons.Rounded.Smartphone,
            badgeColor = Color(0xFF5E5CE6),
            keywords = "apple liquid glass design glas",
            onCheckedChange = onToggleAppleMode
        ),
        HomeNavItem(
            route = SettingsRoute.APPEARANCE,
            title = stringResource(R.string.nav_appearance),
            icon = Icons.Rounded.Palette,
            badgeColor = Color(0xFFAF52DE),
            keywords = "theme design farbe größe höhe abstand einhand"
        ),
        HomeNavItem(
            route = SettingsRoute.QUICK_BAR,
            title = stringResource(R.string.nav_quick_bar),
            icon = Icons.Rounded.Tune,
            badgeColor = Color(0xFF5AC8FA),
            keywords = "leiste schnellleiste toolbar symbole icons reihenfolge"
        ),
        HomeNavItem(
            route = SettingsRoute.LANGUAGES,
            title = stringResource(R.string.nav_languages),
            icon = Icons.Rounded.Language,
            badgeColor = Color(0xFF007AFF),
            keywords = "sprache qwertz qwerty azerty zahlenreihe umlaute leertaste land"
        )
    )

    val intelligenceItems: List<HomeRowItem> = listOf(
        HomeNavItem(
            route = SettingsRoute.DICTIONARY,
            title = stringResource(R.string.nav_dictionary),
            icon = Icons.Rounded.Book,
            badgeColor = Color(0xFF34C759),
            keywords = "wörterbuch lernen zip gboard pixelboard kürzel import export"
        ),
        HomeNavItem(
            route = SettingsRoute.CORRECTIONS,
            title = stringResource(R.string.nav_corrections),
            icon = Icons.Rounded.Spellcheck,
            badgeColor = Color(0xFF30B0C7),
            keywords = "autokorrektur rechner math inkognito undo grammatik swipe wischen"
        ),
        HomeNavItem(
            route = SettingsRoute.VOICE,
            title = stringResource(R.string.nav_voice),
            icon = Icons.Rounded.Mic,
            badgeColor = Color(0xFFFF9500),
            keywords = "sprache diktat rambler whisper modell lokal mikrofon prompt"
        ),
        HomeNavItem(
            route = SettingsRoute.WRITING_TOOLS,
            title = stringResource(R.string.nav_writing_tools),
            icon = Icons.Rounded.AutoAwesome,
            badgeColor = Color(0xFF5856D6),
            keywords = "ki ai gemini openai qwen gemma llama gguf modell prompt"
        )
    )

    val toolsItems: List<HomeRowItem> = listOf(
        HomeNavItem(
            route = SettingsRoute.CLIPBOARD,
            title = stringResource(R.string.nav_clipboard),
            icon = Icons.Rounded.ContentPaste,
            badgeColor = Color(0xFF32ADE6),
            keywords = "zwischenablage clipboard ocr kamera scan screenshot"
        ),
        HomeNavItem(
            route = SettingsRoute.EMOJI,
            title = stringResource(R.string.nav_emoji),
            icon = Icons.Rounded.EmojiEmotions,
            badgeColor = Color(0xFFFF9F0A),
            keywords = "emoji größe vorschläge"
        ),
        HomeNavItem(
            route = SettingsRoute.STATS,
            title = if (de) "Tastatur-Statistik" else "Typing Statistics",
            icon = Icons.Rounded.BarChart,
            badgeColor = Color(0xFF34C759),
            keywords = "statistik wpm geschwindigkeit wörter gespart zeichen swipe"
        ),
        HomeNavItem(
            route = SettingsRoute.ABOUT,
            title = stringResource(R.string.nav_about),
            icon = Icons.Rounded.Info,
            badgeColor = Color(0xFF8E8E93),
            keywords = "über info version datenschutz lizenz system"
        )
    )

    val rows = intelligenceItems + toolsItems + appearanceItems
    val primaryRoutes = listOf(
        SettingsRoute.VOICE, SettingsRoute.WRITING_TOOLS, SettingsRoute.CLIPBOARD,
        SettingsRoute.APPEARANCE, SettingsRoute.LANGUAGES, SettingsRoute.CORRECTIONS
    )
    val primary = primaryRoutes.map { route -> rows.filterIsInstance<HomeNavItem>().first { it.route == route } }
    val extra = rows.filterNot { it in primary }
    val q = searchQuery.trim().lowercase()
    var more by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tapwing",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                AppleSearchPill(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.settings_search_placeholder)
                )
            }
        }

        // Only surface setup banner when Tapwing is not yet enabled or selected
        if (!imeEnabled || !imeSelected) {
            item {
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (!imeEnabled) {
                                    stringResource(R.string.setup_enable_tapwing)
                                } else {
                                    stringResource(R.string.setup_select_tapwing)
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onOpenImeSystemSettings,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "1. Aktivieren" else "1. Enable", fontSize = 13.sp)
                            }
                            Button(
                                onClick = onShowImePicker,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "2. Auswählen" else "2. Select", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Compact 1-line live test input
        if (q.isEmpty()) {
            item {
                AppleGroupedSurface {
                    AppleInlineField(
                        value = testInputText,
                        onValueChange = onTestInputChange,
                        placeholder = stringResource(R.string.settings_test_placeholder),
                        leadingIcon = Icons.Rounded.Keyboard,
                        containerAlpha = 0f,
                        fieldHeight = 46.dp
                    )
                }
            }
        }

        val visibleRows = if (q.isEmpty()) primary else rows.filter {
            it.title.lowercase().contains(q) || it.keywords.contains(q)
        }
        if (visibleRows.isNotEmpty()) {
            item { HomeSettingsRows(visibleRows, onNavigate) }
        } else {
            item {
                AppleSectionFooter(if (de) "Keine passenden Einstellungen gefunden." else "No matching settings.")
            }
        }

        if (q.isEmpty()) {
            item {
                AppleGroupedSurface {
                    SettingsDisclosure(if (de) "Weitere Optionen" else "More options", more) { more = !more }
                }
            }
            if (more) item { HomeSettingsRows(extra, onNavigate) }
        }
    }
}

@Composable
private fun HomeSettingsRows(rows: List<HomeRowItem>, onNavigate: (SettingsRoute) -> Unit) {
    AppleGroupedSurface {
        rows.forEachIndexed { index, item ->
            if (index > 0) AppleHairlineDivider(startIndent = 58.dp)
            when (item) {
                is HomeToggleItem -> MinimalToggleRow(item.title, item.checked, item.icon, item.badgeColor, item.onCheckedChange)
                is HomeNavItem -> MinimalNavRow(item.icon, item.badgeColor, item.title, "") { onNavigate(item.route) }
            }
        }
    }
}

@Composable
private fun AppearanceScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Darstellung" else "Experience")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Apple Modus (Liquid Glass)" else "Apple Mode (Liquid Glass)",
                        checked = prefs.appleModeEnabled,
                        onCheckedChange = { v -> onUpdatePrefs { it.copy(appleModeEnabled = v) } }
                    )
                }
                AppleSectionFooter(
                    if (de) "Durchscheinende Glastasten, Feder-Animationen und ruhige iOS-Oberfläche."
                    else "Translucent glass keycaps, spring animations, and calm iOS-style UI."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(
                    text = if (de) "Größe & Abstand" else "Size & Timing",
                    trailingActionText = "Reset",
                    onTrailingAction = {
                        onUpdatePrefs { it.copy(keyboardScalePercent = 100, keyboardBottomLiftDp = 0) }
                    }
                )
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Höhe" else "Height", fontSize = 15.sp)
                            Text("${prefs.keyboardScalePercent}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.keyboardScalePercent.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(keyboardScalePercent = v.roundToInt()) } },
                            valueRange = 75f..135f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Abstand unten" else "Bottom offset", fontSize = 15.sp)
                            Text("${prefs.keyboardBottomLiftDp} dp", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.keyboardBottomLiftDp.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(keyboardBottomLiftDp = v.roundToInt()) } },
                            valueRange = 0f..48f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Haltedauer" else "Long-press delay", fontSize = 15.sp)
                            Text("${prefs.longPressDelayMs} ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.longPressDelayMs.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(longPressDelayMs = v.roundToInt()) } },
                            valueRange = 150f..500f
                        )
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Farbschema" else "Theme")
                AppleGroupedSurface {
                    val modes = listOf(
                        ThemeMode.PIXEL_SLATE to (if (de) "Pixel Slate (1:1 PixelBoard)" else "Pixel Slate"),
                        ThemeMode.EXPRESSIVE_LAVENDER to (if (de) "Expressive Lavendel" else "Lavender"),
                        ThemeMode.DYNAMIC to "Material You",
                        ThemeMode.PIXEL_BLUE to "Pixel Blue",
                        ThemeMode.AMOLED_DARK to "AMOLED Pure Dark"
                    )
                    modes.forEachIndexed { idx, (mode, label) ->
                        MinimalCheckRow(label, selected = prefs.themeMode == mode) {
                            onUpdatePrefs { it.copy(themeMode = mode) }
                        }
                        if (idx < modes.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Erscheinungsbild" else "Appearance")
                AppleGroupedSurface {
                    val darkOptions = listOf(
                        DarkModePreference.SYSTEM to (if (de) "Automatisch (System)" else "Automatic"),
                        DarkModePreference.LIGHT to (if (de) "Hell" else "Light"),
                        DarkModePreference.DARK to (if (de) "Dunkel" else "Dark")
                    )
                    darkOptions.forEachIndexed { idx, (mode, label) ->
                        MinimalCheckRow(label, selected = prefs.darkMode == mode) {
                            onUpdatePrefs { it.copy(darkMode = mode) }
                        }
                        if (idx < darkOptions.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Tasten & Einhandmodus" else "Keycaps & One-Handed")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Abgerundete Pill-Tasten" else "Capsule keycaps",
                        checked = prefs.roundedExpressiveKeys
                    ) { v -> onUpdatePrefs { it.copy(roundedExpressiveKeys = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Tasten-Umrandung" else "Key borders",
                        checked = prefs.showKeyBorders
                    ) { v -> onUpdatePrefs { it.copy(showKeyBorders = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Haptisches Feedback" else "Haptic feedback",
                        checked = prefs.hapticFeedback
                    ) { v -> onUpdatePrefs { it.copy(hapticFeedback = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Tastenklick-Ton" else "Sound on keypress",
                        checked = prefs.soundOnKeypress
                    ) { v -> onUpdatePrefs { it.copy(soundOnKeypress = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    OneHandedMode.entries.forEachIndexed { idx, mode ->
                        val label = when (mode) {
                            OneHandedMode.OFF -> if (de) "Einhandmodus: Aus" else "One-handed: Off"
                            OneHandedMode.RIGHT -> if (de) "Einhandmodus: Rechts" else "One-handed: Right"
                            OneHandedMode.LEFT -> if (de) "Einhandmodus: Links" else "One-handed: Left"
                        }
                        MinimalCheckRow(label, selected = prefs.oneHandedMode == mode) {
                            onUpdatePrefs { it.copy(oneHandedMode = mode) }
                        }
                        if (idx < OneHandedMode.entries.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Geteilte Tastatur (Tablet & Foldable)" else "Split Keyboard (Tablet & Foldable)")
                AppleGroupedSurface {
                    SplitKeyboardMode.entries.forEachIndexed { idx, mode ->
                        val label = when (mode) {
                            SplitKeyboardMode.OFF -> if (de) "Geteiltes Layout: Aus" else "Split layout: Off"
                            SplitKeyboardMode.AUTO -> if (de) "Automatisch (ab 600dp / Falt-Displays)" else "Auto (tablets & foldables ≥ 600dp)"
                            SplitKeyboardMode.ALWAYS -> if (de) "Immer geteilt anzeigen" else "Always split"
                        }
                        MinimalCheckRow(label, selected = prefs.splitKeyboardMode == mode) {
                            onUpdatePrefs { it.copy(splitKeyboardMode = mode) }
                        }
                        if (idx < SplitKeyboardMode.entries.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
                AppleSectionFooter(
                    if (de) "Teilt die Tastenreihen ergonomisch für Daumen auf Tablets, Foldables oder im Querformat."
                    else "Splits key rows ergonomically for thumbs on tablets, foldables, or landscape screens."
                )
            }
        }
    }
}

@Composable
private fun QuickBarSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val rowStepPx = remember(density) { with(density) { 48.dp.toPx() } }

    val actions = prefs.quickBarActions
    val currentActions by rememberUpdatedState(actions)
    val availableActions = remember(actions) {
        QuickBarAction.entries.filterNot { it in actions }
    }
    var draggedAction by remember { mutableStateOf<QuickBarAction?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Vorschau" else "Preview")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        if (actions.isEmpty()) {
                            Text(
                                text = if (de) "Leer" else "Empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                actions.forEach { action ->
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (action.isHighlighted(prefs)) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = action.icon(),
                                            contentDescription = action.title(de),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(
                    if (de) "Aktiv (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})"
                    else "Pinned (${actions.size}/${QuickBarAction.MAX_PINNED_ACTIONS})"
                )
                AppleGroupedSurface {
                    if (actions.isEmpty()) {
                        Text(
                            text = if (de) "Keine Symbole angeheftet" else "No pinned icons",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        actions.forEachIndexed { index, action ->
                            key(action) {
                                val isDragged = draggedAction == action
                                val canMoveUp = index > 0
                                val canMoveDown = index < actions.lastIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragged) dragOffsetY else 0f
                                        }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .pointerInput(action, rowStepPx) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedAction = action
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggedAction = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedAction = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { _, dragAmount ->
                                                        dragOffsetY += dragAmount.y
                                                        val list = currentActions
                                                        val curIdx = list.indexOf(action)
                                                        if (curIdx >= 0) {
                                                            if (dragOffsetY > rowStepPx && curIdx < list.lastIndex) {
                                                                onUpdatePrefs {
                                                                    it.copy(
                                                                        quickBarActions = QuickBarAction.move(
                                                                            list,
                                                                            curIdx,
                                                                            curIdx + 1
                                                                        )
                                                                    )
                                                                }
                                                                dragOffsetY -= rowStepPx
                                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                            } else if (dragOffsetY < -rowStepPx && curIdx > 0) {
                                                                onUpdatePrefs {
                                                                    it.copy(
                                                                        quickBarActions = QuickBarAction.move(
                                                                            list,
                                                                            curIdx,
                                                                            curIdx - 1
                                                                        )
                                                                    )
                                                                }
                                                                dragOffsetY += rowStepPx
                                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = if (de) "Verschieben" else "Reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = action.icon(),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )

                                    Text(
                                        text = action.title(de),
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            onUpdatePrefs {
                                                it.copy(quickBarActions = QuickBarAction.move(it.quickBarActions, index, index - 1))
                                            }
                                        },
                                        enabled = canMoveUp,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = if (de) "${action.title(de)} hoch" else "Move ${action.title(de)} up",
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdatePrefs {
                                                it.copy(quickBarActions = QuickBarAction.move(it.quickBarActions, index, index + 1))
                                            }
                                        },
                                        enabled = canMoveDown,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDownward,
                                            contentDescription = if (de) "${action.title(de)} runter" else "Move ${action.title(de)} down",
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onUpdatePrefs {
                                                it.copy(quickBarActions = QuickBarAction.remove(it.quickBarActions, action))
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = if (de) "${action.title(de)} entfernen" else "Remove ${action.title(de)}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                            if (index < actions.lastIndex) {
                                AppleHairlineDivider(startIndent = 44.dp)
                            }
                        }
                    }
                }
            }
        }

        if (availableActions.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Weitere Werkzeuge" else "Available tools")
                    AppleGroupedSurface {
                        val canAddMore = actions.size < QuickBarAction.MAX_PINNED_ACTIONS
                        availableActions.forEachIndexed { idx, action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable(enabled = canAddMore) {
                                        onUpdatePrefs {
                                            it.copy(quickBarActions = QuickBarAction.add(it.quickBarActions, action))
                                        }
                                    }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = action.icon(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = action.title(de),
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = if (de) "${action.title(de)} hinzufügen" else "Add ${action.title(de)}",
                                    tint = if (canAddMore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (idx < availableActions.lastIndex) {
                                AppleHairlineDivider(startIndent = 48.dp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column {
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Standard-5 wiederherstellen" else "Restore default 5",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        onUpdatePrefs { it.copy(quickBarActions = QuickBarAction.DEFAULT_ACTIONS) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Am Griff (≡) ziehen oder direkt auf ein Symbol in der Tastatur-Leiste lange drücken."
                    else "Drag handle (≡) to reorder, or long-press any icon on the keyboard strip."
                )
            }
        }
    }
}

@Composable
private fun LanguagesScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Aktive Tastatur-Sprache" else "Active keyboard language")
                AppleGroupedSurface {
                    val activeCandidates = KeyboardLocales.allTypingLocales.filter { it.code in prefs.enabledLocales }
                        .ifEmpty { listOf(KeyboardLocales.allTypingLocales.first()) }
                    activeCandidates.forEachIndexed { idx, spec ->
                        MinimalCheckRow(
                            title = "${spec.nativeLabel} (${spec.layoutLabel})",
                            selected = prefs.activeLocale == spec.code
                        ) {
                            onUpdatePrefs { it.copy(activeLocale = spec.code) }
                        }
                        if (idx < activeCandidates.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Aktivierte Sprachen & Länder" else "Enabled languages & countries")
                AppleGroupedSurface {
                    KeyboardLocales.allTypingLocales.forEachIndexed { idx, spec ->
                        val enabled = spec.code in prefs.enabledLocales
                        MinimalToggleRow(
                            title = "${spec.nativeLabel} · ${spec.layoutLabel}",
                            checked = enabled
                        ) { checked ->
                            onUpdatePrefs { current ->
                                val next = if (checked) {
                                    (current.enabledLocales + spec.code).distinct()
                                } else {
                                    val filtered = current.enabledLocales.filterNot { it == spec.code }
                                    if (filtered.isEmpty()) current.enabledLocales else filtered
                                }
                                val nextActive = if (current.activeLocale in next) current.activeLocale else next.first()
                                current.copy(enabledLocales = next, activeLocale = nextActive)
                            }
                        }
                        if (idx < KeyboardLocales.allTypingLocales.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
                AppleSectionFooter(
                    if (de) "Die Globus-Taste und Leertaste (gedrückt halten) wechseln zwischen deinen aktivierten Sprachen."
                    else "The globe key and spacebar long-press cycle through your enabled languages."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "App-Sprache (Menü)" else "App UI language")
                AppleGroupedSurface {
                    KeyboardLocales.appUiLanguages.forEachIndexed { idx, lang ->
                        val label = if (lang.code == "system") {
                            if (de) "Systemsprache (${KeyboardLocales.systemLanguageDisplayName()})"
                            else "System (${KeyboardLocales.systemLanguageDisplayName()})"
                        } else {
                            lang.nativeLabel
                        }
                        MinimalCheckRow(
                            title = label,
                            selected = prefs.appUiLocale == lang.code
                        ) {
                            onUpdatePrefs { it.copy(appUiLocale = lang.code) }
                        }
                        if (idx < KeyboardLocales.appUiLanguages.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Tastenbelegung & Mehrsprachigkeit" else "Layout & Multilingual")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Mehrsprachige Vorschläge" else "Multilingual predictions",
                        checked = prefs.multilingualPredictions
                    ) { v -> onUpdatePrefs { it.copy(multilingualPredictions = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Zahlenreihe anzeigen" else "Number row",
                        checked = prefs.showNumberRow
                    ) { v -> onUpdatePrefs { it.copy(showNumberRow = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Direkte Umlaut-Tasten (Ä, Ö, Ü)" else "Direct German umlauts",
                        checked = prefs.showGermanUmlautKeys
                    ) { v -> onUpdatePrefs { it.copy(showGermanUmlautKeys = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        title = if (de) "Sprache auf Leertaste" else "Language label on spacebar",
                        checked = prefs.showSpacebarLanguageLabel
                    ) { v -> onUpdatePrefs { it.copy(showSpacebarLanguageLabel = v) } }
                }
                AppleSectionFooter(
                    if (de) "Mehrsprachige Vorschläge erkennen Wörter aus deinen anderen aktivierten Sprachen ohne manuelles Umschalten."
                    else "Multilingual predictions recognize words from your other enabled languages without manual switching."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader("System")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Android Tastatur-Einstellungen" else "Android keyboard settings",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenImeSystemSettings
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Standard-Tastatur wählen" else "Choose input method",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onShowImePicker
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryLearningScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    dictionaryStore: AdaptiveDictionaryStore,
    packManager: OfflineDictionaryPackManager,
    onDictionaryChanged: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packStates by packManager.states.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var newWordInput by remember { mutableStateOf("") }
    var newShortcutTrigger by remember { mutableStateOf("") }
    var newShortcutExpansion by remember { mutableStateOf("") }
    var newSnippetTrigger by remember { mutableStateOf("") }
    var newSnippetTitle by remember { mutableStateOf("") }
    var newSnippetBody by remember { mutableStateOf("") }
    var profileName by remember(prefs.snippetUserName) { mutableStateOf(prefs.snippetUserName) }
    var profileEmail by remember(prefs.snippetUserEmail) { mutableStateOf(prefs.snippetUserEmail) }

    val stats = remember(revision) { dictionaryStore.stats() }
    val customWords = remember(revision) { dictionaryStore.listCustomWords().take(25) }
    val learnedWords = remember(revision) { dictionaryStore.listTopLearnedWords(12) }
    val rejectedWords = remember(revision) { dictionaryStore.listRejectedCorrections().take(12) }
    val shortcuts = remember(revision) { dictionaryStore.listShortcuts().take(20) }
    val snippets = remember(revision) { dictionaryStore.listSnippets() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            dictionaryStore.importStream(stream)
                        } ?: throw IllegalStateException("Datei konnte nicht gelesen werden")
                    }
                }.onSuccess { result ->
                    revision++
                    onDictionaryChanged()
                    val wordTextDe = if (result.addedWords == 1) "1 neues Wort" else "${result.addedWords} neue Wörter"
                    val wordTextEn = if (result.addedWords == 1) "1 word" else "${result.addedWords} words"
                    val shortcutTextEn = if (result.addedShortcuts == 1) "1 shortcut" else "${result.addedShortcuts} shortcuts"
                    statusMessage = when (result.sourceFormat) {
                        "gboard_zip" -> if (de) {
                            "PixelBoard/Gboard .zip importiert: $wordTextDe, ${result.addedShortcuts} Kürzel."
                        } else {
                            "PixelBoard/Gboard .zip imported: $wordTextEn and $shortcutTextEn."
                        }
                        "gboard_tsv" -> if (de) {
                            "PixelBoard/Gboard Wörterbuch importiert: $wordTextDe, ${result.addedShortcuts} Kürzel."
                        } else {
                            "PixelBoard/Gboard dictionary imported: $wordTextEn and $shortcutTextEn."
                        }
                        else -> if (de) {
                            "Importiert: $wordTextDe, ${result.addedShortcuts} Kürzel."
                        } else {
                            "Imported $wordTextEn and $shortcutTextEn."
                        }
                    }
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Import error"
                }
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val localeTag = if (de) "de-DE" else "en-US"
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            dictionaryStore.exportGboardZip(out, defaultLocaleTag = localeTag)
                        } ?: 0
                    }
                }.onSuccess { count ->
                    statusMessage = if (de) {
                        "PixelBoard/Gboard .zip exportiert ($count Einträge)."
                    } else {
                        "PixelBoard/Gboard .zip exported ($count entries)."
                    }
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Export error"
                }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(dictionaryStore.exportJson())
                        }
                    }
                }.onSuccess {
                    statusMessage = if (de) "JSON-Backup exportiert." else "JSON backup exported."
                }.onFailure { err ->
                    statusMessage = err.localizedMessage ?: "Export error"
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Lernen & Statistik" else "Learning")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        title = if (de) "Adaptives Lernen" else "Adaptive learning",
                        checked = prefs.learningEnabled
                    ) { v -> onUpdatePrefs { it.copy(learningEnabled = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBadge(if (de) "Wörter" else "Words", stats.customWordCount.toString())
                        StatBadge(if (de) "Kürzel" else "Shortcuts", stats.shortcutCount.toString())
                        StatBadge(if (de) "Gelernt" else "Learned", stats.learnedWordCount.toString())
                        StatBadge(if (de) "Paare" else "Bigrams", stats.learnedBigramCount.toString())
                        StatBadge(if (de) "3-Gramm" else "Trigrams", stats.learnedTrigramCount.toString())
                    }
                }
                AppleSectionFooter(
                    if (de) "Lernt deine Wörter, Wortpaare und respektiert rückgängig gemachte Korrekturen."
                    else "Learns your vocabulary and respects undone corrections."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Import & Export" else "Import & Export")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "text/*",
                                        "application/json",
                                        "*/*"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (de) "Importieren" else "Import", fontSize = 13.sp)
                        }
                        FilledTonalButton(
                            onClick = { exportZipLauncher.launch("Tapwing-PersonalDictionary.zip") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (de) "Export (.zip)" else "Export (.zip)", fontSize = 13.sp)
                        }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "JSON-Backup exportieren" else "Export JSON backup",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        exportJsonLauncher.launch("tapwing-dictionary.json")
                    }
                    if (statusMessage != null) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = statusMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                AppleSectionFooter(
                    if (de) "1:1 kompatibel mit PixelBoard/Gboard .zip (PersonalDictionary.zip / dictionary.txt), .txt, .csv/.tsv und .json."
                    else "1:1 compatible with PixelBoard/Gboard .zip, .txt, .csv/.tsv, and .json."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Eigenes Wort" else "Custom words")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppleInlineField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            placeholder = if (de) "Wort hinzufügen…" else "Add word…",
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                dictionaryStore.addCustomWord(newWordInput)
                                newWordInput = ""
                                revision++
                                onDictionaryChanged()
                            },
                            enabled = newWordInput.trim().length >= 2,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    customWords.forEach { (word, weight) ->
                        AppleHairlineDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$word ($weight)", modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(
                                onClick = {
                                    dictionaryStore.removeCustomWord(word)
                                    revision++
                                    onDictionaryChanged()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (stats.customWordCount > customWords.size) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = if (de) {
                                "Zeige Top ${customWords.size} von ${stats.customWordCount} Wörtern"
                            } else {
                                "Showing top ${customWords.size} of ${stats.customWordCount} words"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Textkürzel" else "Text shortcuts")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppleInlineField(
                                value = newShortcutTrigger,
                                onValueChange = { newShortcutTrigger = it },
                                placeholder = if (de) "Kürzel" else "Trigger",
                                modifier = Modifier.weight(0.38f)
                            )
                            AppleInlineField(
                                value = newShortcutExpansion,
                                onValueChange = { newShortcutExpansion = it },
                                placeholder = if (de) "Text" else "Phrase",
                                modifier = Modifier.weight(0.62f)
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                dictionaryStore.addShortcut(newShortcutTrigger, newShortcutExpansion)
                                newShortcutTrigger = ""
                                newShortcutExpansion = ""
                                revision++
                                onDictionaryChanged()
                            },
                            enabled = newShortcutTrigger.isNotBlank() && newShortcutExpansion.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (de) "Kürzel speichern" else "Save shortcut", fontSize = 13.sp)
                        }
                    }
                    shortcuts.forEach { (trigger, expansion) ->
                        AppleHairlineDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$trigger → $expansion",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    dictionaryStore.removeShortcut(trigger)
                                    revision++
                                    onDictionaryChanged()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                AppleSectionFooter(if (de) "Dynamisch eingebaut: /datum, /zeit, /woche, /monat, /jahr" else "Built-in triggers: /date, /time, /week, /month, /year")
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Offline-Wörterbuch-Pakete (50.000 Wörter)" else "Offline Dictionary Packs (50k words)")
                AppleGroupedSurface {
                    OfflineDictionaryPackManager.availablePacks.forEachIndexed { idx, spec ->
                        if (idx > 0) AppleHairlineDivider(startIndent = 16.dp)
                        val state = packStates[spec.langCode] ?: PackInstallState.NotInstalled
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spec.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    val sub = when (state) {
                                        PackInstallState.NotInstalled -> "~${spec.approxSizeKb} KB · Offline"
                                        is PackInstallState.Downloading -> "${(state.progress * 100).roundToInt()}%"
                                        is PackInstallState.Installed -> if (de) {
                                            "Installiert · ${state.wordCount} Wörter"
                                        } else {
                                            "Installed · ${state.wordCount} words"
                                        }
                                        is PackInstallState.Error -> state.message
                                    }
                                    Text(
                                        text = sub,
                                        fontSize = 12.sp,
                                        color = if (state is PackInstallState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                when (state) {
                                    is PackInstallState.Downloading -> {
                                        TextButton(onClick = { packManager.cancelDownload(spec.langCode) }) {
                                            Text(if (de) "Stopp" else "Cancel", fontSize = 12.sp)
                                        }
                                    }
                                    is PackInstallState.Installed -> {
                                        IconButton(
                                            onClick = { packManager.deletePack(spec.langCode) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    else -> {
                                        FilledTonalButton(
                                            onClick = { packManager.startDownload(spec) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (de) "Laden" else "Download", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            if (state is PackInstallState.Downloading) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) "Lädt bis zu 50.000 häufige Wörter pro Sprache lokal aufs Gerät — verbessert Wisch-Tippen & Korrektur ohne Internet."
                    else "Downloads 50,000 high-frequency words locally per language — improves swipe typing & autocorrection offline."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Textbausteine & Platzhalter" else "Multi-line Snippets & Placeholders")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppleInlineField(
                                value = profileName,
                                onValueChange = {
                                    profileName = it
                                    onUpdatePrefs { p -> p.copy(snippetUserName = it) }
                                },
                                placeholder = "{NAME}",
                                modifier = Modifier.weight(1f)
                            )
                            AppleInlineField(
                                value = profileEmail,
                                onValueChange = {
                                    profileEmail = it
                                    onUpdatePrefs { p -> p.copy(snippetUserEmail = it) }
                                },
                                placeholder = "{EMAIL}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppleInlineField(
                                value = newSnippetTrigger,
                                onValueChange = { newSnippetTrigger = it },
                                placeholder = if (de) "Kürzel (z.B. /sig)" else "Trigger (e.g. /sig)",
                                modifier = Modifier.weight(0.4f)
                            )
                            AppleInlineField(
                                value = newSnippetTitle,
                                onValueChange = { newSnippetTitle = it },
                                placeholder = if (de) "Titel" else "Title",
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                        OutlinedTextField(
                            value = newSnippetBody,
                            onValueChange = { newSnippetBody = it },
                            placeholder = {
                                Text(
                                    if (de) "Mehrzeiliger Text mit {DATUM}, {ZEIT}, {NAME}, {EMAIL}, {ZWISCHENABLAGE}…"
                                    else "Multi-line text with {DATE}, {TIME}, {NAME}, {EMAIL}, {CLIPBOARD}…",
                                    fontSize = 13.sp
                                )
                            },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilledTonalButton(
                            onClick = {
                                dictionaryStore.addOrUpdateSnippet(
                                    trigger = newSnippetTrigger,
                                    title = newSnippetTitle,
                                    body = newSnippetBody
                                )
                                newSnippetTrigger = ""
                                newSnippetTitle = ""
                                newSnippetBody = ""
                                revision++
                                onDictionaryChanged()
                            },
                            enabled = newSnippetTrigger.isNotBlank() && newSnippetBody.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (de) "Textbaustein speichern" else "Save snippet", fontSize = 13.sp)
                        }
                    }
                    snippets.forEach { snippet ->
                        AppleHairlineDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${snippet.trigger} · ${snippet.title}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = snippet.body,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    dictionaryStore.deleteSnippet(snippet.id)
                                    revision++
                                    onDictionaryChanged()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) "Platzhalter: {DATUM}, {ZEIT}, {WOCHE}, {MONAT}, {JAHR}, {WOCHENTAG}, {NAME}, {EMAIL}, {ZWISCHENABLAGE}"
                    else "Placeholders: {DATE}, {TIME}, {WEEK}, {MONTH}, {YEAR}, {WEEKDAY}, {NAME}, {EMAIL}, {CLIPBOARD}"
                )
            }
        }

        if (learnedWords.isNotEmpty() || rejectedWords.isNotEmpty()) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Häufig gelernte Wörter" else "Top learned words")
                    AppleGroupedSurface {
                        learnedWords.forEachIndexed { idx, (word, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$word ($count×)", modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(
                                    onClick = {
                                        dictionaryStore.removeLearnedWord(word)
                                        revision++
                                        onDictionaryChanged()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                                }
                            }
                            if (idx < learnedWords.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                        }
                    }
                    if (rejectedWords.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        AppleSectionHeader(if (de) "Ignorierte Autokorrekturen" else "Ignored autocorrections")
                        AppleGroupedSurface {
                            rejectedWords.forEachIndexed { idx, word ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(word, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    TextButton(onClick = {
                                        dictionaryStore.unrejectCorrection(word)
                                        revision++
                                        onDictionaryChanged()
                                    }) {
                                        Text(if (de) "Freigeben" else "Allow", fontSize = 12.sp)
                                    }
                                }
                                if (idx < rejectedWords.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                            }
                        }
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                MinimalActionRow(
                    title = if (de) "Lerndaten leeren" else "Reset learned data",
                    textColor = MaterialTheme.colorScheme.error
                ) {
                    dictionaryStore.clearLearnedData()
                    revision++
                    onDictionaryChanged()
                    statusMessage = if (de) "Gelernte Daten gelöscht." else "Learned data cleared."
                }
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalActionRow(
                    title = if (de) "Eigene Wörter leeren" else "Clear custom words",
                    textColor = MaterialTheme.colorScheme.error
                ) {
                    dictionaryStore.clearCustomWords()
                    revision++
                    onDictionaryChanged()
                    statusMessage = if (de) "Importierte Wörter gelöscht." else "Custom words cleared."
                }
            }
        }
    }
}

@Composable
private fun CorrectionsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onOpenQuickBar: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Tippen & Korrektur" else "Typing & Corrections")
                AppleGroupedSurface {
                    MinimalToggleRow(if (de) "Autokorrektur" else "Auto-correction", prefs.autoCorrect) { v ->
                        onUpdatePrefs { it.copy(autoCorrect = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Wisch-Tippen (Glide Typing)" else "Glide / Swipe typing", prefs.glideTypingEnabled) { v ->
                        onUpdatePrefs { it.copy(glideTypingEnabled = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Rückgängig mit Löschtaste (⌫)" else "Backspace undoes autocorrect", prefs.smartBackspaceUndo) { v ->
                        onUpdatePrefs { it.copy(smartBackspaceUndo = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Adaptives Lernen" else "Adaptive learning", prefs.learningEnabled) { v ->
                        onUpdatePrefs { it.copy(learningEnabled = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Pro-App Schreibstil & Kontext lernen" else "Learn style & words per app", prefs.perAppLearningEnabled) { v ->
                        onUpdatePrefs { it.copy(perAppLearningEnabled = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Inline-Taschenrechner" else "Inline math", prefs.inlineMathEnabled) { v ->
                        onUpdatePrefs { it.copy(inlineMathEnabled = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Wischen auf ⌫ löscht Wort" else "Swipe backspace deletes word", prefs.swipeBackspaceDeleteWord) { v ->
                        onUpdatePrefs { it.copy(swipeBackspaceDeleteWord = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Rechtschreib-Chips" else "Spell & grammar chips", prefs.spellGrammarChips) { v ->
                        onUpdatePrefs { it.copy(spellGrammarChips = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow("Proactive Undo", prefs.proactiveUndo) { v ->
                        onUpdatePrefs { it.copy(proactiveUndo = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Inkognito-Modus" else "Incognito mode", prefs.incognitoMode) { v ->
                        onUpdatePrefs { it.copy(incognitoMode = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Doppeltipp-Punkt" else "Double-space period", prefs.doubleSpacePeriod) { v ->
                        onUpdatePrefs { it.copy(doubleSpacePeriod = v) }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Passwörter und Codes" else "Passwords and codes")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        if (de) "Autofill-Vorschläge anzeigen" else "Show autofill suggestions",
                        prefs.inlineAutofillEnabled
                    ) { enabled -> onUpdatePrefs { it.copy(inlineAutofillEnabled = enabled) } }
                }
                AppleSectionFooter(
                    if (de) "Wähle Bitwarden in den Android-Einstellungen als Autofill-Dienst. Stelle in Bitwarden die Anzeige auf „In der Tastatur“. Tapwing zeigt Vorschläge des Dienstes und erkennt kopierte Bestätigungscodes."
                    else "Choose Bitwarden as your Android autofill service and set its display to Inline. Tapwing shows suggestions from that service and recognises copied verification codes."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Leiste" else "Toolbar")
                AppleGroupedSurface {
                    MinimalNavRow(
                        icon = Icons.Rounded.Tune,
                        badgeColor = Color(0xFF5AC8FA),
                        title = if (de) "Schnellleiste anpassen" else "Customize Quick Bar",
                        value = "${prefs.quickBarActions.size}",
                        onClick = onOpenQuickBar
                    )
                }
                AppleSectionFooter(
                    if (de) "Reihenfolge und Symbole über der Tastatur festlegen."
                    else "Choose order and shortcuts above the keyboard."
                )
            }
        }
    }
}

@Composable
private fun VoiceSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    modelManager: LocalModelManager,
    secureKeyStorage: SecureKeyStorage,
    onOpenWritingTools: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStates by modelManager.states.collectAsState()
    var pendingImportSpec by remember { mutableStateOf<LocalModelSpec?>(null) }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }
    val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetSpec = pendingImportSpec
        pendingImportSpec = null
        if (uri != null && targetSpec != null) {
            scope.launch {
                val ok = modelManager.importModelFromUri(targetSpec, uri)
                if (ok) {
                    onUpdatePrefs { it.copy(selectedWhisperModelId = targetSpec.id) }
                }
            }
        }
    }

    var advanced by rememberSaveable { mutableStateOf(false) }
    var modelDetails by rememberSaveable { mutableStateOf(false) }
    val useLiveCloud = prefs.voiceEngine == VoiceEngineMode.GEMINI_LIVE
    val dictationProvider = CloudSpeechProvider.from(prefs.aiProvider, prefs.aiCustomEndpoint)
        ?.takeIf { !useLiveCloud || it == CloudSpeechProvider.GEMINI }
    val keySaved = remember(dictationProvider) {
        dictationProvider != null && secureKeyStorage.readDictationKey(dictationProvider.name).isNotBlank()
    }
    val localAndroidAvailable = remember(context) { TapwingVoiceController.isOnDeviceSpeechAvailable(context) }
    val installedWhisper = remember(modelStates, prefs.selectedWhisperModelId) {
        modelManager.firstInstalledModel(LocalModelKind.WHISPER_STT, prefs.selectedWhisperModelId)
    }
    val visibleModel = installedWhisper?.first ?: requireNotNull(LocalModelCatalog.findById("whisper-tiny-q5_1"))

    val defaultPromptPreview = remember(prefs.voiceCleanupUseSystemLanguage, prefs.activeLocale) {
        CleanupPromptBuilder.buildDefaultPrompt(
            activeKeyboardLocale = prefs.activeLocale,
            useSystemLanguage = prefs.voiceCleanupUseSystemLanguage
        )
    }
    var promptDraft by remember(prefs.voiceCleanupPrompt) {
        mutableStateOf(prefs.voiceCleanupPrompt)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleGroupedSurface {
                    MinimalToggleRow(
                        if (de) "Diktat glätten" else "Clean up dictation",
                        prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
                    ) { enabled ->
                        val mode = if (enabled) VoiceDictationMode.NATURAL_CLEANUP else VoiceDictationMode.STANDARD
                        onUpdatePrefs { it.copy(voiceMode = mode) }
                    }
                    if (!micGranted) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            if (de) "Mikrofon erlauben" else "Allow microphone",
                            MaterialTheme.colorScheme.primary
                        ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Tippe zum Starten und Beenden auf das Mikrofon. Tapwing kann Zögern entfernen und Satzzeichen ergänzen."
                    else "Tap the microphone to start and stop. Tapwing can remove hesitations and add punctuation."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Spracherkennung" else "Speech recognition")
                AppleGroupedSurface {
                    val options = buildList {
                        add(VoiceEngineMode.AUTO_LOCAL_FIRST)
                        if (localAndroidAvailable || prefs.voiceEngine == VoiceEngineMode.ON_DEVICE_SPEECH) {
                            add(VoiceEngineMode.ON_DEVICE_SPEECH)
                        }
                        add(VoiceEngineMode.LOCAL_WHISPER)
                        add(VoiceEngineMode.GEMINI_LIVE)
                        if (advanced || prefs.voiceEngine == VoiceEngineMode.CLOUD) add(VoiceEngineMode.CLOUD)
                        if (advanced || prefs.voiceEngine == VoiceEngineMode.SYSTEM_SPEECH) {
                            add(VoiceEngineMode.SYSTEM_SPEECH)
                        }
                    }
                    options.forEachIndexed { index, mode ->
                        if (index > 0) AppleHairlineDivider(startIndent = 16.dp)
                        val (title, description) = when (mode) {
                            VoiceEngineMode.AUTO_LOCAL_FIRST -> if (de) {
                                "Automatisch" to "Nutzt zuerst ein geprüftes Android-Offline-Sprachpaket. Danach kommen Whisper oder der Android-Sprachdienst."
                            } else "Automatic" to "Prefers a verified Android offline language pack, then Whisper or the Android speech service."
                            VoiceEngineMode.ON_DEVICE_SPEECH -> if (de) {
                                "Android offline · Live-Text" to "Empfohlen für schnelles Offline-Diktat. Benötigt ein Android-Sprachpaket."
                            } else "Android offline · Live text" to "Recommended for quick offline dictation. Requires an Android language pack."
                            VoiceEngineMode.LOCAL_WHISPER -> if (de) {
                                "Whisper offline" to "Verarbeitet Abschnitte bei Sprechpausen. Audio bleibt auf dem Gerät."
                            } else "Whisper offline" to "Processes sections at pauses. Audio stays on your device."
                            VoiceEngineMode.GEMINI_LIVE -> if (de) {
                                "Gemini Live" to "Sendet Audio schon beim Sprechen an Google. Kostenloses Kontingent mit eigenem Schlüssel."
                            } else "Gemini Live" to "Streams audio to Google as you speak. Free quota with your own key."
                            VoiceEngineMode.CLOUD -> if (de) {
                                "Cloud nach dem Stopp" to "Sendet Audio nach dem Stopp an Gemini oder OpenAI. Kosten sind möglich."
                            } else "Cloud after you stop" to "Sends audio to Gemini or OpenAI when you stop. Charges may apply."
                            VoiceEngineMode.SYSTEM_SPEECH -> if (de) {
                                "Android-Sprachdienst" to "Zeigt Live-Text. Der Dienst kann je nach Gerät das Internet nutzen."
                            } else "Android speech service" to "Shows live text. The service may use the internet on some devices."
                        }
                        VoiceChoice(title, description, selected = prefs.voiceEngine == mode) {
                            onUpdatePrefs { it.copy(voiceEngine = mode) }
                        }
                    }
                }
            }
        }

        if (prefs.voiceEngine.usesCloudAudio) {
            item {
                Column {
                    AppleSectionHeader(if (de) "Cloud einrichten" else "Cloud setup")
                    AppleGroupedSurface {
                        MinimalValueRow(
                            if (de) "Modell für Audio" else "Speech model",
                            if (useLiveCloud) "Gemini 3.5 Transcribe Live" else when (dictationProvider) {
                                CloudSpeechProvider.GEMINI -> "Gemini 3.5 Flash-Lite"
                                CloudSpeechProvider.OPENAI -> CloudDictation.OPENAI_MODEL
                                null -> if (de) "Anbieter wählen" else "Choose provider"
                            }
                        )
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            if (de) "Anbieter und Schlüssel" else "Provider and key",
                            MaterialTheme.colorScheme.primary,
                            onClick = onOpenWritingTools
                        )
                    }
                    AppleSectionFooter(
                        when {
                            useLiveCloud && de -> "Gemini 3.5 Transcribe Live glättet Text schon während der Aufnahme. Google kann Eingaben aus dem kostenlosen Tarif zur Produktverbesserung nutzen."
                            useLiveCloud -> "Gemini 3.5 Transcribe Live polishes text while you speak. Google may use free-tier inputs to improve its products."
                            de -> "Gemini 3.5 Flash-Lite ist im kostenlosen Kontingent verfügbar und glättet nach dem Stopp. OpenAI ist eine Alternative mit eigener API-Abrechnung."
                            else -> "Gemini 3.5 Flash-Lite has a free quota and polishes after you stop. OpenAI is an alternative with separate API billing."
                        }
                    )
                    if (useLiveCloud && dictationProvider == null) {
                        AppleSectionFooter(
                            if (de) "Wähle in der Schreibhilfe Google Gemini und speichere dort deinen API-Schlüssel."
                            else "Choose Google Gemini under Writing Tools and save your API key there."
                        )
                    } else if (prefs.aiProvider == AiProviderType.OPENAI_COMPATIBLE && dictationProvider == null) {
                        AppleSectionFooter(
                            if (de) "Cloud-Diktat mit OpenAI benötigt den offiziellen OpenAI-Endpunkt."
                            else "OpenAI dictation requires the official OpenAI endpoint."
                        )
                    } else if (!keySaved) {
                        AppleSectionFooter(
                            if (de) "Speichere den Schlüssel für diesen Anbieter. Gib einen älteren Schlüssel einmal neu ein."
                            else "Save the key for this provider. Re-enter an existing key once to enable cloud dictation."
                        )
                    }
                }
            }
        }

        if (prefs.voiceEngine == VoiceEngineMode.LOCAL_WHISPER || prefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalModelCatalogSection(
                        title = if (de) "Offline-Sprachmodell" else "Offline speech model",
                        recommendationNote = if (de) {
                            "Starte mit Whisper Tiny (32 MB) für kürzere Wartezeit. Wähle Base (60 MB), wenn dir mehr Genauigkeit wichtiger ist."
                        } else {
                            "Start with Whisper Tiny (32 MB) for a shorter wait. Try Base (60 MB) if you need more accuracy."
                        },
                        models = if (modelDetails) LocalModelCatalog.whisperModels else listOf(visibleModel),
                        selectedModelId = installedWhisper?.first?.id.orEmpty(),
                        states = modelStates,
                        de = de,
                        onSelectModel = { spec -> onUpdatePrefs { it.copy(selectedWhisperModelId = spec.id) } },
                        onDownloadModel = { spec ->
                            if (installedWhisper == null) onUpdatePrefs { it.copy(selectedWhisperModelId = spec.id) }
                            modelManager.startDownload(spec)
                        },
                        onCancelDownload = { spec -> modelManager.cancelDownload(spec.id) },
                        onDeleteModel = { spec -> modelManager.deleteModel(spec.id) },
                        onImportModel = { spec ->
                            pendingImportSpec = spec
                            importModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        onOpenUrl = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                        showTechnicalDetails = modelDetails
                    )
                    AppleGroupedSurface {
                        SettingsDisclosure(if (de) "Alle Offline-Modelle" else "All offline models", modelDetails) {
                            modelDetails = !modelDetails
                        }
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                SettingsDisclosure(if (de) "Weitere Diktatoptionen" else "More dictation options", advanced) {
                    advanced = !advanced
                }
            }
        }
        if (!advanced) return@LazyColumn

        item {
            Column {
                AppleSectionHeader(if (de) "Zusätzliche Text-KI" else "Additional text AI")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        if (de) "KI-Bereinigung nach Diktat" else "AI cleanup after dictation",
                        prefs.voiceAiPolish
                    ) { v -> onUpdatePrefs { it.copy(voiceAiPolish = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        if (de) "Sprache = Systemsprache (${KeyboardLocales.systemLanguageDisplayName()})"
                        else "Language = System (${KeyboardLocales.systemLanguageDisplayName()})",
                        prefs.voiceCleanupUseSystemLanguage
                    ) { v -> onUpdatePrefs { it.copy(voiceCleanupUseSystemLanguage = v) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            label = {
                                Text(
                                    if (de) "Eigene Anweisung (optional)"
                                    else "Custom instructions (optional)",
                                    fontSize = 12.sp
                                )
                            },
                            placeholder = {
                                Text(defaultPromptPreview.take(160) + "…", fontSize = 12.sp)
                            },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onUpdatePrefs { it.copy(voiceCleanupPrompt = promptDraft.trim()) }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "Prompt speichern" else "Save prompt", fontSize = 13.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    promptDraft = defaultPromptPreview
                                    onUpdatePrefs { it.copy(voiceCleanupPrompt = "") }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "Standard einfügen" else "Load default", fontSize = 13.sp)
                            }
                        }
                    }
                }
                AppleSectionFooter(
                    if (de) "Optionaler zweiter Schritt mit dem Anbieter aus der Schreibhilfe. In den Offline-Modi bleibt der Text lokal."
                    else "Optional second step using your Writing Tools provider. In offline modes, text stays on the device."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Diktat-Modus & Optionen" else "Dictation Mode & Options")
                AppleGroupedSurface {
                    MinimalToggleRow(if (de) "Füllwörter entfernen" else "Remove filler words", prefs.voiceRemoveFillers) { v ->
                        onUpdatePrefs { it.copy(voiceRemoveFillers = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Auto-Zeichensetzung" else "Auto-punctuation", prefs.voiceAutoPunctuate) { v ->
                        onUpdatePrefs { it.copy(voiceAutoPunctuate = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Tastatursprache bevorzugen" else "Prefer keyboard language", prefs.voiceStrictLanguageLock) { v ->
                        onUpdatePrefs { it.copy(voiceStrictLanguageLock = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Minimieren-Taste" else "Minimize button", prefs.voiceMinimizeButton) { v ->
                        onUpdatePrefs { it.copy(voiceMinimizeButton = v) }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Mikrofon & Timing" else "Microphone & Timing")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Diktat nach Drehung fortsetzen" else "Resume dictation after rotation", fontSize = 15.sp)
                            Text("${prefs.voiceRotationGraceMs} ms", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.voiceRotationGraceMs.toFloat(),
                            onValueChange = { value ->
                                onUpdatePrefs { it.copy(voiceRotationGraceMs = value.roundToInt().toLong()) }
                            },
                            valueRange = 500f..3500f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (micGranted) {
                            if (de) "Mikrofon-Berechtigung erteilt ✓" else "Microphone granted ✓"
                        } else {
                            if (de) "Mikrofon-Berechtigung anfordern" else "Grant microphone permission"
                        },
                        textColor = if (micGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    ) {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }

        item {
            var diagRunning by remember { mutableStateOf(false) }
            var diagReport by remember { mutableStateOf<String?>(null) }
            val installedLlm = remember(modelStates, prefs.selectedLocalLlmId) {
                modelManager.firstInstalledModel(LocalModelKind.CLEANUP_LLM, prefs.selectedLocalLlmId)
            }
            Column {
                AppleSectionHeader(if (de) "Offline-Diktat testen" else "Test offline dictation")
                AppleGroupedSurface {
                    MinimalValueRow(
                        if (de) "Aktives Whisper-Modell" else "Active Whisper model",
                        installedWhisper?.first?.displayName ?: (if (de) "Keines geladen" else "None")
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Aktive KI-Bereinigung" else "Active cleanup AI",
                        when (prefs.aiProvider) {
                            AiProviderType.LOCAL_LLM -> installedLlm?.first?.displayName ?: (if (de) "Kein GGUF" else "No GGUF")
                            AiProviderType.LOCAL_RULES -> if (de) "On-Device Regeln" else "Local Rules"
                            AiProviderType.GEMINI_API -> "Gemini API"
                            AiProviderType.OPENAI_COMPATIBLE -> "OpenAI API"
                        }
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (!micGranted) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@FilledTonalButton
                                }
                                val target = installedWhisper
                                if (target == null) {
                                    diagReport = if (de) "Bitte zuerst ein Whisper-Modell laden." else "Please download a Whisper model first."
                                    return@FilledTonalButton
                                }
                                diagRunning = true
                                diagReport = if (de) "Aufnahme läuft (3 Sek.)… jetzt sprechen!" else "Recording (3 sec)… speak now!"
                                scope.launch {
                                    val wav = File(context.cacheDir, "tapwing_diag_voice.wav")
                                    val rec = WavAudioRecorder()
                                    val started = rec.start(wav, {}, {}, { err ->
                                        diagReport = "Mic error: $err"
                                    })
                                    if (!started) {
                                        diagRunning = false
                                        return@launch
                                    }
                                    delay(3000L)
                                    val result = rec.stopAndGetResult()
                                    if (result == null) {
                                        diagRunning = false
                                        diagReport = if (de) "Keine Audiodatei erzeugt." else "Failed to record WAV."
                                        return@launch
                                    }
                                    diagReport = if (de) {
                                        "Transkribiere (${result.durationMs} ms, Pegel ${result.peakSampleAbs})…"
                                    } else {
                                        "Transcribing (${result.durationMs} ms, peak ${result.peakSampleAbs})…"
                                    }
                                    val t0 = System.currentTimeMillis()
                                    val tr = LocalInferenceEngine.transcribeWav(
                                        wavPath = result.file.absolutePath,
                                        modelFile = target.second,
                                        languageCode = KeyboardLocales.baseLang(prefs.activeLocale)
                                    )
                                    val ms = System.currentTimeMillis() - t0
                                    result.file.delete()
                                    diagRunning = false
                                    diagReport = tr.fold(
                                        onSuccess = { txt ->
                                            "Mic Peak=${result.peakSampleAbs}, Whisper=${ms}ms → '${txt.ifBlank { "(Stille)" }}'"
                                        },
                                        onFailure = { err ->
                                            "Whisper error (${ms}ms): ${err.localizedMessage}"
                                        }
                                    )
                                }
                            },
                            enabled = !diagRunning,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "3s Mic + Whisper" else "3s Mic + Whisper", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                val target = installedLlm
                                if (target == null) {
                                    diagReport = if (de) "Kein lokales GGUF-Modell installiert." else "No local GGUF LLM installed."
                                    return@FilledTonalButton
                                }
                                diagRunning = true
                                diagReport = if (de) "Teste ${target.first.displayName}…" else "Testing ${target.first.displayName}…"
                                scope.launch {
                                    val prompt = CleanupPromptBuilder.resolveSystemPrompt(
                                        customPrompt = prefs.voiceCleanupPrompt,
                                        useSystemLanguage = prefs.voiceCleanupUseSystemLanguage,
                                        activeKeyboardLocale = prefs.activeLocale
                                    )
                                    val t0 = System.currentTimeMillis()
                                    val res = LocalInferenceEngine.generateWithLocalLlm(
                                        modelFile = target.second,
                                        spec = target.first,
                                        systemPrompt = prompt,
                                        userText = "äh hallo ich wollte fragen treffen wir uns um 14 uhr nein warte 15 uhr im büro"
                                    )
                                    val ms = System.currentTimeMillis() - t0
                                    diagRunning = false
                                    diagReport = res.fold(
                                        onSuccess = { out -> "${target.first.displayName} (${ms}ms) → '$out'" },
                                        onFailure = { err -> "LLM error (${ms}ms): ${err.localizedMessage}" }
                                    )
                                }
                            },
                            enabled = !diagRunning,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "Lokale KI testen" else "Test Local LLM", fontSize = 12.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val target = installedWhisper
                                if (target == null) {
                                    diagReport = if (de) "Bitte zuerst ein Whisper-Modell laden." else "Please download a Whisper model first."
                                    return@FilledTonalButton
                                }
                                diagRunning = true
                                diagReport = if (de) "Erzeuge deutsches TTS-Audio & transkribiere…" else "Synthesizing German TTS & transcribing…"
                                scope.launch {
                                    val wav = File(context.cacheDir, "tapwing_tts_16k.wav")
                                    val synth = VoiceSelfTest.synthesizeGermanWav16k(
                                        context = context,
                                        text = "Hallo, treffen wir uns morgen um fünfzehn Uhr im Büro?",
                                        dest16kWav = wav
                                    )
                                    if (synth.isFailure) {
                                        diagRunning = false
                                        diagReport = "TTS Fehler: ${synth.exceptionOrNull()?.localizedMessage}"
                                        return@launch
                                    }
                                    val (file, wavInfo) = synth.getOrThrow()
                                    val t0 = System.currentTimeMillis()
                                    val tr = LocalInferenceEngine.transcribeWav(
                                        wavPath = file.absolutePath,
                                        modelFile = target.second,
                                        languageCode = KeyboardLocales.baseLang(prefs.activeLocale)
                                    )
                                    val ms = System.currentTimeMillis() - t0
                                    file.delete()
                                    diagRunning = false
                                    diagReport = tr.fold(
                                        onSuccess = { txt -> "TTS ($wavInfo) → Whisper=${ms}ms → '${txt.ifBlank { "(leer)" }}'" },
                                        onFailure = { err -> "Whisper Fehler (${ms}ms): ${err.localizedMessage}" }
                                    )
                                }
                            },
                            enabled = !diagRunning,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (de) "TTS-Sprache → Whisper testen" else "Test TTS Speech → Whisper", fontSize = 12.sp)
                        }
                    }
                    if (!diagReport.isNullOrBlank()) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = diagReport!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingToolsSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    secureKeyStorage: SecureKeyStorage,
    modelManager: LocalModelManager,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStates by modelManager.states.collectAsState()
    var pendingImportSpec by remember { mutableStateOf<LocalModelSpec?>(null) }

    var apiKeyInput by remember { mutableStateOf("") }
    var hasSavedKey by remember { mutableStateOf(secureKeyStorage.hasApiKey()) }
    var showKeyHelp by rememberSaveable { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val recommendedModel = when (prefs.aiProvider) {
        AiProviderType.GEMINI_API -> CloudDictation.GEMINI_MODEL
        AiProviderType.OPENAI_COMPATIBLE -> "gpt-4o-mini"
        else -> ""
    }

    if (showKeyHelp && recommendedModel.isNotEmpty()) {
        ApiKeyHelpDialog(
            isGemini = prefs.aiProvider == AiProviderType.GEMINI_API,
            de = de,
            onDismiss = { showKeyHelp = false }
        )
    }

    val importLlmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetSpec = pendingImportSpec
        pendingImportSpec = null
        if (uri != null && targetSpec != null) {
            scope.launch {
                val ok = modelManager.importModelFromUri(targetSpec, uri)
                if (ok) {
                    onUpdatePrefs {
                        it.copy(selectedLocalLlmId = targetSpec.id, aiProvider = AiProviderType.LOCAL_LLM)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AppleGroupedSurface {
                MinimalToggleRow(if (de) "KI-Schreibhilfe" else "AI Writing Tools", prefs.aiWritingToolsEnabled) { v ->
                    onUpdatePrefs { it.copy(aiWritingToolsEnabled = v) }
                }

            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Text verarbeiten" else "Text processing")
                AppleGroupedSurface {
                    AiProviderType.entries.forEachIndexed { index, provider ->
                        if (index > 0) AppleHairlineDivider(startIndent = 16.dp)
                        val (title, description) = when (provider) {
                            AiProviderType.LOCAL_RULES -> if (de) {
                                "Schnell und offline" to "Einfache Korrekturen ohne Download. Empfohlen für den Start."
                            } else "Fast and offline" to "Basic cleanup without a download. Recommended to start."
                            AiProviderType.LOCAL_LLM -> if (de) {
                                "KI auf dem Gerät" to "Schreibt Texte um. Einmaliger Modelldownload nötig."
                            } else "On-device AI" to "Rewrites text. Requires a one-time model download."
                            AiProviderType.GEMINI_API -> if (de) {
                                "Google Gemini" to "Schreibhilfe und optionales Cloud-Diktat mit eigenem Schlüssel."
                            } else "Google Gemini" to "Writing tools and optional cloud dictation with your own key."
                            AiProviderType.OPENAI_COMPATIBLE -> if (de) {
                                "OpenAI" to "Schreibhilfe und optionales Cloud-Diktat mit eigenem Schlüssel."
                            } else "OpenAI" to "Writing tools and optional cloud dictation with your own key."
                        }
                        VoiceChoice(title, description, selected = prefs.aiProvider == provider) {
                            onUpdatePrefs { it.copy(aiProvider = provider) }
                        }
                    }
                }
            }
        }

        if (prefs.aiProvider == AiProviderType.LOCAL_LLM) item {
            LocalModelCatalogSection(
                title = if (de) "Offline-Schreibmodell" else "Offline writing model",
                recommendationNote = if (de) {
                    "Starte mit Qwen3 0.6B (397 MB). Für längere Texte gibt es Qwen3 1.7B (1,1 GB)."
                } else {
                    "Start with Qwen3 0.6B (397 MB). Try Qwen3 1.7B (1.1 GB) for longer text."
                },
                models = if (advanced) LocalModelCatalog.llmModels else listOf(
                    LocalModelCatalog.findById(prefs.selectedLocalLlmId)
                        ?.takeIf { modelStates[it.id] is ModelInstallState.Installed || modelStates[it.id] is ModelInstallState.Downloading }
                        ?: requireNotNull(LocalModelCatalog.findById("qwen3-0.6b-q4_k_m"))
                ),
                selectedModelId = prefs.selectedLocalLlmId,
                states = modelStates,
                de = de,
                onSelectModel = { spec ->
                    onUpdatePrefs { it.copy(selectedLocalLlmId = spec.id, aiProvider = AiProviderType.LOCAL_LLM) }
                },
                onDownloadModel = { spec ->
                    if (!advanced) onUpdatePrefs { it.copy(selectedLocalLlmId = spec.id) }
                    modelManager.startDownload(spec)
                },
                onCancelDownload = { spec -> modelManager.cancelDownload(spec.id) },
                onDeleteModel = { spec -> modelManager.deleteModel(spec.id) },
                onImportModel = { spec ->
                    pendingImportSpec = spec
                    importLlmLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onOpenUrl = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
                showTechnicalDetails = advanced
            )
        }

        if (recommendedModel.isNotEmpty()) item {
            Column {
                AppleSectionHeader(if (de) "API-Schlüssel" else "API key")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (prefs.aiModel != recommendedModel) {
                            FilledTonalButton(onClick = { onUpdatePrefs { it.copy(aiModel = recommendedModel) } }) {
                                Text(if (de) "Empfohlenes Schreibmodell nutzen" else "Use recommended writing model")
                            }
                        }
                        if (advanced) {
                            AppleInlineField(
                                value = prefs.aiModel,
                                onValueChange = { model -> onUpdatePrefs { it.copy(aiModel = model) } },
                                placeholder = if (de) "Modell für die Schreibhilfe" else "Model for Writing Tools"
                            )
                            if (prefs.aiProvider == AiProviderType.OPENAI_COMPATIBLE) {
                                AppleInlineField(
                                    value = prefs.aiCustomEndpoint,
                                    onValueChange = { endpoint -> onUpdatePrefs { it.copy(aiCustomEndpoint = endpoint) } },
                                    placeholder = "https://api.openai.com/v1/chat/completions"
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                AppleInlineField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    placeholder = if (de) "API-Schlüssel einfügen" else "Paste your API key",
                                    isPassword = true
                                )
                            }
                            IconButton(onClick = { showKeyHelp = true }) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = if (de) "Wo bekomme ich einen API-Schlüssel?" else "How do I get an API key?",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val provider = CloudSpeechProvider.from(prefs.aiProvider, prefs.aiCustomEndpoint)
                                    if (!SecureKeyStorage.isValidApiKey(apiKeyInput)) {
                                        testStatus = if (de) "Der Schlüssel enthält Leerzeichen oder ungültige Zeichen. Kopiere ihn erneut."
                                                     else "The key contains spaces or unsupported characters. Copy it again."
                                    } else if (runCatching {
                                        secureKeyStorage.saveApiKey(apiKeyInput, dictationProvider = provider?.name)
                                    }.isSuccess) {
                                        hasSavedKey = secureKeyStorage.hasApiKey()
                                        apiKeyInput = ""
                                        testStatus = if (de) "Schlüssel auf dem Gerät gespeichert." else "Key saved on this device."
                                    } else {
                                        testStatus = if (de) "Der Schlüssel konnte gerade nicht gespeichert werden."
                                                     else "The key could not be saved right now."
                                    }
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "Speichern" else "Save", fontSize = 13.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    secureKeyStorage.clearApiKey()
                                    hasSavedKey = false
                                    testStatus = if (de) "API-Key gelöscht." else "Cleared."
                                },
                                enabled = hasSavedKey,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "Entfernen" else "Clear", fontSize = 13.sp)
                            }
                        }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !testing) {
                                testing = true
                                testStatus = null
                                scope.launch {
                                    val res = CloudAiClient.testConnection(
                                        provider = prefs.aiProvider,
                                        apiKey = secureKeyStorage.readApiKey(),
                                        model = prefs.aiModel,
                                        customEndpoint = prefs.aiCustomEndpoint,
                                        locale = prefs.activeLocale
                                    )
                                    testing = false
                                    testStatus = when (res) {
                                        is CloudAiResult.Success -> "${res.providerLabel}: OK (${res.text})"
                                        is CloudAiResult.Failure -> res.message
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (de) "Verbindung testen" else "Test connection",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (testStatus != null) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = testStatus!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                AppleSectionFooter(
                    if (de) "Empfohlen für die Schreibhilfe: $recommendedModel. Der Anbieter berechnet die Nutzung über dein eigenes Konto."
                    else "Recommended for Writing Tools: $recommendedModel. The provider bills usage through your own account."
                )
            }
        }

        item {
            AppleGroupedSurface {
                SettingsDisclosure(if (de) "Weitere Optionen" else "More options", advanced) {
                    advanced = !advanced
                }
                if (advanced) {
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(
                        if (de) "Alle Tastaturen unterstützen" else "Support all keyboards",
                        prefs.aiSupportAllKeyboards
                    ) { enabled -> onUpdatePrefs { it.copy(aiSupportAllKeyboards = enabled) } }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyHelpDialog(isGemini: Boolean, de: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val provider = if (isGemini) "Google Gemini" else "OpenAI"
    val keyPage = if (isGemini) "https://aistudio.google.com/apikey" else "https://platform.openai.com/api-keys"
    val steps = when {
        isGemini && de -> listOf(
            "Öffne die Schlüsselseite von Google AI Studio und melde dich mit deinem Google-Konto an.",
            "Tippe auf „Create API key“ (API-Schlüssel erstellen). Wähle ein Projekt oder erstelle eines.",
            "Kopiere den Schlüssel. Halte das Schlüsselfeld in Tapwing gedrückt, wähle „Einfügen“ und tippe auf „Speichern“."
        )
        isGemini -> listOf(
            "Open the Google AI Studio key page and sign in with your Google account.",
            "Choose Create API key. Select a project or create one.",
            "Copy the key. Touch and hold Tapwing’s key field, choose Paste, then tap Save."
        )
        de -> listOf(
            "Öffne die Schlüsselseite der OpenAI API-Plattform und melde dich an.",
            "Wähle ein Projekt und tippe auf „Create new secret key“ (neuen Schlüssel erstellen).",
            "Kopiere den angezeigten Schlüssel sofort. Halte das Schlüsselfeld in Tapwing gedrückt, wähle „Einfügen“ und tippe auf „Speichern“."
        )
        else -> listOf(
            "Open the OpenAI API platform's key page and sign in.",
            "Select a project and choose Create new secret key.",
            "Copy the key now. Touch and hold Tapwing’s key field, choose Paste, then tap Save."
        )
    }
    val cost = when {
        isGemini && de -> "Gemini 3.5 Transcribe Live und Flash-Lite haben ein kostenloses Kontingent. Google kann Eingaben im kostenlosen Tarif zur Produktverbesserung verwenden."
        isGemini -> "Gemini 3.5 Transcribe Live and Flash-Lite have a free quota. Google may use free-tier inputs to improve its products."
        de -> "Die OpenAI-API wird getrennt von einem ChatGPT-Abo abgerechnet. Richte bei Bedarf die Abrechnung auf der API-Plattform ein."
        else -> "OpenAI API billing is separate from a ChatGPT subscription. Enable billing on the API platform if needed."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (de) "API-Schlüssel für $provider" else "API key for $provider") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                Text(cost, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyPage))) }
            }) { Text(if (de) "Schlüsselseite öffnen" else "Open key page") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (de) "Schließen" else "Close") } }
    )
}

@Composable
private fun LocalModelCatalogSection(
    title: String,
    recommendationNote: String,
    models: List<LocalModelSpec>,
    selectedModelId: String,
    states: Map<String, ModelInstallState>,
    de: Boolean,
    onSelectModel: (LocalModelSpec) -> Unit,
    onDownloadModel: (LocalModelSpec) -> Unit,
    onCancelDownload: (LocalModelSpec) -> Unit,
    onDeleteModel: (LocalModelSpec) -> Unit,
    onImportModel: (LocalModelSpec) -> Unit,
    onOpenUrl: (String) -> Unit,
    showTechnicalDetails: Boolean = true
) {
    Column {
        AppleSectionHeader(title)
        AppleGroupedSurface {
            Text(
                text = recommendationNote,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            models.forEach { spec ->
                AppleHairlineDivider(startIndent = 16.dp)
                val state = states[spec.id] ?: ModelInstallState.NotInstalled
                val isSelected = selectedModelId == spec.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state is ModelInstallState.Installed) { onSelectModel(spec) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (showTechnicalDetails) spec.displayName else spec.displayName.substringBefore(" ("),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (showTechnicalDetails && spec.isRecommended) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = if (de) "Empfohlen" else "Recommended",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${spec.approxSizeMb} MB" + if (showTechnicalDetails) " · ${spec.minRamGb}+ GB RAM" else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected && state is ModelInstallState.Installed) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (showTechnicalDetails) Text(
                        text = if (de) spec.summaryDe else spec.summaryEn,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (state) {
                        is ModelInstallState.Downloading -> {
                            val pct = (state.progress * 100f).roundToInt()
                            val dlMb = state.downloadedBytes / 1_000_000L
                            val totMb = (state.totalBytes / 1_000_000L).coerceAtLeast(1L)
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$pct% ($dlMb / $totMb MB)", fontSize = 12.sp)
                                TextButton(onClick = { onCancelDownload(spec) }) {
                                    Text(if (de) "Abbrechen" else "Cancel", fontSize = 12.sp)
                                }
                            }
                        }
                        is ModelInstallState.Installed -> if (showTechnicalDetails || !isSelected) {
                            val mb = state.sizeBytes / 1_000_000L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = { onSelectModel(spec) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isSelected) (if (de) "Aktiv ✓" else "Active ✓")
                                        else (if (de) "Auswählen ($mb MB)" else "Use ($mb MB)"),
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(onClick = { onDeleteModel(spec) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        is ModelInstallState.Error -> {
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onDownloadModel(spec) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (de) "Erneut laden" else "Retry", fontSize = 12.sp)
                                }
                                if (showTechnicalDetails) TextButton(onClick = { onOpenUrl(spec.repoUrl) }) {
                                    Text("HuggingFace", fontSize = 12.sp)
                                }
                            }
                        }
                        ModelInstallState.NotInstalled -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onDownloadModel(spec) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (de) "Laden" else "Download", fontSize = 12.sp)
                                }
                                if (showTechnicalDetails) FilledTonalButton(
                                    onClick = { onImportModel(spec) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(if (de) "Datei" else "File", fontSize = 12.sp)
                                }
                                if (showTechnicalDetails) TextButton(onClick = { onOpenUrl(spec.repoUrl) }) {
                                    Text("Link", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ocrResultNotice by remember { mutableStateOf<String?>(null) }
    var mediaPermGranted by remember {
        mutableStateOf(ScreenshotMonitor.hasMediaPermission(context))
    }
    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaPermGranted = granted
    }

    val photoOcrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = ScreenshotMonitor.extractTextFromImageUri(context, uri)
                if (text.isNotBlank()) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Tapwing OCR", text))
                    ocrResultNotice = if (de) {
                        "In Zwischenablage kopiert: ${text.take(64)}"
                    } else {
                        "Copied to clipboard: ${text.take(64)}"
                    }
                } else {
                    ocrResultNotice = if (de) "Kein Text im Bild gefunden." else "No text found in image."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            AppleGroupedSurface {
                MinimalToggleRow(if (de) "Verlauf und Einfügevorschläge" else "History and paste suggestions", prefs.clipboardEnabled) { v ->
                    onUpdatePrefs { it.copy(clipboardEnabled = v) }
                }
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalToggleRow(if (de) "Screenshots automatisch kopieren" else "Auto-copy screenshots", prefs.autoCopyScreenshots) { v ->
                    onUpdatePrefs { it.copy(autoCopyScreenshots = v) }
                    if (v && !mediaPermGranted) {
                        mediaPermLauncher.launch(ScreenshotMonitor.requiredMediaPermission())
                    }
                }
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalToggleRow(if (de) "Screenshot-Vorschau in Leiste" else "Screenshot preview chip", prefs.screenshotSuggestionChip) { v ->
                    onUpdatePrefs { it.copy(screenshotSuggestionChip = v) }
                    if (v && !mediaPermGranted) {
                        mediaPermLauncher.launch(ScreenshotMonitor.requiredMediaPermission())
                    }
                }
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalActionRow(
                    title = if (mediaPermGranted) {
                        if (de) "Screenshot-Zugriff erteilt ✓" else "Screenshot access granted ✓"
                    } else {
                        if (de) "Screenshot-Zugriff erlauben" else "Allow screenshot access"
                    },
                    textColor = if (mediaPermGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                ) {
                    mediaPermLauncher.launch(ScreenshotMonitor.requiredMediaPermission())
                }
            }
            AppleSectionFooter(
                if (de) "Kopierter Text erscheint in der Vorschlagsleiste. Antippen fügt ihn ein. Mit den Screenshot-Optionen kannst du Bilder separat kopieren oder anzeigen."
                else "Copied text appears in the suggestion bar. Tap it to paste. The screenshot options control copying and previews for images."
            )
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Aufbewahrung" else "Retention")
                AppleGroupedSurface {
                    val options = listOf(
                        ClipboardRetention.HOUR_1 to (if (de) "1 Stunde" else "1 hour"),
                        ClipboardRetention.HOURS_24 to (if (de) "24 Stunden" else "24 hours"),
                        ClipboardRetention.DAYS_7 to (if (de) "7 Tage" else "7 days"),
                        ClipboardRetention.DAYS_30 to (if (de) "30 Tage" else "30 days"),
                        ClipboardRetention.NEVER to (if (de) "Nie löschen" else "Never delete")
                    )
                    options.forEachIndexed { idx, (ret, label) ->
                        MinimalCheckRow(label, selected = prefs.clipboardRetention == ret) {
                            onUpdatePrefs { it.copy(clipboardRetention = ret) }
                        }
                        if (idx < options.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Verlauf & Scan Text" else "Limits & OCR")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (de) "Max. Einträge" else "Max clips", fontSize = 15.sp)
                            Text("${prefs.clipboardMaxItems}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Slider(
                            value = prefs.clipboardMaxItems.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(clipboardMaxItems = v.roundToInt()) } },
                            valueRange = 20f..200f
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Foto / Screenshot scannen (OCR)" else "Scan photo / screenshot (OCR)",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        photoOcrLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Kamera-OCR öffnen" else "Open Camera OCR",
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        context.startActivity(Intent(context, ScanTextActivity::class.java))
                    }
                    if (ocrResultNotice != null) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = ocrResultNotice!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                AppleSectionFooter(
                    if (de) "Fotos werden nur einzeln über die System-Auswahl gelesen (kein dauerhafter Galeriezugriff)."
                    else "Photos are read one-by-one via Android Photo Picker without broad storage permission."
                )
            }
        }
    }
}

@Composable
private fun EmojiSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Größe" else "Size")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (de) "Emoji-Größe" else "Emoji size", fontSize = 15.sp)
                            Text("${prefs.emojiScale} sp", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        }
                        Text(
                            text = "😀  🔥  ✨  🙏  🚀",
                            fontSize = prefs.emojiScale.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Slider(
                            value = prefs.emojiScale.toFloat(),
                            onValueChange = { v -> onUpdatePrefs { it.copy(emojiScale = v.roundToInt()) } },
                            valueRange = 20f..34f
                        )
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                MinimalToggleRow(
                    title = if (de) "Emoji-Vorschläge beim Tippen" else "Emoji suggestions while typing",
                    checked = prefs.candidateEmojiSuggestions
                ) { v -> onUpdatePrefs { it.copy(candidateEmojiSuggestions = v) } }
            }
        }
    }
}

@Composable
private fun AboutSettingsScreen(
    de: Boolean,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Text("Tapwing", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} • Apache-2.0",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Column {
                AppleSectionHeader("Details")
                AppleGroupedSurface {
                    MinimalValueRow(if (de) "Paket-ID" else "Package", "com.tapwing.keyboard")
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(if (de) "Verarbeitung" else "Processing", if (de) "Lokal auf Gerät" else "On-device")
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(if (de) "Lizenz" else "License", "Apache-2.0")
                }
            }
        }

        item {
            Column {
                AppleSectionHeader("System")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (de) "Tastatur in System aktivieren" else "Enable in system settings",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onOpenImeSystemSettings
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        title = if (de) "Eingabemethode wählen" else "Choose input method",
                        textColor = MaterialTheme.colorScheme.primary,
                        onClick = onShowImePicker
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MinimalNavRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppleIconBadge(icon = icon, tintColor = badgeColor)
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun MinimalToggleRow(
    title: String,
    checked: Boolean,
    icon: ImageVector? = null,
    badgeColor: Color = Color(0xFF007AFF),
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = if (icon != null) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            AppleIconBadge(icon = icon, tintColor = badgeColor)
        }
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AppleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDisclosure(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .clickable(
                onClickLabel = if (expanded) stringResource(R.string.settings_collapse) else stringResource(R.string.settings_expand),
                role = Role.Button,
                onClick = onToggle
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceChoice(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MinimalCheckRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun MinimalValueRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MinimalActionRow(
    title: String,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TypingStatsScreen(
    stats: TypingStats,
    de: Boolean,
    onReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Geschwindigkeit & Leistung" else "Speed & Productivity")
                AppleGroupedSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatBadge(if (de) "Tempo" else "Speed", if (stats.estimatedWpm > 0) "${stats.estimatedWpm} WPM" else "—")
                        StatBadge(if (de) "Wörter" else "Words", stats.wordsTyped.toString())
                        StatBadge(if (de) "Gespart" else "Saved", "${stats.charactersSaved}")
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Getippte Zeichen" else "Characters typed",
                        stats.charactersTyped.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Angenommene Vorschläge" else "Suggestions accepted",
                        stats.suggestionsAccepted.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Autokorrekturen" else "Autocorrections applied",
                        stats.autocorrectionsApplied.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Wisch-Wörter (Glide)" else "Swipe-typed words",
                        stats.swipesDecoded.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Spracheingaben" else "Voice dictations",
                        stats.voiceDictations.toString()
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Textbausteine eingefügt" else "Snippets inserted",
                        stats.snippetsInserted.toString()
                    )
                }
                AppleSectionFooter(
                    if (de) "100% lokal auf deinem Gerät gespeichert. Im Inkognito-Modus pausiert die Zählung automatisch."
                    else "Stored 100% locally on your device. Automatically pauses in Incognito mode."
                )
            }
        }

        item {
            AppleGroupedSurface {
                MinimalActionRow(
                    title = if (de) "Statistik zurücksetzen" else "Reset statistics",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = onReset
                )
            }
        }
    }
}

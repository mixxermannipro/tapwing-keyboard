package com.tapwing.keyboard.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.inputmethod.InputMethodManager
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.tapwing.keyboard.ai.AiModelCatalog
import com.tapwing.keyboard.ai.CleanupPromptBuilder
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.CloudAiResult
import com.tapwing.keyboard.ai.GeminiNanoClient
import com.tapwing.keyboard.ai.LocalInferenceEngine
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelKind
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.ai.LocalModelSpec
import com.tapwing.keyboard.ai.ModelInstallState
import com.tapwing.keyboard.ai.NanoAvailability
import com.tapwing.keyboard.voice.VoiceSelfTest
import com.tapwing.keyboard.voice.CloudDictation
import com.tapwing.keyboard.voice.GeminiLiveDictation
import com.tapwing.keyboard.voice.CloudSpeechProvider
import com.tapwing.keyboard.voice.TapwingVoiceController
import com.tapwing.keyboard.voice.WavAudioRecorder
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.clipboard.SENSITIVE_CLIP_EXTRA
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.ClipboardRetention
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.QuickSearchEngine
import com.tapwing.keyboard.data.SearchApp
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
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SettingsRoute {
    HOME,
    APPEARANCE,
    QUICK_BAR,
    QUICK_SEARCH,
    AUTOFILL,
    LANGUAGES,
    DICTIONARY,
    CORRECTIONS,
    VOICE,
    WRITING_TOOLS,
    API_KEYS,
    CLIPBOARD,
    EMOJI,
    STATS,
    ABOUT
}

private data class SettingsDestination(val route: SettingsRoute, val tab: SettingsTab)

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
    val focusManager = LocalFocusManager.current
    var backStack by rememberSaveable(stateSaver = listSaver(
        save = { stack: List<SettingsRoute> -> stack.map { it.name } },
        restore = { saved -> saved.map { SettingsRoute.valueOf(it) } }
    )) { mutableStateOf(listOf(SettingsRoute.HOME)) }
    val stateHolder = rememberSaveableStateHolder()
    val route = backStack.last()
    var homeTab by rememberSaveable { mutableStateOf(SettingsTab.START) }
    val selectedTab = if (route == SettingsRoute.HOME) homeTab else SettingsTab.forRoute(route)
    val destination = SettingsDestination(route, selectedTab)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var barRevealed by remember(destination) { mutableStateOf(true) }
    val barVisible = barRevealed && !imeVisible
    val barSpace by animateDpAsState(
        targetValue = if (barVisible) settingsBarSpace else 0.dp,
        animationSpec = tween(settingsBarMotionMillis, easing = FastOutSlowInEasing),
        label = "settingsBarSpace"
    )
    val scrollConnection = remember(destination, density) {
        val scroll = SettingsBarScroll()
        val threshold = with(density) { 12.dp.toPx() }
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val gesture = consumed + available
                if (source == NestedScrollSource.UserInput && abs(gesture.y) > abs(gesture.x)) {
                    scroll.update(barRevealed, consumed.y, available.y, threshold)?.let { barRevealed = it }
                }
                return Offset.Zero
            }
        }
    }
    var navigatingBack by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var testInputText by remember { mutableStateOf("") }
    var dictRevision by remember { mutableIntStateOf(0) }

    var imeEnabled by remember { mutableStateOf(false) }
    var imeSelected by remember { mutableStateOf(false) }
    var installedSearchApp by remember(prefs.quickSearchEngine) {
        mutableStateOf(prefs.quickSearchEngine.installedApp(context))
    }
    var hasAutofillProvider by remember { mutableStateOf(false) }

    fun navigateTo(target: SettingsRoute) {
        if (route != target) {
            navigatingBack = false
            backStack = backStack + target
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            navigatingBack = true
            backStack = backStack.dropLast(1)
        } else if (homeTab != SettingsTab.START) {
            navigatingBack = true
            homeTab = SettingsTab.START
        } else if (searchQuery.isNotEmpty()) {
            focusManager.clearFocus()
            searchQuery = ""
        }
    }

    BackHandler(
        enabled = !imeVisible && (backStack.size > 1 || homeTab != SettingsTab.START || searchQuery.isNotEmpty())
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
        hasAutofillProvider = context.getSystemService(AutofillManager::class.java)?.isEnabled == true
    }

    DisposableEffect(lifecycleOwner, prefs.quickSearchEngine) {
        refreshImeStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
                installedSearchApp = prefs.quickSearchEngine.installedApp(context)
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

    val liveStats by statsStore.stats.collectAsState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            AnimatedContent(
                modifier = Modifier.fillMaxSize().padding(bottom = barSpace).nestedScroll(scrollConnection),
                targetState = destination,
                transitionSpec = {
                    val direction = if (navigatingBack) -1 else 1
                    (slideInHorizontally(tween(270, easing = FastOutSlowInEasing)) { direction * it / 8 } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { -direction * it / 12 } +
                            fadeOut(tween(160)))
                },
                label = "settingsNavigation"
            ) { active ->
                val activeRoute = active.route
                stateHolder.SaveableStateProvider(if (activeRoute == SettingsRoute.HOME) active.tab.name else activeRoute.name) {
                    Column(Modifier.fillMaxSize()) {
                        if (activeRoute != SettingsRoute.HOME) SettingsHeader(activeRoute, ::navigateBack)
                        when (activeRoute) {
                            SettingsRoute.HOME -> HomeSettingsScreen(
                                tab = active.tab,
                                prefs = prefs,
                                imeEnabled = imeEnabled,
                                imeSelected = imeSelected,
                                hasAutofillProvider = hasAutofillProvider,
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
                                onNavigate = { navigateTo(it) }
                            )
                            SettingsRoute.APPEARANCE -> AppearanceScreen(prefs, de, onUpdatePrefs)
                            SettingsRoute.QUICK_BAR -> QuickBarSettingsScreen(prefs, de, onUpdatePrefs)
                            SettingsRoute.QUICK_SEARCH -> QuickSearchSettingsScreen(prefs, installedSearchApp, onUpdatePrefs)
                            SettingsRoute.AUTOFILL -> AutofillSettingsScreen(prefs, hasAutofillProvider, onUpdatePrefs)
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
                                onOpenApiKeys = { navigateTo(SettingsRoute.API_KEYS) },
                                onUpdatePrefs = onUpdatePrefs
                            )
                            SettingsRoute.WRITING_TOOLS -> WritingToolsSettingsScreen(
                                prefs = prefs,
                                de = de,
                                secureKeyStorage = secureKeyStorage,
                                modelManager = modelManager,
                                onOpenApiKeys = { navigateTo(SettingsRoute.API_KEYS) },
                                onUpdatePrefs = onUpdatePrefs
                            )
                            SettingsRoute.API_KEYS -> ApiKeysVaultScreen(
                                prefs = prefs,
                                de = de,
                                secureKeyStorage = secureKeyStorage,
                                onUpdatePrefs = onUpdatePrefs
                            )
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
            SettingsBottomBar(
                visible = barVisible,
                selected = selectedTab,
                onSelect = { tab ->
                    if (route != SettingsRoute.HOME || tab != homeTab) {
                        navigatingBack = tab.ordinal < selectedTab.ordinal
                        homeTab = tab
                        backStack = listOf(SettingsRoute.HOME)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHeader(route: SettingsRoute, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = {
            Text(stringResource(route.label()), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.primary)
            }
        },
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    )
}

private fun SettingsRoute.label(): Int = when (this) {
    SettingsRoute.HOME -> R.string.settings_title
    SettingsRoute.APPEARANCE -> R.string.nav_appearance
    SettingsRoute.QUICK_BAR -> R.string.nav_quick_bar
    SettingsRoute.QUICK_SEARCH -> R.string.nav_quick_search
    SettingsRoute.AUTOFILL -> R.string.nav_autofill
    SettingsRoute.LANGUAGES -> R.string.nav_languages
    SettingsRoute.DICTIONARY -> R.string.nav_dictionary
    SettingsRoute.CORRECTIONS -> R.string.nav_corrections
    SettingsRoute.VOICE -> R.string.nav_voice
    SettingsRoute.WRITING_TOOLS -> R.string.nav_writing_tools
    SettingsRoute.API_KEYS -> R.string.nav_api_keys
    SettingsRoute.CLIPBOARD -> R.string.nav_clipboard
    SettingsRoute.EMOJI -> R.string.nav_emoji
    SettingsRoute.STATS -> R.string.nav_typing_stats
    SettingsRoute.ABOUT -> R.string.nav_about
}

private data class SettingsRow(
    val route: SettingsRoute,
    val icon: ImageVector,
    val keywords: String,
    val summary: String = ""
)

@Composable
private fun HomeSettingsScreen(
    tab: SettingsTab,
    prefs: TapwingPrefs,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    hasAutofillProvider: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit
) {
    val typing = listOf(
        SettingsRow(SettingsRoute.LANGUAGES, Icons.Rounded.Language, "sprache qwertz qwerty azerty layout",
            KeyboardLocales.specFor(prefs.activeLocale).nativeLabel.substringBefore(" ")),
        SettingsRow(SettingsRoute.CORRECTIONS, Icons.Rounded.Spellcheck, "autocorrect autokorrektur rückgängig undo grammatik inkognito enter"),
        SettingsRow(SettingsRoute.DICTIONARY, Icons.Rounded.Book, "wörter lernen learn zip import export local offline"),
        SettingsRow(SettingsRoute.VOICE, Icons.Rounded.Mic, "sprachaufnahme mikrofon speech dictation diktat whisper")
    )
    val tools = listOf(
        SettingsRow(SettingsRoute.QUICK_SEARCH, Icons.Rounded.Search, "browser google duckduckgo bing startpage brave ecosia internet", prefs.quickSearchEngine.label),
        SettingsRow(SettingsRoute.CLIPBOARD, Icons.Rounded.ContentPaste, "clipboard kopieren copy ocr screenshot kamera"),
        SettingsRow(SettingsRoute.AUTOFILL, Icons.Rounded.Key, "bitwarden autofill passwort password anmelden login benutzername 2fa otp"),
        SettingsRow(SettingsRoute.WRITING_TOOLS, Icons.Rounded.AutoAwesome, "ki ai modell model openai groq gemini grok openrouter"),
        SettingsRow(SettingsRoute.API_KEYS, Icons.Rounded.Key, "anbieter provider API schlüssel key modell model")
    )
    val keyboard = listOf(
        SettingsRow(SettingsRoute.APPEARANCE, Icons.Rounded.Palette, "theme apple design farbe größe höhe abstand haptik"),
        SettingsRow(SettingsRoute.QUICK_BAR, Icons.Rounded.Tune, "toolbar schnellleiste kurzbefehle reihenfolge"),
        SettingsRow(SettingsRoute.EMOJI, Icons.Rounded.EmojiEmotions, "emoji smileys symbole"),
        SettingsRow(SettingsRoute.STATS, Icons.Rounded.BarChart, "statistik stats keystrokes"),
        SettingsRow(SettingsRoute.ABOUT, Icons.Rounded.Info, "datenschutz privacy lizenz version")
    )
    val search = searchQuery.trim().lowercase()
    val allRows = typing + tools + keyboard
    val tabRows = allRows.filter { it.route in tab.routes }
    val searchRows = allRows.filter {
        stringResource(it.route.label()).lowercase().contains(search) || it.keywords.contains(search)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "search") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(if (tab == SettingsTab.START) R.string.settings_title else tab.label),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.9).sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
                if (tab == SettingsTab.START) {
                    AppleSearchPill(searchQuery, onSearchQueryChange, stringResource(R.string.settings_search_placeholder))
                }
            }
        }

        if (tab != SettingsTab.START) {
            item(key = "category") { SettingsGroup(null, tabRows, onNavigate) }
        } else if (search.isNotEmpty()) {
            item(key = "results") {
                if (searchRows.isEmpty()) AppleSectionFooter(stringResource(R.string.settings_no_results))
                else SettingsGroup(null, searchRows, onNavigate)
            }
        } else {
            item(key = "setup") {
                AnimatedContent(
                    targetState = imeEnabled to imeSelected,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                    label = "setupStatus"
                ) { (enabled, selected) ->
                    if (enabled && selected) {
                        AppleGroupedSurface {
                            AppleInlineField(
                                value = testInputText,
                                onValueChange = onTestInputChange,
                                placeholder = stringResource(R.string.settings_test_placeholder),
                                leadingIcon = Icons.Rounded.Keyboard,
                                containerAlpha = 0f,
                                fieldHeight = 54.dp
                            )
                        }
                    } else {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(stringResource(R.string.settings_setup_title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (!enabled) R.string.setup_enable_tapwing else R.string.setup_select_tapwing),
                                    style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = if (!enabled) onOpenImeSystemSettings else onShowImePicker) {
                                    Text(stringResource(if (!enabled) R.string.settings_enable else R.string.settings_select))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "recommendations") {
                val recommendation = when {
                    !prefs.learningEnabled && !prefs.incognitoMode -> SettingsRoute.DICTIONARY to R.string.settings_tip_learning
                    !hasAutofillProvider || !prefs.inlineAutofillEnabled -> SettingsRoute.AUTOFILL to R.string.settings_tip_autofill
                    prefs.voiceEngine != VoiceEngineMode.LOCAL_WHISPER -> SettingsRoute.VOICE to R.string.settings_tip_voice
                    else -> SettingsRoute.QUICK_SEARCH to R.string.settings_tip_search
                }
                Column {
                    AppleSectionHeader(stringResource(R.string.settings_recommendation))
                    AppleGroupedSurface {
                        Row(
                            Modifier.fillMaxWidth().clickable { onNavigate(recommendation.first) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(recommendation.first.label()), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(recommendation.second), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "app") { SettingsGroup(null, tabRows, onNavigate) }
        }
    }
}

@Composable
private fun SettingsGroup(title: String?, rows: List<SettingsRow>, onNavigate: (SettingsRoute) -> Unit) {
    Column {
        if (title != null) AppleSectionHeader(title)
        AppleGroupedSurface {
            rows.forEachIndexed { index, item ->
                if (index > 0) AppleHairlineDivider(startIndent = 58.dp)
                MinimalNavRow(item.icon, MaterialTheme.colorScheme.primary, stringResource(item.route.label()), item.summary) {
                    onNavigate(item.route)
                }
            }
        }
    }
}

@Composable
private fun QuickSearchSettingsScreen(
    prefs: TapwingPrefs,
    installedApp: SearchApp?,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val appSelected = installedApp != null && installedApp.packageName == prefs.quickSearchAppPackage
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(stringResource(R.string.search_engine))
                AppleGroupedSurface {
                    QuickSearchEngine.entries.forEachIndexed { index, engine ->
                        if (index > 0) AppleHairlineDivider(startIndent = 16.dp)
                        MinimalCheckRow(engine.label, prefs.quickSearchEngine == engine) {
                            onUpdatePrefs {
                                if (it.quickSearchEngine == engine) it
                                else it.copy(quickSearchEngine = engine, quickSearchAppPackage = "")
                            }
                        }
                    }
                }
            }
        }
        item {
            Column {
                AppleSectionHeader(stringResource(R.string.search_open_in))
                AppleGroupedSurface {
                    MinimalCheckRow(stringResource(R.string.search_browser), !appSelected) {
                        onUpdatePrefs { it.copy(quickSearchAppPackage = "") }
                    }
                    if (installedApp != null) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalCheckRow(stringResource(R.string.search_app, installedApp.label), appSelected) {
                            onUpdatePrefs { it.copy(quickSearchAppPackage = installedApp.packageName) }
                        }
                    }
                }
                AppleSectionFooter(stringResource(
                    if (installedApp == null) R.string.search_app_unavailable else R.string.search_browser_fallback
                ))
            }
        }
    }
}

@Composable
private fun AutofillSettingsScreen(
    prefs: TapwingPrefs,
    hasAutofillProvider: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(stringResource(
                    if (hasAutofillProvider) R.string.autofill_active else R.string.autofill_inactive
                ))
                AppleGroupedSurface {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        MinimalToggleRow(stringResource(R.string.autofill_inline_toggle), prefs.inlineAutofillEnabled) { enabled ->
                            onUpdatePrefs { it.copy(inlineAutofillEnabled = enabled) }
                        }
                        AppleHairlineDivider(startIndent = 16.dp)
                    }
                    MinimalActionRow(stringResource(R.string.autofill_android_settings), MaterialTheme.colorScheme.primary) {
                        val settingsIntents = listOf(
                            Intent("android.settings.CREDENTIAL_PROVIDER"),
                            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}")),
                            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                            Intent(Settings.ACTION_SETTINGS)
                        )
                        settingsIntents.any { runCatching { context.startActivity(it) }.isSuccess }
                    }
                }
                AppleSectionFooter(stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) R.string.autofill_help else R.string.autofill_requires_android_11
                ))
            }
        }
        item {
            AppleGroupedSurface {
                MinimalActionRow(stringResource(R.string.autofill_bitwarden_help), MaterialTheme.colorScheme.primary) {
                    context.openSettingsLink("https://bitwarden.com/help/auto-fill-android/")
                }
            }
        }
    }
}

private fun Context.openSettingsLink(url: String) {
    if (runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure) {
        Toast.makeText(this, R.string.settings_link_unavailable, Toast.LENGTH_SHORT).show()
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
                        ThemeMode.PIXEL_SLATE to (if (de) "Gboard-Stil (Standard)" else "Gboard Style (Default)"),
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
                AppleSectionHeader(if (de) "Taste unten rechts" else "Bottom-right key")
                AppleGroupedSurface {
                    MinimalToggleRow(
                        if (de) "Unten rechts immer neue Zeile" else "Always insert newline on bottom-right",
                        prefs.enterKeyAlwaysNewline
                    ) { v -> onUpdatePrefs { it.copy(enterKeyAlwaysNewline = v) } }
                }
                AppleSectionFooter(
                    if (de) "Aus (Empfohlen): Passt die Taste automatisch an — Suchen/Go im Browser, Zeilenumbruch in Chats mit Senden-Button (z. B. WhatsApp). Ein: erzwingt immer neue Zeile."
                    else "Off (Recommended): Adapts automatically — Search/Go in browsers, newline in chats with a Send button (e.g. WhatsApp). On: always inserts newline."
                )
            }
        }

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
                    MinimalToggleRow(if (de) "Rückgängig anbieten" else "Show undo shortcut", prefs.proactiveUndo) { v ->
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
    onOpenApiKeys: () -> Unit,
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
    var diagRunning by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf<String?>(null) }

    val useLiveCloud = prefs.voiceEngine == VoiceEngineMode.GEMINI_LIVE
    val activeDictProviderType = if (useLiveCloud) AiProviderType.GEMINI_API else prefs.dictationProvider
    val dictationProvider = CloudSpeechProvider.from(activeDictProviderType, prefs.aiCustomEndpoint)
    val keySaved = remember(activeDictProviderType) {
        secureKeyStorage.hasKey(activeDictProviderType) ||
            (dictationProvider != null && secureKeyStorage.readDictationKey(dictationProvider.name).isNotBlank())
    }
    val localAndroidAvailable = remember(context) { TapwingVoiceController.isOnDeviceSpeechAvailable(context) }
    val installedWhisper = remember(modelStates, prefs.selectedWhisperModelId) {
        modelManager.firstInstalledModel(LocalModelKind.WHISPER_STT, prefs.selectedWhisperModelId)
    }
    val visibleModel = installedWhisper?.first ?: requireNotNull(LocalModelCatalog.findById("whisper-tiny-q5_1"))
    val cloudNeedsKey = prefs.voiceEngine.usesCloudAudio && (dictationProvider == null || !keySaved)
    val offlineReady = installedWhisper != null || localAndroidAvailable ||
        (!prefs.voiceEngine.usesCloudAudio && android.speech.SpeechRecognizer.isRecognitionAvailable(context))
    val dictationReady = micGranted && !cloudNeedsKey && (prefs.voiceEngine.usesCloudAudio || offlineReady)

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
            AppleGroupedSurface {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (dictationReady) Color(0xFF34C759).copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (dictationReady) Icons.Rounded.Check else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (dictationReady) Color(0xFF34C759) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = when {
                                !micGranted -> if (de) "Mikrofon noch erlauben" else "Allow microphone access"
                                cloudNeedsKey -> if (de) "Cloud-Schlüssel fehlt" else "Cloud API key needed"
                                installedWhisper == null && prefs.voiceEngine == VoiceEngineMode.LOCAL_WHISPER ->
                                    if (de) "Offline-Sprachpaket laden" else "Download offline speech model"
                                else -> if (de) "Diktat ist einsatzbereit" else "Dictation is ready"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                !micGranted -> if (de) "Ein Tipp genügt, damit Tapwing dich hört." else "Tap once so Tapwing can hear your voice."
                                cloudNeedsKey -> if (de) "Schlüssel hinterlegen oder auf „Automatisch“ wechseln." else "Save your key or switch to Automatic."
                                installedWhisper != null -> if (de) "Schnell & ohne Zeitlimit (${installedWhisper.first.displayName.substringBefore(" (")})." else "Fast & unlimited (${installedWhisper.first.displayName.substringBefore(" (")})."
                                else -> if (de) "Funktioniert direkt und ohne Zeitlimit." else "Ready immediately with no time limit."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!micGranted) {
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalActionRow(
                        if (de) "Mikrofon jetzt erlauben" else "Allow microphone now",
                        MaterialTheme.colorScheme.primary
                    ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Empfohlener Workflow (für Tempo)" else "Recommended workflow (for speed)")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (de) "1. Rambler-Tempo (~0,3 Sek. nach ✓)" else "1. Rambler speed (~0.3s after ✓)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (de) {
                                "„Cloud-KI (Live)“ mit Gemini ODER „Automatisch“ (Android-Live) kombiniert mit Groq/Gemini unter Schreibhilfe."
                            } else {
                                "“Cloud AI (Live)” with Gemini OR “Automatic” (Android live) paired with Groq/Gemini in Writing tools."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppleHairlineDivider(startIndent = 0.dp)
                        Text(
                            text = if (de) "2. Sofort & 100 % Offline (< 0,2 Sek.)" else "2. Instant & 100% Offline (< 0.2s)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (de) {
                                "„Automatisch“ nutzt das Android-Sprachpaket + schnelle Offline-Regeln (auf unterstützten Pixel/Flagships auch Gemini Nano)."
                            } else {
                                "“Automatic” uses your Android speech pack + instant offline rules (or Gemini Nano on supported AICore phones)."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppleHairlineDivider(startIndent = 0.dp)
                        Text(
                            text = if (de) "3. Offline ohne Google (Whisper)" else "3. Offline without Google (Whisper)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (de) {
                                "Whisper Tiny/Base läuft komplett autark auf dem Gerät — ideal für Datenschutz oder lange Monologe."
                            } else {
                                "Whisper Tiny/Base runs standalone on-device — great for strict privacy or long monologues."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Column {
                AppleGroupedSurface {
                    MinimalToggleRow(
                        if (de) "Sätze automatisch glätten" else "Clean up sentences automatically",
                        prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
                    ) { enabled ->
                        val mode = if (enabled) VoiceDictationMode.NATURAL_CLEANUP else VoiceDictationMode.STANDARD
                        onUpdatePrefs { it.copy(voiceMode = mode) }
                    }
                }
                AppleSectionFooter(
                    if (de) "Entfernt „ähm“ und Versprecher mitten im Satz, setzt Satzzeichen und versteht Befehle wie „mach es freundlicher“."
                    else "Removes ‘ums’ and mid-sentence corrections, adds punctuation, and supports commands like ‘make it friendly’."
                )
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Wie soll erkannt werden?" else "Recognition mode")
                AppleGroupedSurface {
                    val primaryOptions = listOf(
                        VoiceEngineMode.AUTO_LOCAL_FIRST,
                        VoiceEngineMode.LOCAL_WHISPER,
                        VoiceEngineMode.GEMINI_LIVE
                    )
                    val options = buildList {
                        addAll(primaryOptions)
                        if (prefs.voiceEngine !in primaryOptions || advanced) {
                            if (localAndroidAvailable || prefs.voiceEngine == VoiceEngineMode.ON_DEVICE_SPEECH) {
                                add(VoiceEngineMode.ON_DEVICE_SPEECH)
                            }
                            if (advanced || prefs.voiceEngine == VoiceEngineMode.CLOUD) add(VoiceEngineMode.CLOUD)
                            if (advanced || prefs.voiceEngine == VoiceEngineMode.SYSTEM_SPEECH) add(VoiceEngineMode.SYSTEM_SPEECH)
                        }
                    }
                    options.forEachIndexed { index, mode ->
                        if (index > 0) AppleHairlineDivider(startIndent = 16.dp)
                        val (title, description) = when (mode) {
                            VoiceEngineMode.AUTO_LOCAL_FIRST -> if (de) {
                                "Automatisch (Empfohlen)" to "Schnell & zuverlässig: nutzt dein Gerät direkt und schützt vor Ausfällen."
                            } else "Automatic (Recommended)" to "Fast & reliable: runs on your device and falls back automatically."
                            VoiceEngineMode.LOCAL_WHISPER -> if (de) {
                                "Nur auf dem Gerät (Offline)" to "100 % privat, funktioniert auch ohne Internet — auch für sehr lange Texte."
                            } else "On-device only (Offline)" to "100% private, works completely offline — even for long dictations."
                            VoiceEngineMode.GEMINI_LIVE -> if (de) {
                                "Cloud-KI (Live)" to "Höchste Genauigkeit mit Live-Vorschau und automatischem Offline-Schutz."
                            } else "Cloud AI (Live)" to "Best accuracy with live preview and automatic offline fallback."
                            VoiceEngineMode.ON_DEVICE_SPEECH -> if (de) {
                                "Android offline · Live-Text" to "System-Sprachpaket auf dem Gerät mit Sofort-Vorschau."
                            } else "Android offline · Live text" to "Built-in device language pack with instant preview."
                            VoiceEngineMode.CLOUD -> if (de) {
                                "Cloud nach dem Stopp" to "Sendet Audio nach dem Stopp an Gemini oder OpenAI."
                            } else "Cloud after stop" to "Sends audio to Gemini or OpenAI after stopping."
                            VoiceEngineMode.SYSTEM_SPEECH -> if (de) {
                                "Android-Sprachdienst" to "Standard-Spracherkennung von Android."
                            } else "Android speech service" to "Standard Android speech recognition."
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
                            if (de) "Diktat-Anbieter" else "Dictation provider",
                            if (useLiveCloud) "Gemini Live" else AiModelCatalog.displayName(prefs.dictationProvider, de)
                        )
                        val model = if (useLiveCloud) GeminiLiveDictation.MODEL
                            else prefs.dictationModel.ifBlank { AiModelCatalog.defaultAudioModelFor(activeDictProviderType) }
                        AiModelReference(activeDictProviderType, model)
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            if (de) "API-Schlüssel-Tresor öffnen" else "Open API Keys Vault",
                            MaterialTheme.colorScheme.primary,
                            onClick = onOpenApiKeys
                        )
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            if (de) "Diktat- & Bereinigungsmodelle wählen" else "Choose dictation & cleanup models",
                            MaterialTheme.colorScheme.primary,
                            onClick = onOpenWritingTools
                        )
                    }
                    if (!keySaved || dictationProvider == null) {
                        AppleSectionFooter(
                            if (de) "Hinterlege in der Schreibhilfe deinen API-Schlüssel — oder nutze „Automatisch“ ohne Schlüssel."
                            else "Add your API key under Writing tools — or switch to Automatic without a key."
                        )
                    } else {
                        AppleSectionFooter(
                            if (de) "Bei Verbindungsabbruch übernimmt automatisch ein Offline-Modell, falls installiert."
                            else "If the connection drops, an installed offline model takes over automatically."
                        )
                    }
                }
            }
        }

        if (prefs.voiceEngine == VoiceEngineMode.LOCAL_WHISPER ||
            prefs.voiceEngine == VoiceEngineMode.AUTO_LOCAL_FIRST ||
            prefs.voiceEngine == VoiceEngineMode.GEMINI_LIVE
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalModelCatalogSection(
                        title = if (de) "Offline-Sprachpaket" else "Offline speech model",
                        recommendationNote = if (de) {
                            "Whisper Tiny (32 MB) ist blitzschnell. Whisper Base (60 MB) erkennt Namen und Satzbau noch genauer."
                        } else {
                            "Whisper Tiny (32 MB) is instant. Whisper Base (60 MB) improves accuracy on names and grammar."
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
                        SettingsDisclosure(if (de) "Weitere Modellgrößen anzeigen" else "Show more model sizes", modelDetails) {
                            modelDetails = !modelDetails
                        }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Schnelltest" else "Quick check")
                AppleGroupedSurface {
                    MinimalActionRow(
                        title = if (diagRunning) (if (de) "Test läuft…" else "Testing…")
                        else (if (de) "Diktat kurz testen (3 Sek.)" else "Test dictation (3 sec)"),
                        textColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (diagRunning) return@MinimalActionRow
                        if (!micGranted) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@MinimalActionRow
                        }
                        val target = installedWhisper
                        if (target == null) {
                            diagReport = if (de) "Lade zuerst oben ein Offline-Sprachpaket, um lokal zu testen."
                            else "Download an offline speech model above first to test locally."
                            return@MinimalActionRow
                        }
                        diagRunning = true
                        diagReport = if (de) "Aufnahme läuft (3 Sek.) — sprich jetzt!" else "Recording (3 sec) — speak now!"
                        scope.launch {
                            val wav = File(context.cacheDir, "tapwing_diag_voice.wav")
                            val rec = WavAudioRecorder()
                            val started = rec.start(wav, {}, {}, { err ->
                                diagReport = if (de) "Mikrofon-Fehler: $err" else "Mic error: $err"
                            })
                            if (!started) {
                                diagRunning = false
                                return@launch
                            }
                            delay(3000L)
                            val result = rec.stopAndGetResult()
                            if (result == null) {
                                diagRunning = false
                                diagReport = if (de) "Keine Aufnahme erstellt." else "No audio recorded."
                                return@launch
                            }
                            diagReport = if (de) "Erkenne Text…" else "Recognizing speech…"
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
                                    if (txt.isBlank()) {
                                        if (de) "Keine Sprache gehört (Pegel ${result.peakSampleAbs}). Bitte lauter sprechen."
                                        else "No speech heard (peak ${result.peakSampleAbs}). Try speaking closer."
                                    } else {
                                        if (de) "Erkannt in ${ms} ms: „$txt“" else "Recognized in ${ms} ms: \"$txt\""
                                    }
                                },
                                onFailure = { err ->
                                    if (de) "Fehler (${ms} ms): ${err.localizedMessage}" else "Error (${ms} ms): ${err.localizedMessage}"
                                }
                            )
                        }
                    }
                    if (!diagReport.isNullOrBlank()) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        Text(
                            text = diagReport!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        item {
            AppleGroupedSurface {
                SettingsDisclosure(if (de) "Erweiterte Einstellungen" else "Advanced settings", advanced) {
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
            Column {
                AppleSectionHeader(if (de) "Diagnose & Technik" else "Diagnostics & Tech")
                AppleGroupedSurface {
                    MinimalValueRow(
                        if (de) "Aktives Whisper-Modell" else "Active Whisper model",
                        installedWhisper?.first?.displayName ?: (if (de) "Keines geladen" else "None")
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalValueRow(
                        if (de) "Aktive KI-Bereinigung" else "Active cleanup AI",
                        "${AiModelCatalog.displayName(prefs.cleanupProvider, de)}${if (prefs.cleanupModel.isNotBlank()) " · ${prefs.cleanupModel}" else ""}"
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "TTS → Whisper" else "TTS → Whisper", fontSize = 12.sp)
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
    @Suppress("UNUSED_PARAMETER") modelManager: LocalModelManager,
    onOpenApiKeys: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nanoStatus by remember { mutableStateOf<NanoAvailability?>(null) }
    var nanoDownloading by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val savedCount = remember(prefs) { secureKeyStorage.configuredCloudProviders().size }

    LaunchedEffect(prefs.aiProvider, prefs.cleanupProvider, prefs.activeLocale) {
        nanoStatus = GeminiNanoClient.checkAvailability(context, prefs.activeLocale)
    }

    val allTextProviders = listOf(
        AiProviderType.LOCAL_RULES,
        AiProviderType.GEMINI_NANO,
        AiProviderType.GEMINI_API,
        AiProviderType.GROQ_API,
        AiProviderType.OPENROUTER_API,
        AiProviderType.XAI_API,
        AiProviderType.OPENAI_COMPATIBLE
    )
    val allAudioProviders = listOf(
        AiProviderType.GROQ_API,
        AiProviderType.GEMINI_API,
        AiProviderType.OPENAI_COMPATIBLE
    )
    val audioFallbackProviders = listOf(
        AiProviderType.LOCAL_RULES,
        AiProviderType.GROQ_API,
        AiProviderType.GEMINI_API,
        AiProviderType.OPENAI_COMPATIBLE
    )

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
                AppleHairlineDivider(startIndent = 16.dp)
                MinimalNavRow(
                    icon = Icons.Rounded.Key,
                    badgeColor = Color(0xFFFF375F),
                    title = if (de) "API-Schlüssel-Tresor" else "API Keys Vault",
                    value = if (savedCount > 0) (if (de) "$savedCount aktiv" else "$savedCount active")
                    else (if (de) "Einrichten" else "Set up"),
                    onClick = onOpenApiKeys
                )
            }
        }

        // 1. Writing Assistant (KI-Schreibhilfe)
        item {
            TaskRoutingSection(
                title = if (de) "1. KI-Schreibhilfe (Text-Aktionen)" else "1. AI Writing Assistant",
                footer = if (de) {
                    "Wähle Anbieter, Free-Account-Modell oder eigenen Modellnamen für Umformulieren, Korrigieren & Übersetzen — plus optionalen Fallback."
                } else {
                    "Mix any provider, free-tier model, or custom model ID for rewriting & proofreading — plus an automatic fallback."
                },
                de = de,
                isAudioTask = false,
                primaryProvider = prefs.aiProvider,
                primaryModel = prefs.aiModel,
                fallbackProvider = prefs.aiFallbackProvider,
                fallbackModel = prefs.aiFallbackModel,
                primaryOptions = allTextProviders,
                fallbackOptions = allTextProviders,
                secureKeyStorage = secureKeyStorage,
                onSelectPrimaryProvider = { prov ->
                    val defaultModel = AiModelCatalog.defaultTextModelFor(prov)
                    onUpdatePrefs { it.copy(aiProvider = prov, aiModel = defaultModel) }
                },
                onPrimaryModelChange = { m -> onUpdatePrefs { it.copy(aiModel = m) } },
                onSelectFallbackProvider = { prov ->
                    val defaultModel = AiModelCatalog.defaultTextModelFor(prov)
                    onUpdatePrefs { it.copy(aiFallbackProvider = prov, aiFallbackModel = defaultModel) }
                },
                onFallbackModelChange = { m -> onUpdatePrefs { it.copy(aiFallbackModel = m) } },
                onOpenApiKeys = onOpenApiKeys
            )
        }

        // 2. Dictation Cleanup (Bereinigung nach Diktat)
        item {
            TaskRoutingSection(
                title = if (de) "2. Diktat-Bereinigung" else "2. Dictation Cleanup",
                footer = if (de) {
                    "Unabhängig vom Diktat-Anbieter: z. B. Gemini für Diktat, Groq Llama 3.1 / Grok für die Glättung."
                } else {
                    "Independent from your speech provider: e.g. Gemini for speech, Groq Llama 3.1 / Grok for text cleanup."
                },
                de = de,
                isAudioTask = false,
                primaryProvider = prefs.cleanupProvider,
                primaryModel = prefs.cleanupModel,
                fallbackProvider = prefs.cleanupFallbackProvider,
                fallbackModel = prefs.cleanupFallbackModel,
                primaryOptions = allTextProviders,
                fallbackOptions = allTextProviders,
                secureKeyStorage = secureKeyStorage,
                onSelectPrimaryProvider = { prov ->
                    val defaultModel = AiModelCatalog.defaultTextModelFor(prov)
                    onUpdatePrefs { it.copy(cleanupProvider = prov, cleanupModel = defaultModel) }
                },
                onPrimaryModelChange = { m -> onUpdatePrefs { it.copy(cleanupModel = m) } },
                onSelectFallbackProvider = { prov ->
                    val defaultModel = AiModelCatalog.defaultTextModelFor(prov)
                    onUpdatePrefs { it.copy(cleanupFallbackProvider = prov, cleanupFallbackModel = defaultModel) }
                },
                onFallbackModelChange = { m -> onUpdatePrefs { it.copy(cleanupFallbackModel = m) } },
                onOpenApiKeys = onOpenApiKeys
            )
        }

        // 3. Cloud Speech-to-Text (Diktat)
        item {
            TaskRoutingSection(
                title = if (de) "3. Cloud-Diktat (Sprache → Text)" else "3. Cloud Dictation (Speech → Text)",
                footer = if (de) {
                    "Greift in den Diktat-Modi „Cloud“ & „Gemini Live“. Mit Fallback wechselt Tapwing bei Fehlern automatisch auf den zweiten Dienst."
                } else {
                    "Used by Cloud & Gemini Live dictation modes. With a fallback configured, Tapwing switches seamlessly on errors."
                },
                de = de,
                isAudioTask = true,
                primaryProvider = prefs.dictationProvider,
                primaryModel = prefs.dictationModel,
                fallbackProvider = prefs.dictationFallbackProvider,
                fallbackModel = prefs.dictationFallbackModel,
                primaryOptions = allAudioProviders,
                fallbackOptions = audioFallbackProviders,
                secureKeyStorage = secureKeyStorage,
                onSelectPrimaryProvider = { prov ->
                    val defaultModel = AiModelCatalog.defaultAudioModelFor(prov)
                    onUpdatePrefs { it.copy(dictationProvider = prov, dictationModel = defaultModel) }
                },
                onPrimaryModelChange = { m -> onUpdatePrefs { it.copy(dictationModel = m) } },
                onSelectFallbackProvider = { prov ->
                    val defaultModel = AiModelCatalog.defaultAudioModelFor(prov)
                    onUpdatePrefs { it.copy(dictationFallbackProvider = prov, dictationFallbackModel = defaultModel) }
                },
                onFallbackModelChange = { m -> onUpdatePrefs { it.copy(dictationFallbackModel = m) } },
                onOpenApiKeys = onOpenApiKeys
            )
        }

        if (prefs.aiProvider == AiProviderType.GEMINI_NANO || prefs.cleanupProvider == AiProviderType.GEMINI_NANO) item {
            Column {
                AppleSectionHeader(if (de) "Geräte-Status (Google AICore)" else "Device status (Google AICore)")
                AppleGroupedSurface {
                    val statusText = when (nanoStatus) {
                        NanoAvailability.AVAILABLE -> if (de) "Bereit auf diesem Gerät ✓" else "Ready on this device ✓"
                        NanoAvailability.DOWNLOADABLE -> if (de) "Kann über Google AICore geladen werden" else "Ready to download via Google AICore"
                        NanoAvailability.DOWNLOADING -> if (de) "Wird über Google AICore geladen…" else "Downloading via Google AICore…"
                        NanoAvailability.UNAVAILABLE -> if (de) "Nicht verfügbar (AICore fehlt)" else "Unavailable (AICore missing)"
                        null -> if (de) "Prüfe Gerät…" else "Checking device…"
                    }
                    MinimalValueRow(if (de) "Gemini Nano Status" else "Gemini Nano status", statusText)
                    if (nanoStatus == NanoAvailability.DOWNLOADABLE) {
                        AppleHairlineDivider(startIndent = 16.dp)
                        MinimalActionRow(
                            title = if (nanoDownloading) (if (de) "Starte Download…" else "Starting download…")
                            else (if (de) "Gemini Nano über AICore laden" else "Download Gemini Nano via AICore"),
                            textColor = MaterialTheme.colorScheme.primary
                        ) {
                            if (nanoDownloading) return@MinimalActionRow
                            nanoDownloading = true
                            scope.launch {
                                GeminiNanoClient.requestDownload(context, prefs.activeLocale)
                                nanoStatus = GeminiNanoClient.checkAvailability(context, prefs.activeLocale)
                                nanoDownloading = false
                            }
                        }
                    }
                }
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
private fun TaskRoutingSection(
    title: String,
    footer: String,
    de: Boolean,
    isAudioTask: Boolean,
    primaryProvider: AiProviderType,
    primaryModel: String,
    fallbackProvider: AiProviderType,
    fallbackModel: String,
    primaryOptions: List<AiProviderType>,
    fallbackOptions: List<AiProviderType>,
    secureKeyStorage: SecureKeyStorage,
    onSelectPrimaryProvider: (AiProviderType) -> Unit,
    onPrimaryModelChange: (String) -> Unit,
    onSelectFallbackProvider: (AiProviderType) -> Unit,
    onFallbackModelChange: (String) -> Unit,
    onOpenApiKeys: () -> Unit
) {
    val primaryPresets = if (isAudioTask) {
        AiModelCatalog.audioModelsFor(primaryProvider)
    } else {
        AiModelCatalog.textModelsFor(primaryProvider)
    }
    val fallbackPresets = if (isAudioTask) {
        AiModelCatalog.audioModelsFor(fallbackProvider)
    } else {
        AiModelCatalog.textModelsFor(fallbackProvider)
    }
    var showFallback by rememberSaveable(title) {
        mutableStateOf(fallbackProvider != AiProviderType.LOCAL_RULES)
    }

    Column {
        AppleSectionHeader(title)
        AppleGroupedSurface {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (de) "Primärer Anbieter" else "Primary provider",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    primaryOptions.forEach { prov ->
                        val hasKey = prov == AiProviderType.LOCAL_RULES ||
                            prov == AiProviderType.GEMINI_NANO ||
                            secureKeyStorage.hasKey(prov)
                        FilterChip(
                            selected = primaryProvider == prov,
                            onClick = { onSelectPrimaryProvider(prov) },
                            label = {
                                Text(
                                    text = if (hasKey) AiModelCatalog.displayName(prov, de)
                                    else "${AiModelCatalog.displayName(prov, de)} · 🔑",
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                if (primaryProvider in SecureKeyStorage.CLOUD_PROVIDERS && !secureKeyStorage.hasKey(primaryProvider)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
                            .clickable(onClick = onOpenApiKeys)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (de) "Noch kein API-Key für ${AiModelCatalog.displayName(primaryProvider, true)} gespeichert"
                            else "No API key saved for ${AiModelCatalog.displayName(primaryProvider, false)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (de) "Tresor →" else "Vault →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (primaryPresets.isNotEmpty() || primaryProvider in SecureKeyStorage.CLOUD_PROVIDERS) {
                    if (primaryPresets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            primaryPresets.forEach { spec ->
                                val tag = if (spec.isFreeTier) "FREE · " else ""
                                FilterChip(
                                    selected = primaryModel.trim() == spec.id,
                                    onClick = { onPrimaryModelChange(spec.id) },
                                    label = { Text("$tag${spec.label}", fontSize = 12.sp) }
                                )
                            }
                        }
                        val activeSpec = primaryPresets.firstOrNull { it.id == primaryModel.trim() }
                        if (activeSpec != null) {
                            Text(
                                text = if (de) activeSpec.noteDe else activeSpec.noteEn,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    AiModelReference(provider = primaryProvider, model = primaryModel)
                    AppleInlineField(
                        value = primaryModel,
                        onValueChange = onPrimaryModelChange,
                        placeholder = if (de) "Modell-ID eingeben" else "Enter model ID"
                    )
                }

                if (primaryProvider == AiProviderType.GEMINI_NANO) AiModelReference(primaryProvider)
                AppleHairlineDivider(startIndent = 0.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFallback = !showFallback }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (de) {
                            "Fallback: ${AiModelCatalog.displayName(fallbackProvider, true)}"
                        } else {
                            "Fallback: ${AiModelCatalog.displayName(fallbackProvider, false)}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (showFallback) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showFallback) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fallbackOptions.forEach { prov ->
                                FilterChip(
                                    selected = fallbackProvider == prov,
                                    onClick = { onSelectFallbackProvider(prov) },
                                    label = { Text(AiModelCatalog.displayName(prov, de), fontSize = 12.sp) }
                                )
                            }
                        }
                        if (fallbackPresets.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                fallbackPresets.forEach { spec ->
                                    FilterChip(
                                        selected = fallbackModel.trim() == spec.id,
                                        onClick = { onFallbackModelChange(spec.id) },
                                        label = { Text(spec.label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        if (fallbackProvider == AiProviderType.GEMINI_NANO) AiModelReference(fallbackProvider)
                        if (fallbackProvider in SecureKeyStorage.CLOUD_PROVIDERS) {
                            AiModelReference(provider = fallbackProvider, model = fallbackModel)
                            AppleInlineField(
                                value = fallbackModel,
                                onValueChange = onFallbackModelChange,
                                placeholder = if (de) "Fallback-Modell-ID (optional)" else "Fallback model ID (optional)"
                            )
                        }
                    }
                }
            }
        }
        AppleSectionFooter(footer)
    }
}

@Composable
private fun AiModelReference(provider: AiProviderType, model: String = "") {
    val url = AiModelCatalog.modelsUrl(provider) ?: return
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { context.openSettingsLink(url) }, modifier = Modifier.weight(1f, fill = false)) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ai_model_catalog))
        }
        if (model.isNotBlank()) {
            IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Model ID", model.trim()))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, R.string.ai_model_copied, Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.ai_model_copy))
            }
        }
    }
}

@Composable
private fun ApiKeysVaultScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    secureKeyStorage: SecureKeyStorage,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vaultRevision by remember { mutableIntStateOf(0) }
    var globalNotice by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (globalNotice != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = globalNotice!!,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        item {
            AppleSectionFooter(
                if (de) {
                    "Alle Schlüssel liegen Hardware-verschlüsselt im Android Keystore. Du kannst Anbieter beliebig mischen (z. B. Gemini fürs Diktat + Groq/Grok für Text) und Schlüssel jederzeit anzeigen, kopieren oder testen."
                } else {
                    "All API keys are encrypted via hardware Android Keystore. Mix providers freely across dictation, cleanup, and writing tools, or view, copy, and test keys anytime."
                }
            )
        }

        SecureKeyStorage.CLOUD_PROVIDERS.forEach { provider ->
            item(key = "${provider.name}_$vaultRevision") {
                var savedKey by remember(vaultRevision) { mutableStateOf(secureKeyStorage.readKey(provider)) }
                var keyInput by remember { mutableStateOf("") }
                var revealKey by remember { mutableStateOf(false) }
                var testing by remember { mutableStateOf(false) }
                var testResult by remember { mutableStateOf<String?>(null) }

                val isFreeProvider = provider in setOf(
                    AiProviderType.GEMINI_API,
                    AiProviderType.GROQ_API,
                    AiProviderType.OPENROUTER_API
                )
                val signupUrl = AiModelCatalog.signupUrl(provider)

                Column {
                    AppleSectionHeader(
                        text = AiModelCatalog.displayName(provider, de),
                        trailingActionText = if (signupUrl.isNotEmpty()) (if (de) "Key holen ↗" else "Get key ↗") else null,
                        onTrailingAction = if (signupUrl.isNotEmpty()) {
                            { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(signupUrl))) } }
                        } else null
                    )
                    AppleGroupedSurface {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AiModelReference(provider)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (savedKey.isNotBlank()) {
                                        if (de) "Gespeichert ✓" else "Saved ✓"
                                    } else {
                                        if (de) "Nicht hinterlegt" else "Not configured"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (savedKey.isNotBlank()) Color(0xFF34C759)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isFreeProvider) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Free Tier",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = AiModelCatalog.freeTierSummary(provider, de),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (savedKey.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (revealKey) savedKey else secureKeyStorage.maskedPreview(provider),
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { revealKey = !revealKey },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (de) "Anzeigen" else "Reveal",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clip = ClipData.newPlainText("Tapwing API Key", savedKey).apply {
                                                    description.extras = PersistableBundle().apply {
                                                        putBoolean(SENSITIVE_CLIP_EXTRA, true)
                                                    }
                                                }
                                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                                                globalNotice = if (de) {
                                                    "${AiModelCatalog.displayName(provider, true)}-Schlüssel kopiert"
                                                } else {
                                                    "Copied ${AiModelCatalog.displayName(provider, false)} key"
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = if (de) "Kopieren" else "Copy",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                secureKeyStorage.deleteKey(provider)
                                                savedKey = ""
                                                revealKey = false
                                                testResult = if (de) "Schlüssel entfernt." else "Key removed."
                                                vaultRevision++
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = if (de) "Löschen" else "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (provider == AiProviderType.OPENAI_COMPATIBLE) {
                                AppleInlineField(
                                    value = prefs.aiCustomEndpoint,
                                    onValueChange = { ep -> onUpdatePrefs { it.copy(aiCustomEndpoint = ep) } },
                                    placeholder = "https://api.openai.com/v1/chat/completions"
                                )
                            }

                            AppleInlineField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                placeholder = if (savedKey.isNotBlank()) {
                                    if (de) "Neuen Schlüssel einfügen zum Ersetzen" else "Paste new key to replace"
                                } else {
                                    if (de) "API-Schlüssel einfügen" else "Paste API key"
                                },
                                isPassword = true
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (!SecureKeyStorage.isValidApiKey(keyInput)) {
                                            testResult = if (de) "Ungültige Zeichen oder Leerzeichen im Schlüssel."
                                            else "Invalid characters or spaces in key."
                                        } else {
                                            secureKeyStorage.saveKey(provider, keyInput)
                                            savedKey = secureKeyStorage.readKey(provider)
                                            keyInput = ""
                                            testResult = if (de) "Sicher im Tresor gespeichert." else "Saved securely in vault."
                                            vaultRevision++
                                        }
                                    },
                                    enabled = keyInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (de) "Speichern" else "Save", fontSize = 13.sp)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val keyToTest = savedKey
                                        if (keyToTest.isBlank()) return@FilledTonalButton
                                        testing = true
                                        testResult = null
                                        scope.launch {
                                            val modelToTest = when (provider) {
                                                prefs.aiProvider -> prefs.aiModel
                                                prefs.cleanupProvider -> prefs.cleanupModel
                                                else -> AiModelCatalog.defaultTextModelFor(provider)
                                            }.ifBlank { AiModelCatalog.defaultTextModelFor(provider) }
                                            val res = CloudAiClient.testConnection(
                                                provider = provider,
                                                apiKey = keyToTest,
                                                model = modelToTest,
                                                customEndpoint = prefs.aiCustomEndpoint,
                                                locale = prefs.activeLocale
                                            )
                                            testing = false
                                            testResult = when (res) {
                                                is CloudAiResult.Success -> "✓ ${res.providerLabel}: ${res.text}"
                                                is CloudAiResult.Failure -> "✗ ${res.message}"
                                            }
                                        }
                                    },
                                    enabled = savedKey.isNotBlank() && !testing,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (testing) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(if (de) "Testen" else "Test", fontSize = 13.sp)
                                    }
                                }
                            }

                            if (testResult != null) {
                                Text(
                                    text = testResult!!,
                                    fontSize = 12.sp,
                                    color = if (testResult!!.startsWith("✗")) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
                            val friendlyBadge = when (spec.id) {
                                "whisper-tiny-q5_1" -> if (de) "Blitzschnell · 32 MB" else "Fastest · 32 MB"
                                "whisper-base-q5_1" -> if (de) "Ausgewogen · 60 MB" else "Balanced · 60 MB"
                                "whisper-small-q5_1" -> if (de) "Sehr genau · 190 MB" else "High accuracy · 190 MB"
                                "whisper-large-v3-turbo-q5_0" -> if (de) "Studio-Qualität · 574 MB" else "Studio · 574 MB"
                                else -> "${spec.approxSizeMb} MB"
                            }
                            Text(
                                text = if (showTechnicalDetails) "${spec.approxSizeMb} MB · ${spec.minRamGb}+ GB RAM" else friendlyBadge,
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
            .heightIn(min = 52.dp)
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp)
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
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AppleSwitch(checked = checked)
    }
}

@Composable
private fun SettingsDisclosure(title: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(240), label = "disclosureChevron")
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
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
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
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
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
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

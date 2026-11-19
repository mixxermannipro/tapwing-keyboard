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
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
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
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.CloudAiResult
import com.tapwing.keyboard.clipboard.ScreenshotMonitor
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.ClipboardRetention
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.QuickBarAction
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.ocr.ScanTextActivity
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.isHighlighted
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapwingSettingsApp(
    prefs: TapwingPrefs,
    secureKeyStorage: SecureKeyStorage,
    dictionaryStore: AdaptiveDictionaryStore,
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
                dictRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val de = prefs.activeLocale == "de"

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (route != SettingsRoute.HOME) {
                val title = when (route) {
                    SettingsRoute.HOME -> ""
                    SettingsRoute.APPEARANCE -> if (de) "Design & Größe" else "Theme & Size"
                    SettingsRoute.QUICK_BAR -> if (de) "Schnellleiste" else "Quick Bar"
                    SettingsRoute.LANGUAGES -> if (de) "Sprachen & Layout" else "Languages"
                    SettingsRoute.DICTIONARY -> if (de) "Wörterbuch & Lernen" else "Dictionary"
                    SettingsRoute.CORRECTIONS -> if (de) "Korrekturen" else "Corrections"
                    SettingsRoute.VOICE -> if (de) "Spracheingabe" else "Voice"
                    SettingsRoute.WRITING_TOOLS -> if (de) "KI-Schreibhilfe" else "Writing Tools"
                    SettingsRoute.CLIPBOARD -> if (de) "Zwischenablage" else "Clipboard & OCR"
                    SettingsRoute.EMOJI -> "Emoji"
                    SettingsRoute.ABOUT -> if (de) "Über Tapwing" else "About"
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
                        dictWordCount = remember(dictRevision) { dictionaryStore.stats().customWordCount },
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
                        onDictionaryChanged = { dictRevision++ },
                        onUpdatePrefs = onUpdatePrefs
                    )
                    SettingsRoute.CORRECTIONS -> CorrectionsScreen(
                        prefs = prefs,
                        de = de,
                        onOpenQuickBar = { navigateTo(SettingsRoute.QUICK_BAR) },
                        onUpdatePrefs = onUpdatePrefs
                    )
                    SettingsRoute.VOICE -> VoiceSettingsScreen(prefs, de, onUpdatePrefs)
                    SettingsRoute.WRITING_TOOLS -> WritingToolsSettingsScreen(prefs, de, secureKeyStorage, onUpdatePrefs)
                    SettingsRoute.CLIPBOARD -> ClipboardSettingsScreen(prefs, de, onUpdatePrefs)
                    SettingsRoute.EMOJI -> EmojiSettingsScreen(prefs, de, onUpdatePrefs)
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
    val value: String,
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
    dictWordCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onToggleAppleMode: (Boolean) -> Unit,
    onNavigate: (SettingsRoute) -> Unit
) {
    val retentionValue = when (prefs.clipboardRetention) {
        ClipboardRetention.HOUR_1 -> if (de) "1 Std." else "1 hr"
        ClipboardRetention.HOURS_24 -> if (de) "24 Std." else "24 hr"
        ClipboardRetention.DAYS_7 -> if (de) "7 Tage" else "7 days"
        ClipboardRetention.DAYS_30 -> if (de) "30 Tage" else "30 days"
        ClipboardRetention.NEVER -> if (de) "Nie" else "Never"
    }
    val aiValue = when (prefs.aiProvider) {
        AiProviderType.LOCAL_RULES -> if (de) "Lokal" else "Local"
        AiProviderType.GEMINI_API -> "Gemini"
        AiProviderType.OPENAI_COMPATIBLE -> "API"
    }

    val appearanceItems: List<HomeRowItem> = listOf(
        HomeToggleItem(
            title = if (de) "Apple Modus (Liquid Glass)" else "Apple Mode (Liquid Glass)",
            checked = prefs.appleModeEnabled,
            icon = Icons.Rounded.Smartphone,
            badgeColor = Color(0xFF5E5CE6),
            keywords = "apple liquid glass design glas",
            onCheckedChange = onToggleAppleMode
        ),
        HomeNavItem(
            route = SettingsRoute.APPEARANCE,
            title = if (de) "Design & Größe" else "Theme & Size",
            value = "${prefs.keyboardScalePercent}%",
            icon = Icons.Rounded.Palette,
            badgeColor = Color(0xFFAF52DE),
            keywords = "theme design farbe größe höhe abstand einhand"
        ),
        HomeNavItem(
            route = SettingsRoute.QUICK_BAR,
            title = if (de) "Schnellleiste" else "Quick Bar",
            value = "${prefs.quickBarActions.size}",
            icon = Icons.Rounded.Tune,
            badgeColor = Color(0xFF5AC8FA),
            keywords = "leiste schnellleiste toolbar symbole icons reihenfolge"
        ),
        HomeNavItem(
            route = SettingsRoute.LANGUAGES,
            title = if (de) "Sprachen & Layout" else "Languages",
            value = if (prefs.activeLocale == "de") "Deutsch" else "English",
            icon = Icons.Rounded.Language,
            badgeColor = Color(0xFF007AFF),
            keywords = "sprache qwertz qwerty zahlenreihe umlaute leertaste"
        )
    )

    val intelligenceItems: List<HomeRowItem> = listOf(
        HomeNavItem(
            route = SettingsRoute.DICTIONARY,
            title = if (de) "Wörterbuch & Lernen" else "Dictionary",
            value = "$dictWordCount",
            icon = Icons.Rounded.Book,
            badgeColor = Color(0xFF34C759),
            keywords = "wörterbuch lernen zip gboard pixelboard kürzel import export"
        ),
        HomeNavItem(
            route = SettingsRoute.CORRECTIONS,
            title = if (de) "Korrekturen" else "Corrections",
            value = if (prefs.autoCorrect) (if (de) "An" else "On") else (if (de) "Aus" else "Off"),
            icon = Icons.Rounded.Spellcheck,
            badgeColor = Color(0xFF30B0C7),
            keywords = "autokorrektur rechner math inkognito undo grammatik"
        ),
        HomeNavItem(
            route = SettingsRoute.VOICE,
            title = if (de) "Spracheingabe" else "Voice typing",
            value = if (prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP) "Rambler" else "Standard",
            icon = Icons.Rounded.Mic,
            badgeColor = Color(0xFFFF9500),
            keywords = "sprache diktat rambler füllwörter mikrofon"
        ),
        HomeNavItem(
            route = SettingsRoute.WRITING_TOOLS,
            title = if (de) "KI-Schreibhilfe" else "Writing Tools",
            value = aiValue,
            icon = Icons.Rounded.AutoAwesome,
            badgeColor = Color(0xFF5856D6),
            keywords = "ki ai gemini openai key schreibhilfe"
        )
    )

    val toolsItems: List<HomeRowItem> = listOf(
        HomeNavItem(
            route = SettingsRoute.CLIPBOARD,
            title = if (de) "Zwischenablage & OCR" else "Clipboard & OCR",
            value = retentionValue,
            icon = Icons.Rounded.ContentPaste,
            badgeColor = Color(0xFF32ADE6),
            keywords = "zwischenablage clipboard ocr kamera scan screenshot"
        ),
        HomeNavItem(
            route = SettingsRoute.EMOJI,
            title = "Emoji",
            value = "${prefs.emojiScale}",
            icon = Icons.Rounded.EmojiEmotions,
            badgeColor = Color(0xFFFF9F0A),
            keywords = "emoji größe vorschläge"
        ),
        HomeNavItem(
            route = SettingsRoute.ABOUT,
            title = if (de) "Über Tapwing" else "About Tapwing",
            value = "v${BuildConfig.VERSION_NAME}",
            icon = Icons.Rounded.Info,
            badgeColor = Color(0xFF8E8E93),
            keywords = "über info version datenschutz lizenz system"
        )
    )

    val sections = listOf(
        (if (de) "Darstellung & Tastatur" else "Appearance & Keyboard") to appearanceItems,
        (if (de) "Tippen & Intelligenz" else "Typing & Intelligence") to intelligenceItems,
        (if (de) "Werkzeuge & Info" else "Tools & Info") to toolsItems
    )

    val q = searchQuery.trim().lowercase()

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
                    placeholder = if (de) "Suchen" else "Search"
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
                                    if (de) "Tapwing in Android aktivieren" else "Enable Tapwing in Android"
                                } else {
                                    if (de) "Tapwing als Tastatur auswählen" else "Select Tapwing as keyboard"
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
                        placeholder = if (de) "Tastatur hier testen…" else "Test keyboard here…",
                        leadingIcon = Icons.Rounded.Keyboard,
                        containerAlpha = 0f,
                        fieldHeight = 46.dp
                    )
                }
            }
        }

        sections.forEach { (header, rows) ->
            val filtered = rows.filter { item ->
                q.isEmpty() || item.title.lowercase().contains(q) || item.keywords.contains(q)
            }
            if (filtered.isNotEmpty()) {
                item(key = header) {
                    Column {
                        AppleSectionHeader(header)
                        AppleGroupedSurface {
                            filtered.forEachIndexed { idx, item ->
                                when (item) {
                                    is HomeToggleItem -> MinimalToggleRow(
                                        title = item.title,
                                        checked = item.checked,
                                        icon = item.icon,
                                        badgeColor = item.badgeColor,
                                        onCheckedChange = item.onCheckedChange
                                    )
                                    is HomeNavItem -> MinimalNavRow(
                                        icon = item.icon,
                                        badgeColor = item.badgeColor,
                                        title = item.title,
                                        value = item.value,
                                        onClick = { onNavigate(item.route) }
                                    )
                                }
                                if (idx < filtered.lastIndex) {
                                    AppleHairlineDivider(startIndent = 58.dp)
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
                AppleSectionHeader(if (de) "Aktive Sprache" else "Active language")
                AppleGroupedSurface {
                    MinimalCheckRow("Deutsch (QWERTZ)", selected = prefs.activeLocale == "de") {
                        onUpdatePrefs { it.copy(activeLocale = "de") }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalCheckRow("English (QWERTY)", selected = prefs.activeLocale == "en") {
                        onUpdatePrefs { it.copy(activeLocale = "en") }
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Tastenbelegung" else "Layout")
                AppleGroupedSurface {
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
                    if (de) "Ohne direkte Umlaut-Tasten bleibt die 1:1 PixelBoard 10er-Reihe aktiv (Umlaute per Gedrückthalten)."
                    else "Keep direct umlauts off for the compact 10-column PixelBoard row."
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
    onDictionaryChanged: () -> Unit,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var newWordInput by remember { mutableStateOf("") }
    var newShortcutTrigger by remember { mutableStateOf("") }
    var newShortcutExpansion by remember { mutableStateOf("") }

    val stats = remember(revision) { dictionaryStore.stats() }
    val customWords = remember(revision) { dictionaryStore.listCustomWords().take(25) }
    val shortcuts = remember(revision) { dictionaryStore.listShortcuts().take(20) }

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
                AppleSectionFooter(if (de) "Dynamisch eingebaut: /datum und /zeit" else "Built-in dynamic triggers: /date and /time")
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
                    MinimalToggleRow(if (de) "Adaptives Lernen" else "Adaptive learning", prefs.learningEnabled) { v ->
                        onUpdatePrefs { it.copy(learningEnabled = v) }
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
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                AppleSectionHeader(if (de) "Diktat-Modus" else "Dictation mode")
                AppleGroupedSurface {
                    MinimalCheckRow(
                        title = if (de) "Natürliche Bereinigung (Rambler)" else "Natural Cleanup (Rambler)",
                        selected = prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
                    ) { onUpdatePrefs { it.copy(voiceMode = VoiceDictationMode.NATURAL_CLEANUP) } }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalCheckRow(
                        title = "Standard",
                        selected = prefs.voiceMode == VoiceDictationMode.STANDARD
                    ) { onUpdatePrefs { it.copy(voiceMode = VoiceDictationMode.STANDARD) } }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Optionen" else "Options")
                AppleGroupedSurface {
                    MinimalToggleRow(if (de) "Füllwörter entfernen" else "Remove filler words", prefs.voiceRemoveFillers) { v ->
                        onUpdatePrefs { it.copy(voiceRemoveFillers = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Auto-Zeichensetzung" else "Auto-punctuation", prefs.voiceAutoPunctuate) { v ->
                        onUpdatePrefs { it.copy(voiceAutoPunctuate = v) }
                    }
                    AppleHairlineDivider(startIndent = 16.dp)
                    MinimalToggleRow(if (de) "Strikte Sprachsperre" else "Strict language lock", prefs.voiceStrictLanguageLock) { v ->
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
                            Text(if (de) "Drehungs-Schonfrist" else "Rotation grace", fontSize = 15.sp)
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
    }
}

@Composable
private fun WritingToolsSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    secureKeyStorage: SecureKeyStorage,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val scope = rememberCoroutineScope()
    var apiKeyInput by remember { mutableStateOf("") }
    var maskedKey by remember { mutableStateOf(secureKeyStorage.maskedPreview()) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

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
                MinimalToggleRow(if (de) "Alle Tastaturen unterstützen" else "Support all keyboards", prefs.aiSupportAllKeyboards) { v ->
                    onUpdatePrefs { it.copy(aiSupportAllKeyboards = v) }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "KI-Backend" else "Backend")
                AppleGroupedSurface {
                    val providers = listOf(
                        AiProviderType.LOCAL_RULES to (if (de) "On-Device Regeln (Offline)" else "On-Device Rules (Offline)"),
                        AiProviderType.GEMINI_API to "Google Gemini API",
                        AiProviderType.OPENAI_COMPATIBLE to (if (de) "OpenAI-kompatibler Endpunkt" else "OpenAI-compatible")
                    )
                    providers.forEachIndexed { idx, (type, label) ->
                        MinimalCheckRow(label, selected = prefs.aiProvider == type) {
                            onUpdatePrefs { it.copy(aiProvider = type) }
                        }
                        if (idx < providers.lastIndex) AppleHairlineDivider(startIndent = 16.dp)
                    }
                }
            }
        }

        item {
            Column {
                AppleSectionHeader(if (de) "Modell & API-Key" else "Model & API Key")
                AppleGroupedSurface {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppleInlineField(
                            value = prefs.aiModel,
                            onValueChange = { m -> onUpdatePrefs { it.copy(aiModel = m) } },
                            placeholder = if (de) "Modellname" else "Model name"
                        )
                        if (prefs.aiProvider == AiProviderType.OPENAI_COMPATIBLE) {
                            AppleInlineField(
                                value = prefs.aiCustomEndpoint,
                                onValueChange = { ep -> onUpdatePrefs { it.copy(aiCustomEndpoint = ep) } },
                                placeholder = "https://api.openai.com/v1/chat/completions"
                            )
                        }
                        AppleInlineField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            placeholder = if (de) "Neuen API-Key eingeben…" else "Enter new API key…",
                            isPassword = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    secureKeyStorage.saveApiKey(apiKeyInput)
                                    maskedKey = secureKeyStorage.maskedPreview()
                                    apiKeyInput = ""
                                    testStatus = if (de) "Key sicher im Android Keystore gespeichert." else "Saved in Android Keystore."
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (de) "Key speichern" else "Save", fontSize = 13.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    secureKeyStorage.clearApiKey()
                                    maskedKey = ""
                                    testStatus = if (de) "API-Key gelöscht." else "Cleared."
                                },
                                enabled = maskedKey.isNotEmpty(),
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
                            text = if (de) "Verbindung testen" else "Test backend",
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
                    if (maskedKey.isNotEmpty()) {
                        if (de) "Android Keystore: $maskedKey" else "Android Keystore: $maskedKey"
                    } else {
                        if (de) "Kein API-Key nötig im On-Device-Modus." else "No API key needed for local rules."
                    }
                )
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
                MinimalToggleRow(if (de) "Erweiterte Zwischenablage" else "Extended clipboard", prefs.clipboardEnabled) { v ->
                    onUpdatePrefs { it.copy(clipboardEnabled = v) }
                }
            }
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

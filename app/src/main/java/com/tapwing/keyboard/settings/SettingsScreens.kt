package com.tapwing.keyboard.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tapwing.keyboard.BuildConfig
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.CloudAiResult
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.ClipboardRetention
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.OneHandedMode
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.ocr.ScanTextActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SettingsRoute {
    HOME,
    LANGUAGES,
    DICTIONARY,
    APPEARANCE,
    CORRECTIONS,
    VOICE,
    WRITING_TOOLS,
    CLIPBOARD,
    EMOJI,
    ADVANCED,
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
    var route by remember { mutableStateOf(SettingsRoute.HOME) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var testInputText by remember { mutableStateOf("") }

    var imeEnabled by remember { mutableStateOf(false) }
    var imeSelected by remember { mutableStateOf(false) }

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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val de = prefs.activeLocale == "de"
    val apple = prefs.appleModeEnabled

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = if (apple) 0.94f else 1f)
                ),
                title = {
                    val title = when (route) {
                        SettingsRoute.HOME -> if (apple) "" else "Tapwing"
                        SettingsRoute.LANGUAGES -> if (de) "Sprachen & Layouts" else "Languages & Layouts"
                        SettingsRoute.DICTIONARY -> if (de) "Wörterbuch & Lernen" else "Dictionary & Learning"
                        SettingsRoute.APPEARANCE -> if (de) "Design & Größe" else "Theme & Size"
                        SettingsRoute.CORRECTIONS -> if (de) "Korrekturen & Leiste" else "Corrections & Strip"
                        SettingsRoute.VOICE -> if (de) "Spracheingabe" else "Voice typing"
                        SettingsRoute.WRITING_TOOLS -> if (de) "KI-Schreibhilfe" else "AI Writing Tools"
                        SettingsRoute.CLIPBOARD -> if (de) "Zwischenablage & OCR" else "Clipboard & OCR"
                        SettingsRoute.EMOJI -> "Emoji"
                        SettingsRoute.ADVANCED -> if (de) "Erweiterte Einstellungen" else "Advanced settings"
                        SettingsRoute.ABOUT -> if (de) "Über Tapwing" else "About Tapwing"
                    }
                    Text(
                        text = title,
                        fontWeight = if (apple) FontWeight.SemiBold else FontWeight.SemiBold,
                        fontSize = if (apple) 18.sp else 20.sp
                    )
                },
                navigationIcon = {
                    if (route != SettingsRoute.HOME) {
                        IconButton(onClick = { route = SettingsRoute.HOME }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (route == SettingsRoute.HOME) {
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        }
                    }
                    if (route == SettingsRoute.ADVANCED) {
                        IconButton(onClick = {
                            refreshImeStatus()
                            Toast.makeText(
                                context,
                                if (de) "Einstellungen aktualisiert" else "Preferences synced",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                        }
                    }
                }
            )
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
                    val forward = targetState != SettingsRoute.HOME
                    val slideIn = slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { width -> if (forward) width / 3 else -width / 3 } + fadeIn(tween(180))
                    val slideOut = slideOutHorizontally(tween(150)) { width ->
                        if (forward) -width / 5 else width / 5
                    } + fadeOut(tween(130))
                    slideIn togetherWith slideOut
                },
                label = "settingsNavigation"
            ) { activeRoute ->
                when (activeRoute) {
                    SettingsRoute.HOME -> HomeSettingsScreen(
                        prefs = prefs,
                        de = de,
                        apple = apple,
                        imeEnabled = imeEnabled,
                        imeSelected = imeSelected,
                        searchVisible = searchVisible,
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
                        onNavigate = { route = it }
                    )
                    SettingsRoute.LANGUAGES -> LanguagesScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.DICTIONARY -> DictionaryLearningScreen(prefs, de, apple, dictionaryStore, onUpdatePrefs)
                    SettingsRoute.APPEARANCE -> AppearanceScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.CORRECTIONS -> CorrectionsScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.VOICE -> VoiceSettingsScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.WRITING_TOOLS -> WritingToolsSettingsScreen(prefs, de, apple, secureKeyStorage, onUpdatePrefs)
                    SettingsRoute.CLIPBOARD -> ClipboardSettingsScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.EMOJI -> EmojiSettingsScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.ADVANCED -> AdvancedSettingsScreen(prefs, de, apple, onUpdatePrefs)
                    SettingsRoute.ABOUT -> AboutSettingsScreen(de, apple)
                }
            }
        }
    }
}

private data class HomeNavEntry(
    val route: SettingsRoute,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val badgeColor: Color
)

@Composable
private fun HomeSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    searchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onToggleAppleMode: (Boolean) -> Unit,
    onNavigate: (SettingsRoute) -> Unit
) {
    val entries = listOf(
        HomeNavEntry(
            SettingsRoute.LANGUAGES,
            if (de) "Sprachen & Layouts" else "Languages & Layouts",
            if (de) "Aktuell: ${if (prefs.activeLocale == "de") "Deutsch (QWERTZ)" else "English (QWERTY)"}" else "Active: ${prefs.activeLocale.uppercase()}",
            Icons.Rounded.Language,
            Color(0xFF007AFF)
        ),
        HomeNavEntry(
            SettingsRoute.DICTIONARY,
            if (de) "Wörterbuch & Lernen" else "Dictionary & Learning",
            if (de) "Eigenes Wörterbuch importieren, Kürzel & adaptives Lernen" else "Import dictionary, shortcuts & adaptive learning",
            Icons.Rounded.Book,
            Color(0xFF34C759)
        ),
        HomeNavEntry(
            SettingsRoute.APPEARANCE,
            if (de) "Design & Größe" else "Theme & Size",
            if (de) "Höhe (${prefs.keyboardScalePercent}%), 1:1 PixelBoard & Apple Liquid Glass" else "Height (${prefs.keyboardScalePercent}%), 1:1 Layout & Liquid Glass",
            Icons.Rounded.Palette,
            Color(0xFFAF52DE)
        ),
        HomeNavEntry(
            SettingsRoute.CORRECTIONS,
            if (de) "Korrekturen & Power-Tools" else "Corrections & Power Tools",
            if (de) "Autokorrektur, Inline-Rechner, Inkognito & Proactive Undo" else "Autocorrect, inline math, incognito & Proactive Undo",
            Icons.Rounded.Spellcheck,
            Color(0xFF30B0C7)
        ),
        HomeNavEntry(
            SettingsRoute.VOICE,
            if (de) "Spracheingabe" else "Voice typing",
            if (de) "Natürliches Diktat (Rambler-Stil), Füllwörter, Sprachsperre" else "Natural dictation cleanup, fillers, language lock",
            Icons.Rounded.Mic,
            Color(0xFFFF9500)
        ),
        HomeNavEntry(
            SettingsRoute.WRITING_TOOLS,
            if (de) "KI-Schreibhilfe" else "AI Writing Tools",
            if (de) "Korrektur, Tonfall-Rewrite & eigener API-Schlüssel" else "Proofread, tone rewrite & optional API key",
            Icons.Rounded.AutoAwesome,
            Color(0xFF5856D6)
        ),
        HomeNavEntry(
            SettingsRoute.CLIPBOARD,
            if (de) "Zwischenablage & Scan Text" else "Clipboard & Scan Text",
            if (de) "${prefs.clipboardMaxItems} Einträge, Aufbewahrung & Kamera-OCR" else "${prefs.clipboardMaxItems} clips, retention & camera OCR",
            Icons.Rounded.ContentPaste,
            Color(0xFF32ADE6)
        ),
        HomeNavEntry(
            SettingsRoute.EMOJI,
            "Emoji",
            if (de) "Größe (${prefs.emojiScale} sp) & Vorschläge" else "Scale (${prefs.emojiScale} sp) & suggestions",
            Icons.Rounded.EmojiEmotions,
            Color(0xFFFFCC00)
        )
    )

    val q = searchQuery.trim().lowercase()
    val filteredMain = entries.filter { e ->
        q.isEmpty() || e.title.lowercase().contains(q) || e.summary.lowercase().contains(q)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (apple) 14.dp else 12.dp)
    ) {
        if (apple) {
            item {
                Text(
                    text = if (de) "Einstellungen" else "Settings",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }

        if (searchVisible || apple) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(if (apple) 14.dp else 16.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(if (de) "Einstellungen durchsuchen…" else "Search settings…") }
                )
            }
        }

        // Apple Mode Quick Switch right at the top
        item {
            if (apple) AppleSectionHeader(if (de) "Darstellung" else "Experience")
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Apple Modus (Liquid Glass)" else "Apple Mode (Liquid Glass)",
                    summary = if (de) "Liquid-Glass-Tasten, Feder-Animationen & iOS-Einstellungen"
                    else "Liquid Glass keycaps, spring animations & iOS settings",
                    checked = prefs.appleModeEnabled,
                    apple = apple,
                    icon = Icons.Rounded.Smartphone,
                    badgeColor = Color(0xFF1D1D1F)
                ) { v -> onToggleAppleMode(v) }
            }
        }

        // IME Activation Card
        item {
            GroupedCard(apple) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (imeSelected) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (imeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                imeSelected -> if (de) "Tapwing ist aktiv" else "Tapwing is active"
                                imeEnabled -> if (de) "Tapwing aktiviert – jetzt auswählen" else "Tapwing enabled – select now"
                                else -> if (de) "Tapwing in Android aktivieren" else "Enable Tapwing in Android"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (de) "Eigenständige Tastatur-App (com.tapwing.keyboard)" else "Standalone keyboard app (com.tapwing.keyboard)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenImeSystemSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (de) "1. Aktivieren" else "1. Enable", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onShowImePicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (de) "2. Auswählen" else "2. Select", fontSize = 12.sp)
                    }
                }
            }
        }

        // Interactive Test Field
        item {
            GroupedCard(apple) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (de) "Tastatur direkt hier testen" else "Test keyboard right here",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = testInputText,
                        onValueChange = onTestInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (de) "Tippe z. B. mfg, 24*7= oder teste Sprache & OCR…"
                                else "Try typing mfg, 24*7=, voice or OCR…"
                            )
                        }
                    )
                }
            }
        }

        if (filteredMain.isNotEmpty()) {
            item {
                if (apple) AppleSectionHeader(if (de) "Allgemein & Funktionen" else "General & Features")
                GroupedCard(apple) {
                    filteredMain.forEachIndexed { idx, entry ->
                        NavRow(
                            icon = entry.icon,
                            badgeColor = entry.badgeColor,
                            apple = apple,
                            title = entry.title,
                            summary = entry.summary
                        ) {
                            onNavigate(entry.route)
                        }
                        if (idx < filteredMain.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        item {
            GroupedCard(apple) {
                NavRow(
                    icon = Icons.Rounded.Info,
                    badgeColor = Color(0xFF8E8E93),
                    apple = apple,
                    title = if (de) "Über Tapwing" else "About",
                    summary = "Version ${BuildConfig.VERSION_NAME} • Apache-2.0"
                ) {
                    onNavigate(SettingsRoute.ABOUT)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp)
                NavRow(
                    icon = Icons.Rounded.Star,
                    badgeColor = Color(0xFF5856D6),
                    apple = apple,
                    title = if (de) "Erweiterte Einstellungen" else "Advanced settings",
                    summary = if (de) "KI-Schalter, Expressive-UI, Clipboard-Sync & Datenschutz" else "AI toggles, Expressive UI, clipboard & privacy"
                ) {
                    onNavigate(SettingsRoute.ADVANCED)
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun DictionaryLearningScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    dictionaryStore: AdaptiveDictionaryStore,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
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
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { text ->
                val result = dictionaryStore.importText(text)
                revision++
                statusMessage = if (de) {
                    "Importiert: ${result.addedWords} neue Wörter, ${result.addedShortcuts} Kürzel."
                } else {
                    "Imported ${result.addedWords} words and ${result.addedShortcuts} shortcuts."
                }
            }.onFailure { err ->
                statusMessage = err.localizedMessage ?: "Import error"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(dictionaryStore.exportJson())
                }
            }.onSuccess {
                statusMessage = if (de) "Wörterbuch exportiert." else "Dictionary exported."
            }.onFailure { err ->
                statusMessage = err.localizedMessage ?: "Export error"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (apple) AppleSectionHeader(if (de) "Lernverhalten" else "Adaptive Learning")
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Adaptives Lernen" else "Adaptive learning",
                    summary = if (de) "Lernt deine Wörter, Wortpaare & merkt sich rückgängig gemachte Korrekturen"
                    else "Learns your vocabulary, next-word pairs & respects undone corrections",
                    checked = prefs.learningEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(learningEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatBadge(if (de) "Eigene Wörter" else "Custom", stats.customWordCount.toString())
                    StatBadge(if (de) "Kürzel" else "Shortcuts", stats.shortcutCount.toString())
                    StatBadge(if (de) "Gelernt" else "Learned", stats.learnedWordCount.toString())
                    StatBadge(if (de) "Paare" else "Bigrams", stats.learnedBigramCount.toString())
                }
            }
        }

        item {
            if (apple) AppleSectionHeader(if (de) "Import & Export" else "Import & Export")
            GroupedCard(apple) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (de) {
                            "Unterstützt .txt (Wortliste), .csv / .tsv (inkl. Gboard-Export), „kürzel => Text“ sowie .json. Gilt sofort für Vorschläge, Korrektur, Schreibhilfe und Diktat."
                        } else {
                            "Supports .txt, .csv/.tsv (including Gboard personal dictionary export), 'key => phrase', and .json. Applies across suggestions, autocorrect, writing tools, and voice."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (de) "Importieren" else "Import", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { exportLauncher.launch("tapwing-dictionary.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (de) "Exportieren" else "Export", fontSize = 12.sp)
                        }
                    }
                    if (statusMessage != null) {
                        Text(statusMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            if (apple) AppleSectionHeader(if (de) "Eigenes Wort hinzufügen" else "Add custom word")
            GroupedCard(apple) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newWordInput,
                            onValueChange = { newWordInput = it },
                            label = { Text(if (de) "Neues Wort / Fachbegriff" else "New word / term") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                dictionaryStore.addCustomWord(newWordInput)
                                newWordInput = ""
                                revision++
                            },
                            enabled = newWordInput.trim().length >= 2
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                    }
                    customWords.forEach { (word, weight) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$word ($weight)", modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(
                                onClick = {
                                    dictionaryStore.removeCustomWord(word)
                                    revision++
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            if (apple) AppleSectionHeader(if (de) "Textbausteine / Kürzel" else "Text shortcuts")
            GroupedCard(apple) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (de) "Dynamische Kürzel eingebaut: /datum und /zeit" else "Built-in dynamic triggers: /date and /time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newShortcutTrigger,
                            onValueChange = { newShortcutTrigger = it },
                            label = { Text(if (de) "Kürzel" else "Trigger") },
                            singleLine = true,
                            modifier = Modifier.weight(0.38f)
                        )
                        OutlinedTextField(
                            value = newShortcutExpansion,
                            onValueChange = { newShortcutExpansion = it },
                            label = { Text(if (de) "Text" else "Phrase") },
                            singleLine = true,
                            modifier = Modifier.weight(0.62f)
                        )
                    }
                    Button(
                        onClick = {
                            dictionaryStore.addShortcut(newShortcutTrigger, newShortcutExpansion)
                            newShortcutTrigger = ""
                            newShortcutExpansion = ""
                            revision++
                        },
                        enabled = newShortcutTrigger.isNotBlank() && newShortcutExpansion.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (de) "Kürzel speichern" else "Save shortcut")
                    }
                    shortcuts.forEach { (trigger, expansion) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$trigger → $expansion", modifier = Modifier.weight(1f), fontSize = 13.sp)
                            IconButton(
                                onClick = {
                                    dictionaryStore.removeShortcut(trigger)
                                    revision++
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            GroupedCard(apple) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            dictionaryStore.clearLearnedData()
                            revision++
                            statusMessage = if (de) "Gelernte Daten gelöscht." else "Learned data cleared."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (de) "Lerndaten leeren" else "Reset learned", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            dictionaryStore.clearCustomWords()
                            revision++
                            statusMessage = if (de) "Importierte Wörter gelöscht." else "Custom words cleared."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (de) "Wörterbuch leeren" else "Clear custom", fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LanguagesScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard(apple) {
                RadioRow(
                    title = "Deutsch (QWERTZ)",
                    selected = prefs.activeLocale == "de"
                ) { onUpdatePrefs { it.copy(activeLocale = "de") } }
                HorizontalDivider(thickness = 0.5.dp)
                RadioRow(
                    title = "English (QWERTY)",
                    selected = prefs.activeLocale == "en"
                ) { onUpdatePrefs { it.copy(activeLocale = "en") } }
            }
        }
        item {
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Zahlenreihe dauerhaft anzeigen" else "Show number row",
                    summary = if (de) "Eigene 1–0 Ziffernreihe wie bei PixelBoard" else "Dedicated 1–0 row above letters",
                    checked = prefs.showNumberRow,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showNumberRow = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Direkte Umlaut-Tasten (Ä, Ö, Ü)" else "Direct German umlauts (Ä, Ö, Ü)",
                    summary = if (de) "Aus = 1:1 PixelBoard 10er-Reihe (Umlaute per Gedrückthalten)"
                    else "Off = 1:1 PixelBoard 10-key row (umlauts via long-press)",
                    checked = prefs.showGermanUmlautKeys,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showGermanUmlautKeys = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Sprache auf Leertaste anzeigen" else "Show language label on spacebar",
                    summary = if (de) "Aus = cleane Leertaste wie bei PixelBoard" else "Off = clean unmarked spacebar",
                    checked = prefs.showSpacebarLanguageLabel,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showSpacebarLanguageLabel = v) } }
            }
        }
    }
}

@Composable
private fun AppearanceScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(if (de) "Apple Modus" else "Apple Mode", apple)
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Apple Modus (Liquid Glass)" else "Apple Mode (Liquid Glass)",
                    summary = if (de) "Dezent durchscheinende Glastasten, Feder-Animationen & iOS-UI"
                    else "Subtle Liquid Glass keys, spring physics & iOS UI",
                    checked = prefs.appleModeEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(appleModeEnabled = v) } }
            }
        }
        item {
            SectionLabel(if (de) "Dynamische Tastaturgröße" else "Dynamic keyboard size", apple)
            GroupedCard(apple) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (de) "Höhe: ${prefs.keyboardScalePercent}%" else "Height: ${prefs.keyboardScalePercent}%",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Reset",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                onUpdatePrefs { it.copy(keyboardScalePercent = 100, keyboardBottomLiftDp = 0) }
                            }
                        )
                    }
                    Slider(
                        value = prefs.keyboardScalePercent.toFloat(),
                        onValueChange = { v -> onUpdatePrefs { it.copy(keyboardScalePercent = v.roundToInt()) } },
                        valueRange = 75f..135f
                    )
                    Text(
                        text = if (de) "Abstand unten: ${prefs.keyboardBottomLiftDp} dp"
                        else "Bottom offset: ${prefs.keyboardBottomLiftDp} dp",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = prefs.keyboardBottomLiftDp.toFloat(),
                        onValueChange = { v -> onUpdatePrefs { it.copy(keyboardBottomLiftDp = v.roundToInt()) } },
                        valueRange = 0f..48f
                    )
                    Text(
                        text = if (de) "Gedrückthalten-Verzögerung: ${prefs.longPressDelayMs} ms"
                        else "Long-press delay: ${prefs.longPressDelayMs} ms",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = prefs.longPressDelayMs.toFloat(),
                        onValueChange = { v -> onUpdatePrefs { it.copy(longPressDelayMs = v.roundToInt()) } },
                        valueRange = 150f..500f
                    )
                }
            }
        }
        item {
            SectionLabel(if (de) "Farbschema" else "Theme palette", apple)
            GroupedCard(apple) {
                listOf(
                    ThemeMode.PIXEL_SLATE to (if (de) "Pixel Slate (1:1 PixelBoard)" else "Pixel Slate (1:1 PixelBoard)"),
                    ThemeMode.EXPRESSIVE_LAVENDER to (if (de) "Expressive Lavendel" else "Expressive Lavender"),
                    ThemeMode.DYNAMIC to (if (de) "Material You (Systemfarben)" else "Dynamic System Colors"),
                    ThemeMode.PIXEL_BLUE to "Pixel Blue",
                    ThemeMode.AMOLED_DARK to "AMOLED Pure Dark"
                ).forEachIndexed { idx, (mode, label) ->
                    RadioRow(title = label, selected = prefs.themeMode == mode) {
                        onUpdatePrefs { it.copy(themeMode = mode) }
                    }
                    if (idx < 4) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        item {
            SectionLabel(if (de) "Dunkelmodus" else "Dark mode", apple)
            GroupedCard(apple) {
                listOf(
                    DarkModePreference.SYSTEM to (if (de) "Systemstandard" else "Follow system"),
                    DarkModePreference.LIGHT to (if (de) "Hell" else "Light"),
                    DarkModePreference.DARK to (if (de) "Dunkel" else "Dark")
                ).forEachIndexed { idx, (mode, label) ->
                    RadioRow(title = label, selected = prefs.darkMode == mode) {
                        onUpdatePrefs { it.copy(darkMode = mode) }
                    }
                    if (idx < 2) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        item {
            SectionLabel(if (de) "Tasten & Einhandmodus" else "Keycaps & one-handed", apple)
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Abgerundete Pill-Tasten" else "Pill-shaped Expressive keys",
                    summary = if (de) "Volle abgerundete Kapsel-Tasten wie bei PixelBoard" else "Full capsule keycaps",
                    checked = prefs.roundedExpressiveKeys,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(roundedExpressiveKeys = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Tasten-Umrandung" else "Key borders",
                    summary = if (de) "Feine Linien um Tastenflächen" else "Subtle keycap borders",
                    checked = prefs.showKeyBorders,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showKeyBorders = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Haptisches Feedback" else "Haptic feedback",
                    summary = if (de) "Dezente Vibration beim Tippen" else "Subtle vibration per tap",
                    checked = prefs.hapticFeedback,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(hapticFeedback = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                OneHandedMode.entries.forEachIndexed { idx, mode ->
                    val label = when (mode) {
                        OneHandedMode.OFF -> if (de) "Einhandmodus: Aus" else "One-handed: Off"
                        OneHandedMode.RIGHT -> if (de) "Einhandmodus: Rechts" else "One-handed: Right"
                        OneHandedMode.LEFT -> if (de) "Einhandmodus: Links" else "One-handed: Left"
                    }
                    RadioRow(title = label, selected = prefs.oneHandedMode == mode) {
                        onUpdatePrefs { it.copy(oneHandedMode = mode) }
                    }
                    if (idx < OneHandedMode.entries.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CorrectionsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Autokorrektur" else "Auto-correction",
                    summary = if (de) "Respektiert eigene Wörter & rückgängig gemachte Wörter" else "Respects custom & rejected words",
                    checked = prefs.autoCorrect,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(autoCorrect = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Adaptives Lernen" else "Adaptive learning",
                    summary = if (de) "Wird mit deiner Tippgewohnheit präziser" else "Learns your vocabulary & bigrams over time",
                    checked = prefs.learningEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(learningEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Inline-Taschenrechner" else "Inline math strip",
                    summary = if (de) "Berechnet z. B. 24*7= oder 19,5+4,5= direkt in der Leiste" else "Evaluates 24*7= right in the strip",
                    checked = prefs.inlineMathEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(inlineMathEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Wischen auf ⌫ löscht Wort" else "Swipe backspace deletes word",
                    summary = if (de) "Schnelles Wortlöschen mit Proactive Undo" else "Fast word delete with Proactive Undo",
                    checked = prefs.swipeBackspaceDeleteWord,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(swipeBackspaceDeleteWord = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Rechtschreib- & Grammatik-Chips" else "Spell & grammar chips",
                    summary = if (de) "Klickbare Korrekturvorschläge in der Leiste anzeigen" else "Show interactive correction & grammar pills",
                    checked = prefs.spellGrammarChips,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(spellGrammarChips = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = "Proactive Undo",
                    summary = if (de) "1-Tap Rückgängig nach Autokorrektur, Diktat oder KI-Edit" else "1-tap Undo chip after edits",
                    checked = prefs.proactiveUndo,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(proactiveUndo = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Inkognito-Modus" else "Incognito mode",
                    summary = if (de) "Pausiert Lernen & Zwischenablage-Verlauf vorübergehend" else "Temporarily pauses learning & clipboard history",
                    checked = prefs.incognitoMode,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(incognitoMode = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Doppeltipp-Leertaste für Punkt" else "Double-space period",
                    summary = if (de) "Zweimal Leertaste fügt '. ' ein" else "Double-tap space inserts '. '",
                    checked = prefs.doubleSpacePeriod,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(doubleSpacePeriod = v) } }
            }
        }
        item {
            SectionLabel(if (de) "Vorschlagsleiste" else "Toolbar icons", apple)
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Schreibhilfe-Symbol (✨)" else "Writing tools icon (✨)",
                    summary = if (de) "Schnellzugriff in der Leiste anzeigen" else "Show Writing Tools shortcut",
                    checked = prefs.showWritingToolsIcon,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showWritingToolsIcon = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Zwischenablage-Symbol" else "Clipboard icon",
                    summary = if (de) "Direktzugriff auf kopierte Texte" else "Quick shortcut to clipboard",
                    checked = prefs.showClipboardIcon,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showClipboardIcon = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Scan-Text-OCR-Symbol" else "Scan Text OCR icon",
                    summary = if (de) "Kamera-Texterkennung in Leiste anzeigen" else "Show camera OCR button",
                    checked = prefs.showScanTextIcon,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showScanTextIcon = v) } }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VoiceSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(if (de) "Diktat-Typ" else "Dictation type", apple)
            GroupedCard(apple) {
                RadioRow(
                    title = if (de) "Natürliche Bereinigung (Rambler-Stil)" else "Natural Cleanup (Rambler style)",
                    selected = prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
                ) { onUpdatePrefs { it.copy(voiceMode = VoiceDictationMode.NATURAL_CLEANUP) } }
                HorizontalDivider(thickness = 0.5.dp)
                RadioRow(
                    title = "Standard",
                    selected = prefs.voiceMode == VoiceDictationMode.STANDARD
                ) { onUpdatePrefs { it.copy(voiceMode = VoiceDictationMode.STANDARD) } }
            }
        }
        item {
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Füllwörter & Pausen entfernen" else "Remove filler words",
                    summary = if (de) "Bereinigt 'ähm', 'äh', 'um', 'uh' & Selbstkorrekturen" else "Strips 'um', 'uh', and self-corrections",
                    checked = prefs.voiceRemoveFillers,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(voiceRemoveFillers = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Automatische Zeichensetzung" else "Auto-punctuation",
                    summary = if (de) "Erkennt gesprochene Satzzeichen & Satzenden" else "Applies spoken punctuation and caps",
                    checked = prefs.voiceAutoPunctuate,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(voiceAutoPunctuate = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Strikte Sprachsperre" else "Strict language lock",
                    summary = if (de) "Verhindert ungewollten Sprachwechsel während des Diktats" else "Prevents automatic language drift",
                    checked = prefs.voiceStrictLanguageLock,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(voiceStrictLanguageLock = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Minimieren-Taste in Sprachleiste" else "Voice toolbar minimize button",
                    summary = if (de) "Tastenfeld während des Diktats einklappen" else "Collapse keys while dictating",
                    checked = prefs.voiceMinimizeButton,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(voiceMinimizeButton = v) } }
            }
        }
        item {
            GroupedCard(apple) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (de) "Drehungs-Schonfrist: ${prefs.voiceRotationGraceMs} ms"
                        else "Rotation grace period: ${prefs.voiceRotationGraceMs} ms",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = prefs.voiceRotationGraceMs.toFloat(),
                        onValueChange = { value ->
                            onUpdatePrefs { it.copy(voiceRotationGraceMs = value.roundToInt().toLong()) }
                        },
                        valueRange = 500f..3500f
                    )
                    OutlinedButton(
                        onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (micGranted) {
                                if (de) "Mikrofon-Berechtigung erteilt ✓" else "Microphone permission granted ✓"
                            } else {
                                if (de) "Mikrofon-Berechtigung anfordern" else "Grant microphone permission"
                            }
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
    apple: Boolean,
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "KI-Schreibhilfe aktivieren" else "Enable AI Writing Tools",
                    summary = if (de) "Korrigieren, Tonfall anpassen & Umformulieren" else "Proofread, tone rewrite & rephrase",
                    checked = prefs.aiWritingToolsEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(aiWritingToolsEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Alle Tastaturen unterstützen" else "Support all keyboards",
                    summary = if (de) "Auch außerhalb von Englisch in DE/allen Layouts nutzen" else "Enable across all keyboard locales",
                    checked = prefs.aiSupportAllKeyboards,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(aiSupportAllKeyboards = v) } }
            }
        }
        item {
            SectionLabel(if (de) "KI-Backend wählen" else "Inference Backend", apple)
            GroupedCard(apple) {
                listOf(
                    AiProviderType.LOCAL_RULES to (if (de) "On-Device Regeln (Offline, ohne Key)" else "On-Device Rules (Offline, no key)"),
                    AiProviderType.GEMINI_API to "Google Gemini API (Eigener API-Key)",
                    AiProviderType.OPENAI_COMPATIBLE to "OpenAI-kompatibler Endpunkt (Eigener Key)"
                ).forEachIndexed { idx, (type, label) ->
                    RadioRow(title = label, selected = prefs.aiProvider == type) {
                        onUpdatePrefs { it.copy(aiProvider = type) }
                    }
                    if (idx < 2) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        item {
            GroupedCard(apple) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = prefs.aiModel,
                        onValueChange = { m -> onUpdatePrefs { it.copy(aiModel = m) } },
                        label = { Text(if (de) "Modellname" else "Model name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (prefs.aiProvider == AiProviderType.OPENAI_COMPATIBLE) {
                        OutlinedTextField(
                            value = prefs.aiCustomEndpoint,
                            onValueChange = { ep -> onUpdatePrefs { it.copy(aiCustomEndpoint = ep) } },
                            label = { Text("Endpoint URL") },
                            placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (maskedKey.isNotEmpty()) {
                                if (de) "Gespeicherter Key (Android Keystore): $maskedKey" else "Saved key (Keystore): $maskedKey"
                            } else {
                                if (de) "Kein API-Key gespeichert (lokaler Modus aktiv)" else "No API key stored (local rules active)"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text(if (de) "Neuen API-Schlüssel eingeben" else "Enter new API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                secureKeyStorage.saveApiKey(apiKeyInput)
                                maskedKey = secureKeyStorage.maskedPreview()
                                apiKeyInput = ""
                                testStatus = if (de) "Schlüssel sicher im Android Keystore gespeichert." else "Key saved in Android Keystore."
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "Key speichern" else "Save key")
                        }
                        OutlinedButton(
                            onClick = {
                                secureKeyStorage.clearApiKey()
                                maskedKey = ""
                                testStatus = if (de) "API-Schlüssel gelöscht." else "API key cleared."
                            },
                            enabled = maskedKey.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (de) "Entfernen" else "Clear")
                        }
                    }
                    FilledTonalButton(
                        onClick = {
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
                        },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (de) "Backend testen" else "Test backend")
                    }
                    if (testStatus != null) {
                        Text(
                            text = testStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ClipboardSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    val context = LocalContext.current
    val mediaPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasMediaPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, mediaPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMediaPerm = it
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Erweiterte Zwischenablage" else "Extended clipboard history",
                    summary = if (de) "Speichert kopierte Texte bis zum eingestellten Limit" else "Keeps copied clips up to limit",
                    checked = prefs.clipboardEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(clipboardEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Screenshot-OCR-Vorschlag" else "Screenshot OCR chip",
                    summary = if (de) "Bietet kürzlich aufgenommene Screenshots zum Texterkennen an" else "Shows chip for recent screenshots",
                    checked = prefs.screenshotSuggestionChip,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(screenshotSuggestionChip = v) } }
            }
        }
        item {
            SectionLabel(if (de) "Aufbewahrungsdauer" else "Retention duration", apple)
            GroupedCard(apple) {
                val options = listOf(
                    ClipboardRetention.HOUR_1 to (if (de) "1 Stunde" else "1 hour"),
                    ClipboardRetention.HOURS_24 to (if (de) "24 Stunden" else "24 hours"),
                    ClipboardRetention.DAYS_7 to (if (de) "7 Tage" else "7 days"),
                    ClipboardRetention.DAYS_30 to (if (de) "30 Tage" else "30 days"),
                    ClipboardRetention.NEVER to (if (de) "Nie löschen" else "Never delete")
                )
                options.forEachIndexed { idx, (ret, label) ->
                    RadioRow(title = label, selected = prefs.clipboardRetention == ret) {
                        onUpdatePrefs { it.copy(clipboardRetention = ret) }
                    }
                    if (idx < options.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        item {
            GroupedCard(apple) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (de) "Max. unangeheftete Einträge: ${prefs.clipboardMaxItems}"
                        else "Max unpinned clips: ${prefs.clipboardMaxItems}",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = prefs.clipboardMaxItems.toFloat(),
                        onValueChange = { v -> onUpdatePrefs { it.copy(clipboardMaxItems = v.roundToInt()) } },
                        valueRange = 20f..200f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { mediaLauncher.launch(mediaPerm) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (hasMediaPerm) (if (de) "Medienzugriff ✓" else "Media access ✓")
                                else (if (de) "Screenshot-Zugriff" else "Media permission"),
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { context.startActivity(Intent(context, ScanTextActivity::class.java)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (de) "OCR testen" else "Test OCR", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun EmojiSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard(apple) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (de) "Emoji-Größe: ${prefs.emojiScale} sp" else "Emoji size: ${prefs.emojiScale} sp",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "😀  🔥  ✨  🙏  🚀",
                        fontSize = prefs.emojiScale.sp
                    )
                    Slider(
                        value = prefs.emojiScale.toFloat(),
                        onValueChange = { v -> onUpdatePrefs { it.copy(emojiScale = v.roundToInt()) } },
                        valueRange = 20f..34f
                    )
                }
            }
        }
        item {
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "Emoji-Vorschläge in der Leiste" else "Candidate emoji suggestions",
                    summary = if (de) "Schlägt passende Emojis zu getippten Wörtern vor" else "Suggest emojis matching typed words",
                    checked = prefs.candidateEmojiSuggestions,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(candidateEmojiSuggestions = v) } }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    apple: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(if (de) "Funktionen" else "Features", apple)
            GroupedCard(apple) {
                ToggleRow(
                    title = if (de) "KI-Schreibhilfe aktivieren" else "Enable AI Writing Tools",
                    summary = if (de) "Zeigt das Schreibhilfe-Symbol (✨) in der Vorschlagsleiste" else "Show Writing tools icon",
                    checked = prefs.aiWritingToolsEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(aiWritingToolsEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Alle Tastaturen unterstützen" else "Support All Keyboards",
                    summary = if (de) "Schreibhilfe auf allen Sprachen und Layouts nutzen" else "Enable across all layouts",
                    checked = prefs.aiSupportAllKeyboards,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(aiSupportAllKeyboards = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Natürliche Spracheingabe aktivieren" else "Enable Advanced Voice Typing",
                    summary = if (de) "Füllwort-Bereinigung, Satzzeichen & Minimieren-Taste" else "Filler cleanup & punctuation",
                    checked = prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP,
                    apple = apple
                ) { v ->
                    onUpdatePrefs {
                        it.copy(voiceMode = if (v) VoiceDictationMode.NATURAL_CLEANUP else VoiceDictationMode.STANDARD)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Apple Modus (Liquid Glass)" else "Apple Mode (Liquid Glass)",
                    summary = if (de) "Flüssiges Glas, Feder-Animationen & iOS-Einstellungen" else "Liquid Glass keys & iOS settings",
                    checked = prefs.appleModeEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(appleModeEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Proactive Undo in Vorschlagsleiste" else "Proactive strip Undo",
                    summary = if (de) "Sofortiges Rückgängigmachen nach Autokorrektur/KI" else "Instant Undo pill",
                    checked = prefs.proactiveUndo,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(proactiveUndo = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Erweiterte Zwischenablage (bis 100)" else "Extended Clipboard (up to 100)",
                    summary = if (de) "Speichert bis zu 100 unangeheftete Einträge lokal" else "Stores up to 100 clips locally",
                    checked = prefs.clipboardEnabled,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(clipboardEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Scan-Text-OCR-Schnellzugriff" else "Scan Text OCR shortcut",
                    summary = if (de) "Kamera-Texterkennung über ML Kit in der Leiste" else "On-device ML Kit OCR shortcut",
                    checked = prefs.showScanTextIcon,
                    apple = apple
                ) { v -> onUpdatePrefs { it.copy(showScanTextIcon = v) } }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tapwing Keyboard • v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutSettingsScreen(de: Boolean, apple: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard(apple) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text("Tapwing", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("v${BuildConfig.VERSION_NAME} • com.tapwing.keyboard", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        text = if (de) {
                            "Eigenständige, sauber lizenzierte (Apache-2.0) Android-Tastatur auf Basis offizieller Android-APIs (InputMethodService, SpeechRecognizer, CameraX + ML Kit, Android Keystore)."
                        } else {
                            "Standalone, clean-room Apache-2.0 Android keyboard built on official Android APIs."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (de) {
                            "Datenschutz: Tippen, eigenes Wörterbuch, adaptives Lernen, Clipboard und Kamera-OCR bleiben lokal auf deinem Gerät."
                        } else {
                            "Privacy: Typing, custom dictionary, adaptive learning, clipboard and OCR run locally on your device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupedCard(
    apple: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    AppleGroupedSurface(appleMode = apple, content = content)
}

@Composable
private fun SectionLabel(text: String, apple: Boolean = false) {
    if (apple) {
        AppleSectionHeader(text)
    } else {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    badgeColor: Color,
    apple: Boolean,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (apple) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (apple) {
            AppleIconBadge(icon = icon, tintColor = badgeColor)
        } else {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    apple: Boolean = false,
    icon: ImageVector? = null,
    badgeColor: Color = Color(0xFF007AFF),
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            if (apple) {
                AppleIconBadge(icon = icon, tintColor = badgeColor)
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (apple) {
            AppleSwitch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

package com.tapwing.keyboard.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tapwing.keyboard.BuildConfig
import com.tapwing.keyboard.ai.CloudAiClient
import com.tapwing.keyboard.ai.CloudAiResult
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.ClipboardRetention
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.KeyboardHeightMode
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.data.VoiceDictationMode
import com.tapwing.keyboard.ocr.ScanTextActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SettingsRoute {
    HOME,
    LANGUAGES,
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = when (route) {
                            SettingsRoute.HOME -> "Tapwing"
                            SettingsRoute.LANGUAGES -> if (de) "Sprachen & Layouts" else "Languages & Layouts"
                            SettingsRoute.APPEARANCE -> if (de) "Design & Tasten" else "Theme & Keys"
                            SettingsRoute.CORRECTIONS -> if (de) "Korrekturen & Leiste" else "Corrections & Strip"
                            SettingsRoute.VOICE -> if (de) "Spracheingabe" else "Voice typing"
                            SettingsRoute.WRITING_TOOLS -> if (de) "KI-Schreibhilfe" else "AI Writing Tools"
                            SettingsRoute.CLIPBOARD -> if (de) "Zwischenablage & OCR" else "Clipboard & OCR"
                            SettingsRoute.EMOJI -> "Emoji"
                            SettingsRoute.ADVANCED -> if (de) "Erweiterte Einstellungen" else "Advanced settings"
                            SettingsRoute.ABOUT -> if (de) "Über Tapwing" else "About Tapwing"
                        },
                        fontWeight = FontWeight.SemiBold
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
            when (route) {
                SettingsRoute.HOME -> HomeSettingsScreen(
                    prefs = prefs,
                    de = de,
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
                    onNavigate = { route = it }
                )
                SettingsRoute.LANGUAGES -> LanguagesScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.APPEARANCE -> AppearanceScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.CORRECTIONS -> CorrectionsScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.VOICE -> VoiceSettingsScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.WRITING_TOOLS -> WritingToolsSettingsScreen(prefs, de, secureKeyStorage, onUpdatePrefs)
                SettingsRoute.CLIPBOARD -> ClipboardSettingsScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.EMOJI -> EmojiSettingsScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.ADVANCED -> AdvancedSettingsScreen(prefs, de, onUpdatePrefs)
                SettingsRoute.ABOUT -> AboutSettingsScreen(de)
            }
        }
    }
}

@Composable
private fun HomeSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    imeEnabled: Boolean,
    imeSelected: Boolean,
    searchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    testInputText: String,
    onTestInputChange: (String) -> Unit,
    onOpenImeSystemSettings: () -> Unit,
    onShowImePicker: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit
) {
    val entries = listOf(
        Triple(SettingsRoute.LANGUAGES, if (de) "Sprachen & Layouts" else "Languages & Layouts",
            if (de) "Aktuell: ${if (prefs.activeLocale == "de") "Deutsch (QWERTZ)" else "English (QWERTY)"}" else "Active: ${prefs.activeLocale.uppercase()}"),
        Triple(SettingsRoute.APPEARANCE, if (de) "Design & Tasten" else "Theme & Appearance",
            if (de) "Material 3 Expressive, abgerundete Tasten" else "Material 3 Expressive, rounded keycaps"),
        Triple(SettingsRoute.CORRECTIONS, if (de) "Korrekturen & Vorschläge" else "Corrections & Suggestions",
            if (de) "Autokorrektur, Grammatik-Chips, Proactive Undo" else "Autocorrect, grammar chips, Proactive Undo"),
        Triple(SettingsRoute.VOICE, if (de) "Spracheingabe" else "Voice typing",
            if (de) "Natürliches Diktat (Rambler-Stil), Füllwörter, Sprachsperre" else "Natural dictation cleanup, fillers, language lock"),
        Triple(SettingsRoute.WRITING_TOOLS, if (de) "KI-Schreibhilfe" else "AI Writing Tools",
            if (de) "Korrektur, Tonfall-Rewrite & eigener API-Schlüssel" else "Proofread, tone rewrite & optional API key"),
        Triple(SettingsRoute.CLIPBOARD, if (de) "Zwischenablage & Scan Text" else "Clipboard & Scan Text",
            if (de) "${prefs.clipboardMaxItems} Einträge, Aufbewahrung & Kamera-OCR" else "${prefs.clipboardMaxItems} clips, retention & camera OCR"),
        Triple(SettingsRoute.EMOJI, "Emoji",
            if (de) "Größe (${prefs.emojiScale} sp) & Vorschläge" else "Scale (${prefs.emojiScale} sp) & suggestions")
    )

    val q = searchQuery.trim().lowercase()
    val filteredMain = entries.filter { (_, title, summary) ->
        q.isEmpty() || title.lowercase().contains(q) || summary.lowercase().contains(q)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (searchVisible) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(if (de) "Einstellungen durchsuchen…" else "Search settings…") }
                )
            }
        }

        // IME Activation Card
        item {
            GroupedCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (imeSelected) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (imeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
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
            GroupedCard {
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
                                if (de) "Tippen, diktieren, ✨ Schreibhilfe oder OCR testen…"
                                else "Try typing, voice, ✨ writing tools or OCR…"
                            )
                        }
                    )
                }
            }
        }

        // Core Settings Group
        if (filteredMain.isNotEmpty()) {
            item {
                GroupedCard {
                    filteredMain.forEachIndexed { idx, (target, title, summary) ->
                        val icon = when (target) {
                            SettingsRoute.LANGUAGES -> Icons.Rounded.Language
                            SettingsRoute.APPEARANCE -> Icons.Rounded.Palette
                            SettingsRoute.CORRECTIONS -> Icons.Rounded.Spellcheck
                            SettingsRoute.VOICE -> Icons.Rounded.Mic
                            SettingsRoute.WRITING_TOOLS -> Icons.Rounded.AutoAwesome
                            SettingsRoute.CLIPBOARD -> Icons.Rounded.ContentPaste
                            else -> Icons.Rounded.EmojiEmotions
                        }
                        NavRow(icon = icon, title = title, summary = summary) {
                            onNavigate(target)
                        }
                        if (idx < filteredMain.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 54.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // About & Advanced Settings Card (matching showcase.png bottom card)
        item {
            GroupedCard {
                NavRow(
                    icon = Icons.Rounded.Info,
                    title = if (de) "Über Tapwing" else "About",
                    summary = "Version ${BuildConfig.VERSION_NAME} • Apache-2.0"
                ) {
                    onNavigate(SettingsRoute.ABOUT)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp), thickness = 0.5.dp)
                NavRow(
                    icon = Icons.Rounded.Star,
                    title = if (de) "Erweiterte Einstellungen" else "Advanced settings",
                    summary = if (de) "KI-Schalter, Expressive-UI, Clipboard-Sync & Datenschutz" else "AI toggles, Expressive UI, clipboard & privacy"
                ) {
                    onNavigate(SettingsRoute.ADVANCED)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LanguagesScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard {
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
            GroupedCard {
                ToggleRow(
                    title = if (de) "Zahlenreihe dauerhaft anzeigen" else "Show number row",
                    summary = if (de) "Zusätzliche Ziffernreihe über den Buchstaben" else "Dedicated number row above letters",
                    checked = prefs.showNumberRow
                ) { v -> onUpdatePrefs { it.copy(showNumberRow = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Deutsche Umlaute (Ä, Ö, Ü) direkt" else "Direct German umlauts (Ä, Ö, Ü)",
                    summary = if (de) "Direkte Tasten im deutschen QWERTZ-Layout" else "Dedicated umlaut keys in German layout",
                    checked = prefs.showGermanUmlautKeys
                ) { v -> onUpdatePrefs { it.copy(showGermanUmlautKeys = v) } }
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(if (de) "Farbschema" else "Theme palette")
            GroupedCard {
                listOf(
                    ThemeMode.EXPRESSIVE_LAVENDER to (if (de) "Expressive Lavendel (Pixel-Stil)" else "Expressive Lavender"),
                    ThemeMode.DYNAMIC to (if (de) "Material You (Systemfarben)" else "Dynamic System Colors"),
                    ThemeMode.PIXEL_BLUE to "Pixel Blue",
                    ThemeMode.AMOLED_DARK to "AMOLED Pure Dark"
                ).forEachIndexed { idx, (mode, label) ->
                    RadioRow(title = label, selected = prefs.themeMode == mode) {
                        onUpdatePrefs { it.copy(themeMode = mode) }
                    }
                    if (idx < 3) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
        item {
            SectionLabel(if (de) "Dunkelmodus" else "Dark mode")
            GroupedCard {
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
            SectionLabel(if (de) "Tasten & Feedback" else "Keycaps & feedback")
            GroupedCard {
                ToggleRow(
                    title = if (de) "Abgerundete Expressive-Tasten" else "Rounded Expressive keys",
                    summary = if (de) "Material 3 Pill-Design für Tasten und Modifier" else "Material 3 pill keycaps & circular modifiers",
                    checked = prefs.roundedExpressiveKeys
                ) { v -> onUpdatePrefs { it.copy(roundedExpressiveKeys = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Tasten-Umrandung" else "Key borders",
                    summary = if (de) "Feine Linien um Tastenflächen" else "Subtle keycap borders",
                    checked = prefs.showKeyBorders
                ) { v -> onUpdatePrefs { it.copy(showKeyBorders = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Haptisches Feedback" else "Haptic feedback on keypress",
                    summary = if (de) "Dezente Vibration beim Tippen" else "Subtle vibration per key tap",
                    checked = prefs.hapticFeedback
                ) { v -> onUpdatePrefs { it.copy(hapticFeedback = v) } }
            }
        }
        item {
            SectionLabel(if (de) "Tastaturhöhe" else "Keyboard height")
            GroupedCard {
                KeyboardHeightMode.entries.forEachIndexed { idx, mode ->
                    val label = when (mode) {
                        KeyboardHeightMode.COMPACT -> if (de) "Kompakt" else "Compact"
                        KeyboardHeightMode.NORMAL -> "Normal"
                        KeyboardHeightMode.TALL -> if (de) "Groß" else "Tall"
                    }
                    RadioRow(title = label, selected = prefs.keyboardHeight == mode) {
                        onUpdatePrefs { it.copy(keyboardHeight = mode) }
                    }
                    if (idx < KeyboardHeightMode.entries.lastIndex) HorizontalDivider(thickness = 0.5.dp)
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
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard {
                ToggleRow(
                    title = if (de) "Autokorrektur" else "Auto-correction",
                    summary = if (de) "Tippfehler bei Leertaste/Satzzeichen automatisch korrigieren" else "Correct typos on space or punctuation",
                    checked = prefs.autoCorrect
                ) { v -> onUpdatePrefs { it.copy(autoCorrect = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Rechtschreib- & Grammatik-Chips" else "Spell & grammar chips",
                    summary = if (de) "Klickbare Korrekturvorschläge in der Leiste anzeigen" else "Show interactive correction & grammar pills",
                    checked = prefs.spellGrammarChips
                ) { v -> onUpdatePrefs { it.copy(spellGrammarChips = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = "Proactive Undo",
                    summary = if (de) "1-Tap Rückgängig nach Autokorrektur, Diktat oder KI-Edit" else "1-tap Undo chip after edits or dictation",
                    checked = prefs.proactiveUndo
                ) { v -> onUpdatePrefs { it.copy(proactiveUndo = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Doppeltipp-Leertaste für Punkt" else "Double-space period",
                    summary = if (de) "Zweimal Leertaste fügt '. ' ein" else "Double-tap space inserts '. '",
                    checked = prefs.doubleSpacePeriod
                ) { v -> onUpdatePrefs { it.copy(doubleSpacePeriod = v) } }
            }
        }
        item {
            SectionLabel(if (de) "Vorschlagsleiste" else "Toolbar icons")
            GroupedCard {
                ToggleRow(
                    title = if (de) "Schreibhilfe-Symbol (✨)" else "Writing tools icon (✨)",
                    summary = if (de) "Schnellzugriff in der Leiste anzeigen" else "Show Writing Tools shortcut while typing",
                    checked = prefs.showWritingToolsIcon
                ) { v -> onUpdatePrefs { it.copy(showWritingToolsIcon = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Zwischenablage-Symbol" else "Clipboard icon",
                    summary = if (de) "Direktzugriff auf kopierte Texte" else "Quick shortcut to clipboard panel",
                    checked = prefs.showClipboardIcon
                ) { v -> onUpdatePrefs { it.copy(showClipboardIcon = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Scan-Text-OCR-Symbol" else "Scan Text OCR icon",
                    summary = if (de) "Kamera-Texterkennung in Leiste anzeigen" else "Show camera OCR button in strip",
                    checked = prefs.showScanTextIcon
                ) { v -> onUpdatePrefs { it.copy(showScanTextIcon = v) } }
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(if (de) "Diktat-Typ" else "Dictation type")
            GroupedCard {
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
            GroupedCard {
                ToggleRow(
                    title = if (de) "Füllwörter & Pausen entfernen" else "Remove filler words",
                    summary = if (de) "Bereinigt 'ähm', 'äh', 'um', 'uh' & Selbstkorrekturen" else "Strips 'um', 'uh', and handles self-corrections",
                    checked = prefs.voiceRemoveFillers
                ) { v -> onUpdatePrefs { it.copy(voiceRemoveFillers = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Automatische Zeichensetzung" else "Auto-punctuation",
                    summary = if (de) "Erkennt gesprochene Satzzeichen & Satzenden" else "Applies spoken punctuation and sentence caps",
                    checked = prefs.voiceAutoPunctuate
                ) { v -> onUpdatePrefs { it.copy(voiceAutoPunctuate = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Strikte Sprachsperre" else "Strict language lock",
                    summary = if (de) "Verhindert ungewollten Sprachwechsel während des Diktats" else "Prevents automatic language drift while dictating",
                    checked = prefs.voiceStrictLanguageLock
                ) { v -> onUpdatePrefs { it.copy(voiceStrictLanguageLock = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Minimieren-Taste in Sprachleiste" else "Voice toolbar minimize button",
                    summary = if (de) "Tastenfeld während des Diktats einklappen" else "Collapse keys while voice bar stays visible",
                    checked = prefs.voiceMinimizeButton
                ) { v -> onUpdatePrefs { it.copy(voiceMinimizeButton = v) } }
            }
        }
        item {
            GroupedCard {
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
            GroupedCard {
                ToggleRow(
                    title = if (de) "KI-Schreibhilfe aktivieren" else "Enable AI Writing Tools",
                    summary = if (de) "Korrigieren, Tonfall anpassen & Umformulieren" else "Proofread, tone rewrite & rephrase",
                    checked = prefs.aiWritingToolsEnabled
                ) { v -> onUpdatePrefs { it.copy(aiWritingToolsEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Alle Tastaturen unterstützen" else "Support all keyboards",
                    summary = if (de) "Auch außerhalb von Englisch in DE/allen Layouts nutzen" else "Enable across all keyboard locales",
                    checked = prefs.aiSupportAllKeyboards
                ) { v -> onUpdatePrefs { it.copy(aiSupportAllKeyboards = v) } }
            }
        }
        item {
            SectionLabel(if (de) "KI-Backend wählen" else "Inference Backend")
            GroupedCard {
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
            GroupedCard {
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
            GroupedCard {
                ToggleRow(
                    title = if (de) "Erweiterte Zwischenablage" else "Extended clipboard history",
                    summary = if (de) "Speichert kopierte Texte bis zum eingestellten Limit" else "Keeps copied clips up to configured limit",
                    checked = prefs.clipboardEnabled
                ) { v -> onUpdatePrefs { it.copy(clipboardEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Screenshot-OCR-Vorschlag" else "Screenshot OCR chip",
                    summary = if (de) "Bietet kürzlich aufgenommene Screenshots zum Texterkennen an" else "Shows chip for recent screenshots",
                    checked = prefs.screenshotSuggestionChip
                ) { v -> onUpdatePrefs { it.copy(screenshotSuggestionChip = v) } }
            }
        }
        item {
            SectionLabel(if (de) "Aufbewahrungsdauer" else "Retention duration")
            GroupedCard {
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
            GroupedCard {
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
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard {
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
            GroupedCard {
                ToggleRow(
                    title = if (de) "Emoji-Vorschläge in der Leiste" else "Candidate emoji suggestions",
                    summary = if (de) "Schlägt passende Emojis zu getippten Wörtern vor" else "Suggest emojis matching typed words",
                    checked = prefs.candidateEmojiSuggestions
                ) { v -> onUpdatePrefs { it.copy(candidateEmojiSuggestions = v) } }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    onUpdatePrefs: ((TapwingPrefs) -> TapwingPrefs) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionLabel(if (de) "Funktionen" else "Features")
            GroupedCard {
                ToggleRow(
                    title = if (de) "KI-Schreibhilfe aktivieren" else "Enable AI Writing Tools",
                    summary = if (de) "Zeigt das Schreibhilfe-Symbol (✨) in der Vorschlagsleiste" else "Show Writing tools icon in suggestion strip",
                    checked = prefs.aiWritingToolsEnabled
                ) { v -> onUpdatePrefs { it.copy(aiWritingToolsEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Alle Tastaturen unterstützen" else "Support All Keyboards",
                    summary = if (de) "Schreibhilfe auf allen Sprachen und Layouts nutzen" else "Enable writing tools across all layouts",
                    checked = prefs.aiSupportAllKeyboards
                ) { v -> onUpdatePrefs { it.copy(aiSupportAllKeyboards = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Natürliche Spracheingabe aktivieren" else "Enable Advanced Voice Typing",
                    summary = if (de) "Füllwort-Bereinigung, Satzzeichen & Minimieren-Taste" else "Filler cleanup, punctuation & minimize toggle",
                    checked = prefs.voiceMode == VoiceDictationMode.NATURAL_CLEANUP
                ) { v ->
                    onUpdatePrefs {
                        it.copy(voiceMode = if (v) VoiceDictationMode.NATURAL_CLEANUP else VoiceDictationMode.STANDARD)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Abgerundete Expressive-Tasten" else "Rounded Expressive keys",
                    summary = if (de) "Material 3 Belka Pill-Tasten aktivieren" else "Material 3 Belka pill-shaped keycaps",
                    checked = prefs.roundedExpressiveKeys
                ) { v -> onUpdatePrefs { it.copy(roundedExpressiveKeys = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Proactive Undo in Vorschlagsleiste" else "Proactive strip Undo",
                    summary = if (de) "Sofortiges Rückgängigmachen nach Autokorrektur/KI" else "Instant Undo pill after corrections or rewrites",
                    checked = prefs.proactiveUndo
                ) { v -> onUpdatePrefs { it.copy(proactiveUndo = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Erweiterte Zwischenablage (bis 100)" else "Extended Clipboard (up to 100)",
                    summary = if (de) "Speichert bis zu 100 unangeheftete Einträge lokal" else "Stores up to 100 recent unpinned clips",
                    checked = prefs.clipboardEnabled
                ) { v -> onUpdatePrefs { it.copy(clipboardEnabled = v) } }
                HorizontalDivider(thickness = 0.5.dp)
                ToggleRow(
                    title = if (de) "Scan-Text-OCR-Schnellzugriff" else "Scan Text OCR shortcut",
                    summary = if (de) "Kamera-Texterkennung über ML Kit in der Leiste" else "On-device ML Kit OCR shortcut in toolbar",
                    checked = prefs.showScanTextIcon
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
private fun AboutSettingsScreen(de: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GroupedCard {
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
                            "Eigenständige, sauber lizenzierte (Apache-2.0) Material 3 Expressive Android-Tastatur auf Basis offizieller Android-APIs (InputMethodService, SpeechRecognizer, CameraX + ML Kit Text Recognition, Android Keystore)."
                        } else {
                            "Standalone, clean-room Apache-2.0 Material 3 Expressive Android keyboard built on official Android APIs."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (de) {
                            "Datenschutz: Tippen, Korrekturen, Clipboard-Historie und Kamera-OCR laufen vollständig lokal auf deinem Gerät. Optionale Cloud-KI wird nur genutzt, wenn du deinen eigenen API-Schlüssel hinterlegst."
                        } else {
                            "Privacy: Typing, corrections, clipboard history and camera OCR run locally on your device. Cloud AI runs only if you supply your own API key."
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
private fun GroupedCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )
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

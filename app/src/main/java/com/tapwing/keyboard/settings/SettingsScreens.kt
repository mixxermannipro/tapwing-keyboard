// SPDX-License-Identifier: LicenseRef-Tapwing-Source-Available

package com.tapwing.keyboard.settings

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tapwing.keyboard.BuildConfig
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.tapwing.keyboard.R
import com.tapwing.keyboard.ai.AiModelCatalog
import com.tapwing.keyboard.ai.LocalModelCatalog
import com.tapwing.keyboard.ai.LocalModelManager
import com.tapwing.keyboard.data.AiProviderType
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.QuickSearchEngine
import com.tapwing.keyboard.data.SearchApp
import com.tapwing.keyboard.data.SecureKeyStorage
import com.tapwing.keyboard.data.TapwingPrefs
import com.tapwing.keyboard.data.ThemeMode
import com.tapwing.keyboard.data.TypingStats
import com.tapwing.keyboard.data.TypingStatsStore
import com.tapwing.keyboard.data.VoiceEngineMode
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.OfflineDictionaryPackManager
import com.tapwing.keyboard.ui.keyboard.icon
import com.tapwing.keyboard.ui.keyboard.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsRoute {
    HOME,
    LANGUAGES,
    PREFERENCES,
    APPEARANCE,
    CORRECTIONS,
    GLIDE_TYPING,
    VOICE,
    DICTIONARY,
    CLIPBOARD,
    EMOJI,
    QUICK_BAR,
    WRITING_TOOLS,
    API_KEYS,
    QUICK_SEARCH,
    AUTOFILL,
    STATS,
    PRIVACY,
    LICENSES,
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
    val focusManager = LocalFocusManager.current
    var backStack by rememberSaveable(stateSaver = listSaver(
        save = { stack: List<SettingsRoute> -> stack.map { it.name } },
        restore = { saved ->
            val restored = saved.mapNotNull { name ->
                runCatching { SettingsRoute.valueOf(name) }.getOrNull()
            }
            if (restored.isEmpty()) listOf(SettingsRoute.HOME) else restored
        }
    )) { mutableStateOf(listOf(SettingsRoute.HOME)) }
    val stateHolder = rememberSaveableStateHolder()
    val route = backStack.last()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
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
        } else if (searchQuery.isNotEmpty()) {
            focusManager.clearFocus()
            searchQuery = ""
        }
    }

    BackHandler(
        enabled = !imeVisible && (backStack.size > 1 || searchQuery.isNotEmpty())
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

    val ioScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner, prefs.quickSearchEngine) {
        refreshImeStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshImeStatus()
                installedSearchApp = prefs.quickSearchEngine.installedApp(context)
                ioScope.launch(Dispatchers.IO) {
                    dictionaryStore.reloadIfModifiedOnDisk()
                    statsStore.reloadIfModifiedOnDisk()
                    packManager.refreshStates()
                    withContext(Dispatchers.Main) {
                        dictRevision++
                    }
                }
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
                modifier = Modifier.fillMaxSize(),
                targetState = route,
                transitionSpec = {
                    val direction = if (navigatingBack) -1 else 1
                    (slideInHorizontally(tween(290, easing = FastOutSlowInEasing)) { direction * it / 7 } +
                        scaleIn(initialScale = 0.985f, animationSpec = tween(290, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { -direction * it / 12 } +
                            scaleOut(targetScale = 0.985f, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            fadeOut(tween(150)))
                },
                label = "settingsNavigation"
            ) { activeRoute ->
                stateHolder.SaveableStateProvider(activeRoute.name) {
                    Column(Modifier.fillMaxSize()) {
                        if (activeRoute != SettingsRoute.HOME) SettingsHeader(activeRoute, ::navigateBack)
                        when (activeRoute) {
                            SettingsRoute.HOME -> HomeSettingsScreen(
                                prefs = prefs,
                                de = de,
                                stats = liveStats,
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
                            SettingsRoute.PREFERENCES -> PreferencesScreen(
                                prefs = prefs,
                                de = de,
                                onOpenQuickBar = { navigateTo(SettingsRoute.QUICK_BAR) },
                                onUpdatePrefs = onUpdatePrefs
                            )
                            SettingsRoute.APPEARANCE -> AppearanceScreen(prefs, de, onUpdatePrefs)
                            SettingsRoute.GLIDE_TYPING -> GlideTypingScreen(prefs, de, onUpdatePrefs)
                            SettingsRoute.QUICK_BAR -> QuickBarSettingsScreen(prefs, de, onUpdatePrefs)
                            SettingsRoute.QUICK_SEARCH -> QuickSearchSettingsScreen(prefs, installedSearchApp, onUpdatePrefs)
                            SettingsRoute.AUTOFILL -> AutofillSettingsScreen(
                                prefs = prefs,
                                de = de,
                                hasAutofillProvider = hasAutofillProvider,
                                dictionaryStore = dictionaryStore,
                                onUpdatePrefs = onUpdatePrefs
                            )
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
                            SettingsRoute.PRIVACY -> PrivacySettingsScreen(
                                prefs = prefs,
                                de = de,
                                onUpdatePrefs = onUpdatePrefs,
                                onOpenLicenses = { navigateTo(SettingsRoute.LICENSES) }
                            )
                            SettingsRoute.LICENSES -> LicensesScreen(de = de)
                            SettingsRoute.ABOUT -> AboutSettingsScreen(
                                de = de,
                                onOpenImeSystemSettings = {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                },
                                onShowImePicker = {
                                    val imm = context.getSystemService(InputMethodManager::class.java)
                                    imm?.showInputMethodPicker()
                                },
                                onOpenPrivacy = { navigateTo(SettingsRoute.PRIVACY) },
                                onOpenLicenses = { navigateTo(SettingsRoute.LICENSES) }
                            )
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHeader(route: SettingsRoute, onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = {
            Text(
                text = stringResource(route.label()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    )
}
internal fun SettingsRoute.label(): Int = when (this) {
    SettingsRoute.HOME -> R.string.settings_title
    SettingsRoute.LANGUAGES -> R.string.nav_languages_short
    SettingsRoute.PREFERENCES -> R.string.nav_preferences
    SettingsRoute.APPEARANCE -> R.string.nav_theme_short
    SettingsRoute.CORRECTIONS -> R.string.nav_text_correction
    SettingsRoute.GLIDE_TYPING -> R.string.nav_glide_typing
    SettingsRoute.VOICE -> R.string.nav_voice_typing
    SettingsRoute.DICTIONARY -> R.string.nav_dictionary_short
    SettingsRoute.CLIPBOARD -> R.string.nav_clipboard_short
    SettingsRoute.EMOJI -> R.string.nav_emojis_short
    SettingsRoute.QUICK_BAR -> R.string.nav_quick_bar
    SettingsRoute.WRITING_TOOLS -> R.string.nav_writing_tools
    SettingsRoute.API_KEYS -> R.string.nav_api_keys
    SettingsRoute.QUICK_SEARCH -> R.string.nav_quick_search
    SettingsRoute.AUTOFILL -> R.string.nav_autofill
    SettingsRoute.STATS -> R.string.nav_typing_stats
    SettingsRoute.PRIVACY -> R.string.nav_privacy
    SettingsRoute.LICENSES -> R.string.nav_licenses
    SettingsRoute.ABOUT -> R.string.nav_about
}
private fun SettingsRoute.icon(): ImageVector = when (this) {
    SettingsRoute.HOME -> Icons.Rounded.Keyboard
    SettingsRoute.LANGUAGES -> Icons.Rounded.Language
    SettingsRoute.PREFERENCES -> Icons.Rounded.Tune
    SettingsRoute.APPEARANCE -> Icons.Rounded.Palette
    SettingsRoute.CORRECTIONS -> Icons.Rounded.Spellcheck
    SettingsRoute.GLIDE_TYPING -> Icons.Rounded.Gesture
    SettingsRoute.VOICE -> Icons.Rounded.Mic
    SettingsRoute.DICTIONARY -> Icons.Rounded.Book
    SettingsRoute.CLIPBOARD -> Icons.Rounded.ContentPaste
    SettingsRoute.EMOJI -> Icons.Rounded.EmojiEmotions
    SettingsRoute.QUICK_BAR -> Icons.Rounded.DashboardCustomize
    SettingsRoute.WRITING_TOOLS -> Icons.Rounded.AutoAwesome
    SettingsRoute.API_KEYS -> Icons.Rounded.Key
    SettingsRoute.QUICK_SEARCH -> Icons.Rounded.Search
    SettingsRoute.AUTOFILL -> Icons.Rounded.Key
    SettingsRoute.STATS -> Icons.Rounded.BarChart
    SettingsRoute.PRIVACY -> Icons.Rounded.Lock
    SettingsRoute.LICENSES -> Icons.Rounded.Description
    SettingsRoute.ABOUT -> Icons.Rounded.Info
}
@Composable
private fun SettingsRoute.iconTint(): Color {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SettingsRoute.LANGUAGES, SettingsRoute.PREFERENCES, SettingsRoute.APPEARANCE -> scheme.primary
        SettingsRoute.CORRECTIONS, SettingsRoute.GLIDE_TYPING, SettingsRoute.VOICE, SettingsRoute.DICTIONARY -> scheme.tertiary
        SettingsRoute.CLIPBOARD, SettingsRoute.EMOJI, SettingsRoute.QUICK_BAR -> scheme.secondary
        SettingsRoute.WRITING_TOOLS, SettingsRoute.API_KEYS -> scheme.primary
        SettingsRoute.QUICK_SEARCH, SettingsRoute.AUTOFILL -> scheme.tertiary
        SettingsRoute.STATS, SettingsRoute.PRIVACY, SettingsRoute.LICENSES, SettingsRoute.ABOUT, SettingsRoute.HOME -> scheme.secondary
    }
}
private fun themePreviewAccent(mode: ThemeMode, fallback: Color): Color = when (mode) {
    ThemeMode.SLATE -> Color(0xFF5F6368)
    ThemeMode.EXPRESSIVE_LAVENDER -> Color(0xFF6750A4)
    ThemeMode.DYNAMIC -> fallback
    ThemeMode.COBALT -> Color(0xFF1A73E8)
    ThemeMode.AMOLED_DARK -> Color(0xFF2B2B33)
}
private fun SettingsRoute.liveSummary(
    prefs: TapwingPrefs,
    de: Boolean,
    stats: TypingStats,
    hasAutofillProvider: Boolean
): String = when (this) {
    SettingsRoute.LANGUAGES -> {
        val active = KeyboardLocales.specFor(prefs.activeLocale).nativeLabel.substringBefore(" ")
        val count = prefs.enabledLocales.size
        if (count > 1) "$active · +${count - 1}" else active
    }
    SettingsRoute.PREFERENCES -> {
        val haptic = if (prefs.hapticFeedback) (if (de) "Haptik an" else "Haptics on") else (if (de) "Haptik aus" else "Haptics off")
        "${prefs.keyboardScalePercent}% · $haptic"
    }
    SettingsRoute.APPEARANCE -> {
        val themeName = when (prefs.themeMode) {
            ThemeMode.SLATE -> if (de) "Schiefer" else "Slate"
            ThemeMode.EXPRESSIVE_LAVENDER -> if (de) "Lavendel" else "Lavender"
            ThemeMode.DYNAMIC -> "Material You"
            ThemeMode.COBALT -> if (de) "Kobaltblau" else "Cobalt Blue"
            ThemeMode.AMOLED_DARK -> "AMOLED Dark"
        }
        val mode = when (prefs.darkMode) {
            DarkModePreference.SYSTEM -> if (de) "System" else "Auto"
            DarkModePreference.LIGHT -> if (de) "Hell" else "Light"
            DarkModePreference.DARK -> if (de) "Dunkel" else "Dark"
        }
        if (prefs.appleModeEnabled) "$themeName · ${if (de) "Glas" else "Glass"}" else "$themeName · $mode"
    }
    SettingsRoute.CORRECTIONS -> {
        val parts = buildList {
            if (prefs.autoCorrect) add(if (de) "Autokorrektur" else "Auto-correct")
            if (prefs.smartBackspaceUndo) add(if (de) "Rückgängig" else "Undo")
            if (prefs.incognitoMode) add(if (de) "Inkognito" else "Incognito")
        }
        if (parts.isEmpty()) (if (de) "Manuell" else "Manual") else parts.joinToString(" · ")
    }
    SettingsRoute.GLIDE_TYPING -> {
        if (prefs.glideTypingEnabled) {
            if (de) "Aktiv · 2D-Cursor & Wisch-Löschen" else "On · 2D cursor & swipe delete"
        } else {
            if (de) "Aus · 2D-Cursor aktiv" else "Off · 2D cursor active"
        }
    }
    SettingsRoute.VOICE -> {
        val modelShort = LocalModelCatalog.findById(prefs.selectedWhisperModelId)?.shortEngineLabel ?: "Sherpa"
        when (prefs.voiceEngine) {
            VoiceEngineMode.AUTO_LOCAL_FIRST -> if (de) "Automatisch · $modelShort" else "Auto · $modelShort"
            VoiceEngineMode.LOCAL_WHISPER -> if (de) "Lokal · $modelShort" else "On-device · $modelShort"
            VoiceEngineMode.ON_DEVICE_SPEECH -> "Android On-Device"
            VoiceEngineMode.CLOUD -> if (de) "Cloud-Diktat" else "Cloud speech"
            VoiceEngineMode.GEMINI_LIVE -> "Gemini Live"
            VoiceEngineMode.SYSTEM_SPEECH -> if (de) "System-Spracheingabe" else "System speech"
        }
    }
    SettingsRoute.DICTIONARY -> {
        if (prefs.learningEnabled) {
            if (de) "Adaptives Lernen · Offline-Pakete" else "Adaptive learning · Offline packs"
        } else {
            if (de) "Persönliches Wörterbuch" else "Personal dictionary"
        }
    }
    SettingsRoute.CLIPBOARD -> {
        if (prefs.clipboardEnabled) {
            if (de) "Verlauf aktiv · OCR & Screenshots" else "History on · OCR & screenshots"
        } else {
            if (de) "Deaktiviert" else "Off"
        }
    }
    SettingsRoute.EMOJI -> {
        if (prefs.candidateEmojiSuggestions) {
            if (de) "Vorschläge an · Größe ${prefs.emojiScale}" else "Suggestions on · Size ${prefs.emojiScale}"
        } else {
            if (de) "Größe ${prefs.emojiScale}" else "Size ${prefs.emojiScale}"
        }
    }
    SettingsRoute.QUICK_BAR -> {
        if (de) "${prefs.quickBarActions.size} Aktionen in der Leiste" else "${prefs.quickBarActions.size} toolbar shortcuts"
    }
    SettingsRoute.WRITING_TOOLS -> {
        if (!prefs.aiWritingToolsEnabled) {
            if (de) "Aus" else "Off"
        } else {
            when (prefs.aiProvider) {
                AiProviderType.LOCAL_LLM -> {
                    val spec = LocalModelCatalog.findById(prefs.selectedLlmModelId)
                    val label = spec?.shortEngineLabel ?: "Lokal-KI"
                    if (de) "Lokal-KI · $label" else "Local AI · $label"
                }
                AiProviderType.LOCAL_RULES -> if (de) "Sofort (Offline)" else "Instant (Offline)"
                AiProviderType.GEMINI_NANO -> "Gemini Nano"
                else -> "${AiModelCatalog.displayName(prefs.aiProvider, de)} · Cloud"
            }
        }
    }
    SettingsRoute.API_KEYS -> {
        if (de) "Verschlüsselter Schlüssel-Tresor" else "Encrypted provider vault"
    }
    SettingsRoute.QUICK_SEARCH -> prefs.quickSearchEngine.label
    SettingsRoute.AUTOFILL -> {
        val count = prefs.personalProfile.filledFieldCount
        when {
            count > 0 && prefs.smartFillEnabled -> if (de) "Profil aktiv ($count Felder) · Smart-Fill" else "Profile active ($count fields) · Smart Fill"
            prefs.smartFillEnabled && prefs.smartProfileAutoLearn -> if (de) "Auto-Erkennung aktiv" else "Auto-detect active"
            hasAutofillProvider && prefs.inlineAutofillEnabled -> if (de) "Passwortmanager aktiv" else "Password manager on"
            else -> if (de) "Profil & Passwörter einrichten" else "Set up profile & passwords"
        }
    }
    SettingsRoute.STATS -> {
        if (stats.estimatedWpm > 0) {
            "${stats.estimatedWpm} WPM · ${stats.wordsTyped} ${if (de) "Wörter" else "words"}"
        } else {
            if (de) "100% lokal auf deinem Gerät" else "100% on-device insights"
        }
    }
    SettingsRoute.PRIVACY -> {
        val cloudCount = prefs.consentedCloudProviders.size
        if (cloudCount == 0) {
            if (de) "100 % lokal · Keine Cloud-Freigaben" else "100% offline · No cloud consents"
        } else {
            if (de) "$cloudCount Cloud-Anbieter freigegeben" else "$cloudCount cloud provider(s) consented"
        }
    }
    SettingsRoute.LICENSES -> if (de) "Bibliotheken, Modelle & Wörterbücher" else "Libraries, models & dictionaries"
    SettingsRoute.ABOUT -> "Version ${BuildConfig.VERSION_NAME} · Tapwing"
    SettingsRoute.HOME -> ""
}
@Composable
private fun HomeSettingsScreen(
    prefs: TapwingPrefs,
    de: Boolean,
    stats: TypingStats,
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
    val context = LocalContext.current
    val search = searchQuery.trim()
    val searchMatches = remember(search, de) {
        if (search.isEmpty()) emptyList()
        else SettingsCatalog.search(search) { r -> context.getString(r.label()) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Pixel / Gboard Expressive Header + Pill Search Bar
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Tapwing",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                AppleSearchPill(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = stringResource(R.string.settings_search_placeholder)
                )
            }
        }

        if (search.isNotEmpty()) {
            item(key = "search_results") {
                if (searchMatches.isEmpty()) {
                    AppleSectionFooter(stringResource(R.string.settings_no_results))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        searchMatches.forEachIndexed { index, target ->
                            ExpressiveNavCard(
                                icon = target.icon(),
                                iconTint = target.iconTint(),
                                title = stringResource(target.label()),
                                subtitle = target.liveSummary(prefs, de, stats, hasAutofillProvider),
                                shape = expressiveItemShape(index, searchMatches.size),
                                swatchColor = if (target == SettingsRoute.APPEARANCE) {
                                    themePreviewAccent(prefs.themeMode, MaterialTheme.colorScheme.primary)
                                } else null,
                                onClick = { onNavigate(target) }
                            )
                        }
                    }
                }
            }
        } else {
            // Setup Hero Card (when not enabled/active) OR Live Keyboard Test Pill (when ready)
            item(key = "setup_or_test") {
                AnimatedContent(
                    targetState = imeEnabled to imeSelected,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                    label = "setupStatus"
                ) { (enabled, selected) ->
                    if (enabled && selected) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Keyboard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_setup_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        if (!enabled) R.string.setup_enable_tapwing else R.string.setup_select_tapwing
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                                Button(
                                    onClick = if (!enabled) onOpenImeSystemSettings else onShowImePicker,
                                    shape = CircleShape
                                ) {
                                    Text(stringResource(if (!enabled) R.string.settings_enable else R.string.settings_select))
                                }
                            }
                        }
                    }
                }
            }

            // Gboard / Pixel Material 3 Expressive Grouped Sections
            SettingsSection.entries.forEach { section ->
                item(key = "section_${section.name}") {
                    Column {
                        AppleSectionHeader(stringResource(section.titleRes))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            section.routes.forEachIndexed { index, target ->
                                ExpressiveNavCard(
                                    icon = target.icon(),
                                    iconTint = target.iconTint(),
                                    title = stringResource(target.label()),
                                    subtitle = target.liveSummary(prefs, de, stats, hasAutofillProvider),
                                    shape = expressiveItemShape(index, section.routes.size),
                                    swatchColor = if (target == SettingsRoute.APPEARANCE) {
                                        themePreviewAccent(prefs.themeMode, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    onClick = { onNavigate(target) }
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
                    MinimalDropdownRow(
                        title = stringResource(R.string.search_engine),
                        selectedValue = prefs.quickSearchEngine,
                        options = QuickSearchEngine.entries.map { engine ->
                            DropdownOption(engine, engine.label)
                        },
                        onSelect = { engine ->
                            onUpdatePrefs {
                                if (it.quickSearchEngine == engine) it
                                else it.copy(quickSearchEngine = engine, quickSearchAppPackage = "")
                            }
                        }
                    )
                    AppleHairlineDivider(startIndent = 16.dp)
                    val openTargets = buildList {
                        add(DropdownOption("", stringResource(R.string.search_browser)))
                        if (installedApp != null) {
                            add(
                                DropdownOption(
                                    installedApp.packageName,
                                    stringResource(R.string.search_app, installedApp.label)
                                )
                            )
                        }
                    }
                    MinimalDropdownRow(
                        title = stringResource(R.string.search_open_in),
                        selectedValue = if (appSelected) installedApp.packageName else "",
                        options = openTargets,
                        onSelect = { pkg ->
                            onUpdatePrefs { it.copy(quickSearchAppPackage = pkg) }
                        }
                    )
                }
                AppleSectionFooter(stringResource(
                    if (installedApp == null) R.string.search_app_unavailable else R.string.search_browser_fallback
                ))
            }
        }
    }
}

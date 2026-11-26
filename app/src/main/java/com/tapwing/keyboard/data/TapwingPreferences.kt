package com.tapwing.keyboard.data

import android.content.Context
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.io.IOException

enum class KeyboardHeightMode(val rowHeightDp: Int, val defaultScalePercent: Int) {
    COMPACT(44, 88),
    NORMAL(48, 100),
    TALL(56, 116)
}

enum class ThemeMode {
    SLATE,
    EXPRESSIVE_LAVENDER,
    DYNAMIC,
    COBALT,
    AMOLED_DARK
}

enum class DarkModePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class OneHandedMode {
    OFF,
    LEFT,
    RIGHT
}

enum class SplitKeyboardMode {
    OFF,
    AUTO,
    ALWAYS
}

enum class VoiceDictationMode {
    NATURAL_CLEANUP,
    STANDARD
}

enum class VoiceEngineMode {
    AUTO_LOCAL_FIRST,
    LOCAL_WHISPER,
    ON_DEVICE_SPEECH,
    CLOUD,
    GEMINI_LIVE,
    SYSTEM_SPEECH;

    val usesCloudAudio: Boolean get() = this == CLOUD || this == GEMINI_LIVE
}

enum class AiProviderType {
    LOCAL_RULES,
    LOCAL_LLM,
    GEMINI_NANO,
    GROQ_API,
    GEMINI_API,
    OPENROUTER_API,
    XAI_API,
    OPENAI_COMPATIBLE
}

enum class ClipboardRetention(val durationMs: Long) {
    HOUR_1(60L * 60L * 1000L),
    HOURS_24(24L * 60L * 60L * 1000L),
    DAYS_7(7L * 24L * 60L * 60L * 1000L),
    DAYS_30(30L * 24L * 60L * 60L * 1000L),
    NEVER(-1L)
}

data class TapwingPrefs(
    val activeLocale: String = "de",
    val enabledLocales: List<String> = listOf("de", "en"),
    val appUiLocale: String = "system",
    val multilingualPredictions: Boolean = true,
    val showNumberRow: Boolean = true,
    val showGermanUmlautKeys: Boolean = false,
    val showSpacebarLanguageLabel: Boolean = true,
    val enterKeyAlwaysNewline: Boolean = false,
    val roundedExpressiveKeys: Boolean = true,
    val showKeyBorders: Boolean = false,
    val keyboardHeight: KeyboardHeightMode = KeyboardHeightMode.NORMAL,
    val keyboardScalePercent: Int = 100,
    val keyboardBottomLiftDp: Int = 0,
    val longPressDelayMs: Int = 280,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val splitKeyboardMode: SplitKeyboardMode = SplitKeyboardMode.AUTO,
    val hapticFeedback: Boolean = true,
    val soundOnKeypress: Boolean = false,

    val themeMode: ThemeMode = ThemeMode.SLATE,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val appleModeEnabled: Boolean = false,

    val autoCorrect: Boolean = true,
    val spellGrammarChips: Boolean = true,
    val proactiveUndo: Boolean = true,
    val smartBackspaceUndo: Boolean = true,
    val glideTypingEnabled: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val learningEnabled: Boolean = true,
    val perAppLearningEnabled: Boolean = true,
    val inlineAutofillEnabled: Boolean = true,
    val smartFillEnabled: Boolean = true,
    val smartProfileAutoLearn: Boolean = false,
    val profileAutoLearnNoticeSeen: Boolean = false,
    val personalProfile: PersonalProfile = PersonalProfile(),
    val incognitoMode: Boolean = false,
    val snippetUserName: String = "",
    val snippetUserEmail: String = "",
    val inlineMathEnabled: Boolean = true,
    val swipeBackspaceDeleteWord: Boolean = true,
    val showWritingToolsIcon: Boolean = true,
    val showClipboardIcon: Boolean = true,
    val showScanTextIcon: Boolean = true,
    val quickBarActions: List<QuickBarAction> = QuickBarAction.DEFAULT_ACTIONS,
    val quickSearchEngine: QuickSearchEngine = QuickSearchEngine.GOOGLE,
    val quickSearchAppPackage: String = "",

    val voiceMode: VoiceDictationMode = VoiceDictationMode.NATURAL_CLEANUP,
    val voiceEngine: VoiceEngineMode = VoiceEngineMode.AUTO_LOCAL_FIRST,
    val selectedWhisperModelId: String = "sherpa-fastconformer-de-pc-int8",
    val voiceCleanupPrompt: String = "",
    val voiceCleanupUseSystemLanguage: Boolean = true,
    val voiceRemoveFillers: Boolean = true,
    val voiceAutoPunctuate: Boolean = true,
    val voiceAutoBulletLists: Boolean = true,
    val voiceMultilingualCodeSwitching: Boolean = true,
    val voiceStrictLanguageLock: Boolean = false,
    val voiceMinimizeButton: Boolean = true,
    val voiceRotationGraceMs: Long = 1500L,
    val voiceAiPolish: Boolean = true,
    val voiceOnboardingSeen: Boolean = false,

    val aiWritingToolsEnabled: Boolean = true,
    val aiSupportAllKeyboards: Boolean = true,
    val selectedLlmModelId: String = "llm-lfm2.5-1.2b-q4km",
    val aiProvider: AiProviderType = AiProviderType.LOCAL_RULES,
    val aiModel: String = "gemini-3.8-flash",
    val aiCustomEndpoint: String = "",
    val aiFallbackProvider: AiProviderType = AiProviderType.LOCAL_RULES,
    val aiFallbackModel: String = "",
    val dictationProvider: AiProviderType = AiProviderType.GROQ_API,
    val dictationModel: String = "whisper-large-v3-turbo",
    val dictationFallbackProvider: AiProviderType = AiProviderType.LOCAL_RULES,
    val dictationFallbackModel: String = "",
    val cleanupProvider: AiProviderType = AiProviderType.LOCAL_RULES,
    val cleanupModel: String = "",
    val cleanupFallbackProvider: AiProviderType = AiProviderType.LOCAL_RULES,
    val cleanupFallbackModel: String = "",
    val consentedCloudProviders: Set<String> = emptySet(),
    val aiOnboardingSeen: Boolean = false,

    val clipboardEnabled: Boolean = true,
    val clipboardRetention: ClipboardRetention = ClipboardRetention.DAYS_7,
    val clipboardMaxItems: Int = 100,
    val screenshotSuggestionChip: Boolean = true,

    val emojiScale: Int = 26,
    val candidateEmojiSuggestions: Boolean = true,

    val highContrastKeys: Boolean = false,
    val largeKeyLabels: Boolean = false
)

private val Context.tapwingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tapwing_preferences"
)

class TapwingPreferencesRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    // Non-secret keyboard configuration lives in device-protected storage so Direct Boot IME works cleanly.
    // Personal profile data lives strictly in Credential-Encrypted storage via PersonalProfileStorage.
    private val storageContext: Context =
        ContextCompat.createDeviceProtectedStorageContext(appContext)
            ?: appContext

    private val profileStorage = PersonalProfileStorage(appContext)

    @Volatile
    private var legacyDeCleanedUp = false

    private object Keys {
        val ACTIVE_LOCALE = stringPreferencesKey("active_locale")
        val ENABLED_LOCALES = stringPreferencesKey("enabled_locales")
        val APP_UI_LOCALE = stringPreferencesKey("app_ui_locale")
        val MULTILINGUAL_PREDICTIONS = booleanPreferencesKey("multilingual_predictions")
        val SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        val SHOW_GERMAN_UMLAUTS = booleanPreferencesKey("show_german_umlauts")
        val SHOW_SPACEBAR_LABEL = booleanPreferencesKey("show_spacebar_label")
        val ENTER_KEY_ALWAYS_NEWLINE = booleanPreferencesKey("enter_key_always_newline")
        val ROUNDED_EXPRESSIVE_KEYS = booleanPreferencesKey("rounded_expressive_keys")
        val SHOW_KEY_BORDERS = booleanPreferencesKey("show_key_borders")
        val KEYBOARD_HEIGHT = stringPreferencesKey("keyboard_height")
        val KEYBOARD_SCALE_PERCENT = intPreferencesKey("keyboard_scale_percent")
        val KEYBOARD_BOTTOM_LIFT_DP = intPreferencesKey("keyboard_bottom_lift_dp")
        val LONG_PRESS_DELAY_MS = intPreferencesKey("long_press_delay_ms")
        val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        val SPLIT_KEYBOARD_MODE = stringPreferencesKey("split_keyboard_mode")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_ON_KEYPRESS = booleanPreferencesKey("sound_on_keypress")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val APPLE_MODE_ENABLED = booleanPreferencesKey("apple_mode_enabled")
        val HIGH_CONTRAST_KEYS = booleanPreferencesKey("high_contrast_keys")
        val LARGE_KEY_LABELS = booleanPreferencesKey("large_key_labels")

        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val SPELL_GRAMMAR_CHIPS = booleanPreferencesKey("spell_grammar_chips")
        val PROACTIVE_UNDO = booleanPreferencesKey("proactive_undo")
        val SMART_BACKSPACE_UNDO = booleanPreferencesKey("smart_backspace_undo")
        val GLIDE_TYPING_ENABLED = booleanPreferencesKey("glide_typing_enabled")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val PER_APP_LEARNING_ENABLED = booleanPreferencesKey("per_app_learning_enabled")
        val INLINE_AUTOFILL_ENABLED = booleanPreferencesKey("inline_autofill_enabled")
        val SMART_FILL_ENABLED = booleanPreferencesKey("smart_fill_enabled")
        val SMART_PROFILE_AUTO_LEARN = booleanPreferencesKey("smart_profile_auto_learn")
        val PROFILE_AUTO_LEARN_NOTICE_SEEN = booleanPreferencesKey("profile_auto_learn_notice_seen")
        val PERSONAL_PROFILE_JSON = stringPreferencesKey("personal_profile_json")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val SNIPPET_USER_NAME = stringPreferencesKey("snippet_user_name")
        val SNIPPET_USER_EMAIL = stringPreferencesKey("snippet_user_email")
        val INLINE_MATH_ENABLED = booleanPreferencesKey("inline_math_enabled")
        val SWIPE_BACKSPACE_WORD = booleanPreferencesKey("swipe_backspace_word")
        val SHOW_WRITING_TOOLS_ICON = booleanPreferencesKey("show_writing_tools_icon")
        val SHOW_CLIPBOARD_ICON = booleanPreferencesKey("show_clipboard_icon")
        val SHOW_SCAN_TEXT_ICON = booleanPreferencesKey("show_scan_text_icon")
        val QUICK_SEARCH_ENGINE = stringPreferencesKey("quick_search_engine")
        val QUICK_SEARCH_APP = stringPreferencesKey("quick_search_app")
        val QUICK_BAR_ACTIONS = stringPreferencesKey("quick_bar_actions")

        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val SELECTED_WHISPER_MODEL_ID = stringPreferencesKey("selected_whisper_model_id")
        val VOICE_CLEANUP_PROMPT = stringPreferencesKey("voice_cleanup_prompt")
        val VOICE_CLEANUP_USE_SYSTEM_LANG = booleanPreferencesKey("voice_cleanup_use_system_lang")
        val VOICE_REMOVE_FILLERS = booleanPreferencesKey("voice_remove_fillers")
        val VOICE_AUTO_PUNCTUATE = booleanPreferencesKey("voice_auto_punctuate")
        val VOICE_AUTO_BULLET_LISTS = booleanPreferencesKey("voice_auto_bullet_lists")
        val VOICE_MULTILINGUAL_CODE_SWITCHING = booleanPreferencesKey("voice_multilingual_code_switching")
        val VOICE_STRICT_LANGUAGE_LOCK = booleanPreferencesKey("voice_strict_language_lock")
        val VOICE_MINIMIZE_BUTTON = booleanPreferencesKey("voice_minimize_button")
        val VOICE_ROTATION_GRACE_MS = longPreferencesKey("voice_rotation_grace_ms")
        val VOICE_AI_POLISH = booleanPreferencesKey("voice_ai_polish")
        val VOICE_ONBOARDING_SEEN = booleanPreferencesKey("voice_onboarding_seen")

        val AI_WRITING_TOOLS_ENABLED = booleanPreferencesKey("ai_writing_tools_enabled")
        val AI_SUPPORT_ALL_KEYBOARDS = booleanPreferencesKey("ai_support_all_keyboards")
        val SELECTED_LLM_MODEL_ID = stringPreferencesKey("selected_llm_model_id")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_CUSTOM_ENDPOINT = stringPreferencesKey("ai_custom_endpoint")
        val AI_FALLBACK_PROVIDER = stringPreferencesKey("ai_fallback_provider")
        val AI_FALLBACK_MODEL = stringPreferencesKey("ai_fallback_model")
        val DICTATION_PROVIDER = stringPreferencesKey("dictation_provider")
        val DICTATION_MODEL = stringPreferencesKey("dictation_model")
        val DICTATION_FALLBACK_PROVIDER = stringPreferencesKey("dictation_fallback_provider")
        val DICTATION_FALLBACK_MODEL = stringPreferencesKey("dictation_fallback_model")
        val CLEANUP_PROVIDER = stringPreferencesKey("cleanup_provider")
        val CLEANUP_MODEL = stringPreferencesKey("cleanup_model")
        val CLEANUP_FALLBACK_PROVIDER = stringPreferencesKey("cleanup_fallback_provider")
        val CLEANUP_FALLBACK_MODEL = stringPreferencesKey("cleanup_fallback_model")
        val CLOUD_CONSENTED_PROVIDERS = stringPreferencesKey("cloud_consented_providers")
        val AI_ONBOARDING_SEEN = booleanPreferencesKey("ai_onboarding_seen")

        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIPBOARD_RETENTION = stringPreferencesKey("clipboard_retention")
        val CLIPBOARD_MAX_ITEMS = intPreferencesKey("clipboard_max_items")
        val SCREENSHOT_SUGGESTION_CHIP = booleanPreferencesKey("screenshot_suggestion_chip")

        val EMOJI_SCALE = intPreferencesKey("emoji_scale")
        val CANDIDATE_EMOJI_SUGGESTIONS = booleanPreferencesKey("candidate_emoji_suggestions")
    }

    fun reloadAfterUnlock() {
        profileStorage.reloadAfterUnlock()
    }

    val prefsFlow: Flow<TapwingPrefs> = combine(
        storageContext.tapwingDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .onEach { p ->
                if (!legacyDeCleanedUp && isUserUnlocked(appContext) && hasLegacyPersonalKeys(p)) {
                    legacyDeCleanedUp = true
                    storageContext.tapwingDataStore.edit { mutable ->
                        migrateAndStripLegacyPersonalKeys(mutable)
                    }
                }
            },
        profileStorage.profileFlow
    ) { prefs, ceProfile ->
        val resolvedProfile = if (isUserUnlocked(appContext)) ceProfile else PersonalProfile()
        fromPreferences(prefs, resolvedProfile)
    }

    suspend fun update(transform: (TapwingPrefs) -> TapwingPrefs) {
        storageContext.tapwingDataStore.edit { mutable ->
            if (isUserUnlocked(appContext)) {
                migrateAndStripLegacyPersonalKeys(mutable)
            }
            val activeProfile = if (isUserUnlocked(appContext)) profileStorage.load() else PersonalProfile()
            val current = fromPreferences(mutable, activeProfile)
            val next = transform(current)
            if (isUserUnlocked(appContext) && next.personalProfile != activeProfile) {
                profileStorage.save(next.personalProfile)
            }
            writePreferences(mutable, next)
        }
    }

    private fun hasLegacyPersonalKeys(p: Preferences): Boolean =
        p.contains(Keys.PERSONAL_PROFILE_JSON) ||
            p.contains(Keys.SNIPPET_USER_NAME) ||
            p.contains(Keys.SNIPPET_USER_EMAIL)

    private fun migrateAndStripLegacyPersonalKeys(mutable: MutablePreferences) {
        val legacyJson = mutable[Keys.PERSONAL_PROFILE_JSON]
        val legacyName = mutable[Keys.SNIPPET_USER_NAME].orEmpty()
        val legacyEmail = mutable[Keys.SNIPPET_USER_EMAIL].orEmpty()
        profileStorage.migrateFromLegacyIfNeeded(
            legacyProfileJson = legacyJson,
            legacyUserName = legacyName,
            legacyUserEmail = legacyEmail
        )
        mutable.remove(Keys.PERSONAL_PROFILE_JSON)
        mutable.remove(Keys.SNIPPET_USER_NAME)
        mutable.remove(Keys.SNIPPET_USER_EMAIL)
    }

    private fun fromPreferences(p: Preferences, profile: PersonalProfile): TapwingPrefs {
        val showWritingLegacy = p[Keys.SHOW_WRITING_TOOLS_ICON] ?: true
        val showClipboardLegacy = p[Keys.SHOW_CLIPBOARD_ICON] ?: true
        val showScanLegacy = p[Keys.SHOW_SCAN_TEXT_ICON] ?: true
        val resolvedQuickBar = p[Keys.QUICK_BAR_ACTIONS]?.let { QuickBarAction.parse(it) }
            ?: QuickBarAction.fromLegacyToggles(
                showWritingTools = showWritingLegacy,
                showClipboard = showClipboardLegacy,
                showScanText = showScanLegacy
            )
        val enabledLocales = KeyboardLocales.parseEnabledLocales(p[Keys.ENABLED_LOCALES])
        val activeLocale = (p[Keys.ACTIVE_LOCALE] ?: enabledLocales.first()).let { loc ->
            if (loc in enabledLocales) loc else enabledLocales.first()
        }

        return TapwingPrefs(
            activeLocale = activeLocale,
            enabledLocales = enabledLocales,
            appUiLocale = p[Keys.APP_UI_LOCALE] ?: "system",
            multilingualPredictions = p[Keys.MULTILINGUAL_PREDICTIONS] ?: true,
            showNumberRow = p[Keys.SHOW_NUMBER_ROW] ?: true,
            showGermanUmlautKeys = p[Keys.SHOW_GERMAN_UMLAUTS] ?: false,
            showSpacebarLanguageLabel = p[Keys.SHOW_SPACEBAR_LABEL] ?: true,
            enterKeyAlwaysNewline = p[Keys.ENTER_KEY_ALWAYS_NEWLINE] ?: false,
            roundedExpressiveKeys = p[Keys.ROUNDED_EXPRESSIVE_KEYS] ?: true,
            showKeyBorders = p[Keys.SHOW_KEY_BORDERS] ?: false,
            keyboardHeight = parseEnum(p[Keys.KEYBOARD_HEIGHT], KeyboardHeightMode.NORMAL),
            keyboardScalePercent = (p[Keys.KEYBOARD_SCALE_PERCENT] ?: 100).coerceIn(75, 135),
            keyboardBottomLiftDp = (p[Keys.KEYBOARD_BOTTOM_LIFT_DP] ?: 0).coerceIn(0, 48),
            longPressDelayMs = (p[Keys.LONG_PRESS_DELAY_MS] ?: 280).coerceIn(150, 500),
            oneHandedMode = parseEnum(p[Keys.ONE_HANDED_MODE], OneHandedMode.OFF),
            splitKeyboardMode = parseEnum(p[Keys.SPLIT_KEYBOARD_MODE], SplitKeyboardMode.AUTO),
            hapticFeedback = p[Keys.HAPTIC_FEEDBACK] ?: true,
            soundOnKeypress = p[Keys.SOUND_ON_KEYPRESS] ?: false,

            themeMode = parseThemeMode(p[Keys.THEME_MODE]),
            darkMode = parseEnum(p[Keys.DARK_MODE], DarkModePreference.SYSTEM),
            appleModeEnabled = p[Keys.APPLE_MODE_ENABLED] ?: false,

            autoCorrect = p[Keys.AUTO_CORRECT] ?: true,
            spellGrammarChips = p[Keys.SPELL_GRAMMAR_CHIPS] ?: true,
            proactiveUndo = p[Keys.PROACTIVE_UNDO] ?: true,
            smartBackspaceUndo = p[Keys.SMART_BACKSPACE_UNDO] ?: true,
            glideTypingEnabled = p[Keys.GLIDE_TYPING_ENABLED] ?: true,
            doubleSpacePeriod = p[Keys.DOUBLE_SPACE_PERIOD] ?: true,
            learningEnabled = p[Keys.LEARNING_ENABLED] ?: true,
            perAppLearningEnabled = p[Keys.PER_APP_LEARNING_ENABLED] ?: true,
            inlineAutofillEnabled = p[Keys.INLINE_AUTOFILL_ENABLED] ?: true,
            smartFillEnabled = p[Keys.SMART_FILL_ENABLED] ?: true,
            smartProfileAutoLearn = p[Keys.SMART_PROFILE_AUTO_LEARN] ?: false,
            profileAutoLearnNoticeSeen = p[Keys.PROFILE_AUTO_LEARN_NOTICE_SEEN] ?: false,
            personalProfile = profile,
            incognitoMode = p[Keys.INCOGNITO_MODE] ?: false,
            snippetUserName = profile.fullName,
            snippetUserEmail = profile.primaryEmail,
            inlineMathEnabled = p[Keys.INLINE_MATH_ENABLED] ?: true,
            swipeBackspaceDeleteWord = p[Keys.SWIPE_BACKSPACE_WORD] ?: true,
            showWritingToolsIcon = QuickBarAction.WRITING_TOOLS in resolvedQuickBar,
            showClipboardIcon = QuickBarAction.CLIPBOARD in resolvedQuickBar,
            showScanTextIcon = QuickBarAction.SCAN_TEXT in resolvedQuickBar,
            quickBarActions = resolvedQuickBar,
            quickSearchEngine = parseEnum(p[Keys.QUICK_SEARCH_ENGINE], QuickSearchEngine.GOOGLE),
            quickSearchAppPackage = p[Keys.QUICK_SEARCH_APP].orEmpty(),

            voiceMode = parseEnum(p[Keys.VOICE_MODE], VoiceDictationMode.NATURAL_CLEANUP),
            voiceEngine = parseEnum(p[Keys.VOICE_ENGINE], VoiceEngineMode.AUTO_LOCAL_FIRST),
            selectedWhisperModelId = p[Keys.SELECTED_WHISPER_MODEL_ID] ?: "sherpa-fastconformer-de-pc-int8",
            voiceCleanupPrompt = p[Keys.VOICE_CLEANUP_PROMPT] ?: "",
            voiceCleanupUseSystemLanguage = p[Keys.VOICE_CLEANUP_USE_SYSTEM_LANG] ?: true,
            voiceRemoveFillers = p[Keys.VOICE_REMOVE_FILLERS] ?: true,
            voiceAutoPunctuate = p[Keys.VOICE_AUTO_PUNCTUATE] ?: true,
            voiceAutoBulletLists = p[Keys.VOICE_AUTO_BULLET_LISTS] ?: true,
            voiceMultilingualCodeSwitching = p[Keys.VOICE_MULTILINGUAL_CODE_SWITCHING] ?: true,
            voiceStrictLanguageLock = p[Keys.VOICE_STRICT_LANGUAGE_LOCK] ?: false,
            voiceMinimizeButton = p[Keys.VOICE_MINIMIZE_BUTTON] ?: true,
            voiceRotationGraceMs = (p[Keys.VOICE_ROTATION_GRACE_MS] ?: 1500L).coerceIn(500L, 4000L),
            voiceAiPolish = p[Keys.VOICE_AI_POLISH] ?: true,
            voiceOnboardingSeen = p[Keys.VOICE_ONBOARDING_SEEN] ?: false,

            aiWritingToolsEnabled = p[Keys.AI_WRITING_TOOLS_ENABLED] ?: true,
            aiSupportAllKeyboards = p[Keys.AI_SUPPORT_ALL_KEYBOARDS] ?: true,
            selectedLlmModelId = p[Keys.SELECTED_LLM_MODEL_ID] ?: "llm-lfm2.5-1.2b-q4km",
            aiProvider = parseEnum(p[Keys.AI_PROVIDER], AiProviderType.LOCAL_RULES),
            aiModel = p[Keys.AI_MODEL] ?: "gemini-3.8-flash",
            aiCustomEndpoint = p[Keys.AI_CUSTOM_ENDPOINT] ?: "",
            aiFallbackProvider = parseEnum(p[Keys.AI_FALLBACK_PROVIDER], AiProviderType.LOCAL_RULES),
            aiFallbackModel = p[Keys.AI_FALLBACK_MODEL] ?: "",
            dictationProvider = p[Keys.DICTATION_PROVIDER]?.let { parseEnum(it, AiProviderType.GROQ_API) }
                ?: run {
                    val legacyAi = parseEnum(p[Keys.AI_PROVIDER], AiProviderType.LOCAL_RULES)
                    if (legacyAi == AiProviderType.GEMINI_API || legacyAi == AiProviderType.OPENAI_COMPATIBLE) {
                        legacyAi
                    } else {
                        AiProviderType.GROQ_API
                    }
                },
            dictationModel = p[Keys.DICTATION_MODEL] ?: "whisper-large-v3-turbo",
            dictationFallbackProvider = parseEnum(p[Keys.DICTATION_FALLBACK_PROVIDER], AiProviderType.LOCAL_RULES),
            dictationFallbackModel = p[Keys.DICTATION_FALLBACK_MODEL] ?: "",
            cleanupProvider = p[Keys.CLEANUP_PROVIDER]?.let { parseEnum(it, AiProviderType.LOCAL_RULES) }
                ?: parseEnum(p[Keys.AI_PROVIDER], AiProviderType.LOCAL_RULES),
            cleanupModel = p[Keys.CLEANUP_MODEL] ?: (p[Keys.AI_MODEL] ?: ""),
            cleanupFallbackProvider = parseEnum(p[Keys.CLEANUP_FALLBACK_PROVIDER], AiProviderType.LOCAL_RULES),
            cleanupFallbackModel = p[Keys.CLEANUP_FALLBACK_MODEL] ?: "",
            consentedCloudProviders = parseStringSet(p[Keys.CLOUD_CONSENTED_PROVIDERS]),
            aiOnboardingSeen = p[Keys.AI_ONBOARDING_SEEN] ?: false,

            clipboardEnabled = p[Keys.CLIPBOARD_ENABLED] ?: true,
            clipboardRetention = parseEnum(p[Keys.CLIPBOARD_RETENTION], ClipboardRetention.DAYS_7),
            clipboardMaxItems = (p[Keys.CLIPBOARD_MAX_ITEMS] ?: 100).coerceIn(20, 200),
            screenshotSuggestionChip = p[Keys.SCREENSHOT_SUGGESTION_CHIP] ?: true,

            emojiScale = (p[Keys.EMOJI_SCALE] ?: 26).coerceIn(20, 34),
            candidateEmojiSuggestions = p[Keys.CANDIDATE_EMOJI_SUGGESTIONS] ?: true,

            highContrastKeys = p[Keys.HIGH_CONTRAST_KEYS] ?: false,
            largeKeyLabels = p[Keys.LARGE_KEY_LABELS] ?: false
        )
    }

    private fun writePreferences(mutable: MutablePreferences, next: TapwingPrefs) {
        val cleanEnabled = KeyboardLocales.serializeEnabledLocales(next.enabledLocales)
        val enabledList = KeyboardLocales.parseEnabledLocales(cleanEnabled)
        val cleanActive = if (next.activeLocale in enabledList) next.activeLocale else enabledList.first()

        mutable[Keys.ACTIVE_LOCALE] = cleanActive
        mutable[Keys.ENABLED_LOCALES] = cleanEnabled
        mutable[Keys.APP_UI_LOCALE] = next.appUiLocale
        mutable[Keys.MULTILINGUAL_PREDICTIONS] = next.multilingualPredictions
        mutable[Keys.SHOW_NUMBER_ROW] = next.showNumberRow
        mutable[Keys.SHOW_GERMAN_UMLAUTS] = next.showGermanUmlautKeys
        mutable[Keys.SHOW_SPACEBAR_LABEL] = next.showSpacebarLanguageLabel
        mutable[Keys.ENTER_KEY_ALWAYS_NEWLINE] = next.enterKeyAlwaysNewline
        mutable[Keys.ROUNDED_EXPRESSIVE_KEYS] = next.roundedExpressiveKeys
        mutable[Keys.SHOW_KEY_BORDERS] = next.showKeyBorders
        mutable[Keys.KEYBOARD_HEIGHT] = next.keyboardHeight.name
        mutable[Keys.KEYBOARD_SCALE_PERCENT] = next.keyboardScalePercent.coerceIn(75, 135)
        mutable[Keys.KEYBOARD_BOTTOM_LIFT_DP] = next.keyboardBottomLiftDp.coerceIn(0, 48)
        mutable[Keys.LONG_PRESS_DELAY_MS] = next.longPressDelayMs.coerceIn(150, 500)
        mutable[Keys.ONE_HANDED_MODE] = next.oneHandedMode.name
        mutable[Keys.SPLIT_KEYBOARD_MODE] = next.splitKeyboardMode.name
        mutable[Keys.HAPTIC_FEEDBACK] = next.hapticFeedback
        mutable[Keys.SOUND_ON_KEYPRESS] = next.soundOnKeypress
        mutable[Keys.THEME_MODE] = next.themeMode.name
        mutable[Keys.DARK_MODE] = next.darkMode.name
        mutable[Keys.APPLE_MODE_ENABLED] = next.appleModeEnabled
        mutable[Keys.HIGH_CONTRAST_KEYS] = next.highContrastKeys
        mutable[Keys.LARGE_KEY_LABELS] = next.largeKeyLabels
        mutable[Keys.AUTO_CORRECT] = next.autoCorrect
        mutable[Keys.SPELL_GRAMMAR_CHIPS] = next.spellGrammarChips
        mutable[Keys.PROACTIVE_UNDO] = next.proactiveUndo
        mutable[Keys.SMART_BACKSPACE_UNDO] = next.smartBackspaceUndo
        mutable[Keys.GLIDE_TYPING_ENABLED] = next.glideTypingEnabled
        mutable[Keys.DOUBLE_SPACE_PERIOD] = next.doubleSpacePeriod
        mutable[Keys.LEARNING_ENABLED] = next.learningEnabled
        mutable[Keys.PER_APP_LEARNING_ENABLED] = next.perAppLearningEnabled
        mutable[Keys.INLINE_AUTOFILL_ENABLED] = next.inlineAutofillEnabled
        mutable[Keys.SMART_FILL_ENABLED] = next.smartFillEnabled
        mutable[Keys.SMART_PROFILE_AUTO_LEARN] = next.smartProfileAutoLearn
        mutable[Keys.PROFILE_AUTO_LEARN_NOTICE_SEEN] = next.profileAutoLearnNoticeSeen
        mutable.remove(Keys.PERSONAL_PROFILE_JSON)
        mutable[Keys.INCOGNITO_MODE] = next.incognitoMode
        mutable.remove(Keys.SNIPPET_USER_NAME)
        mutable.remove(Keys.SNIPPET_USER_EMAIL)
        mutable[Keys.INLINE_MATH_ENABLED] = next.inlineMathEnabled
        mutable[Keys.SWIPE_BACKSPACE_WORD] = next.swipeBackspaceDeleteWord
        mutable[Keys.QUICK_SEARCH_ENGINE] = next.quickSearchEngine.name
        mutable[Keys.QUICK_SEARCH_APP] = next.quickSearchEngine.selectedApp(next.quickSearchAppPackage)?.packageName.orEmpty()
        mutable[Keys.QUICK_BAR_ACTIONS] = QuickBarAction.serialize(next.quickBarActions)
        mutable[Keys.SHOW_WRITING_TOOLS_ICON] = QuickBarAction.WRITING_TOOLS in next.quickBarActions
        mutable[Keys.SHOW_CLIPBOARD_ICON] = QuickBarAction.CLIPBOARD in next.quickBarActions
        mutable[Keys.SHOW_SCAN_TEXT_ICON] = QuickBarAction.SCAN_TEXT in next.quickBarActions
        mutable[Keys.VOICE_MODE] = next.voiceMode.name
        mutable[Keys.VOICE_ENGINE] = next.voiceEngine.name
        mutable[Keys.SELECTED_WHISPER_MODEL_ID] = next.selectedWhisperModelId
        mutable[Keys.VOICE_CLEANUP_PROMPT] = next.voiceCleanupPrompt
        mutable[Keys.VOICE_CLEANUP_USE_SYSTEM_LANG] = next.voiceCleanupUseSystemLanguage
        mutable[Keys.VOICE_REMOVE_FILLERS] = next.voiceRemoveFillers
        mutable[Keys.VOICE_AUTO_PUNCTUATE] = next.voiceAutoPunctuate
        mutable[Keys.VOICE_AUTO_BULLET_LISTS] = next.voiceAutoBulletLists
        mutable[Keys.VOICE_MULTILINGUAL_CODE_SWITCHING] = next.voiceMultilingualCodeSwitching
        mutable[Keys.VOICE_STRICT_LANGUAGE_LOCK] = next.voiceStrictLanguageLock
        mutable[Keys.VOICE_MINIMIZE_BUTTON] = next.voiceMinimizeButton
        mutable[Keys.VOICE_ROTATION_GRACE_MS] = next.voiceRotationGraceMs.coerceIn(500L, 4000L)
        mutable[Keys.VOICE_AI_POLISH] = next.voiceAiPolish
        mutable[Keys.VOICE_ONBOARDING_SEEN] = next.voiceOnboardingSeen
        mutable[Keys.AI_WRITING_TOOLS_ENABLED] = next.aiWritingToolsEnabled
        mutable[Keys.AI_SUPPORT_ALL_KEYBOARDS] = next.aiSupportAllKeyboards
        mutable[Keys.SELECTED_LLM_MODEL_ID] = next.selectedLlmModelId
        mutable[Keys.AI_PROVIDER] = next.aiProvider.name
        mutable[Keys.AI_MODEL] = next.aiModel
        mutable[Keys.AI_CUSTOM_ENDPOINT] = next.aiCustomEndpoint
        mutable[Keys.AI_FALLBACK_PROVIDER] = next.aiFallbackProvider.name
        mutable[Keys.AI_FALLBACK_MODEL] = next.aiFallbackModel
        mutable[Keys.DICTATION_PROVIDER] = next.dictationProvider.name
        mutable[Keys.DICTATION_MODEL] = next.dictationModel
        mutable[Keys.DICTATION_FALLBACK_PROVIDER] = next.dictationFallbackProvider.name
        mutable[Keys.DICTATION_FALLBACK_MODEL] = next.dictationFallbackModel
        mutable[Keys.CLEANUP_PROVIDER] = next.cleanupProvider.name
        mutable[Keys.CLEANUP_MODEL] = next.cleanupModel
        mutable[Keys.CLEANUP_FALLBACK_PROVIDER] = next.cleanupFallbackProvider.name
        mutable[Keys.CLEANUP_FALLBACK_MODEL] = next.cleanupFallbackModel
        mutable[Keys.CLOUD_CONSENTED_PROVIDERS] = next.consentedCloudProviders
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .joinToString(",")
        mutable[Keys.AI_ONBOARDING_SEEN] = next.aiOnboardingSeen
        mutable[Keys.CLIPBOARD_ENABLED] = next.clipboardEnabled
        mutable[Keys.CLIPBOARD_RETENTION] = next.clipboardRetention.name
        mutable[Keys.CLIPBOARD_MAX_ITEMS] = next.clipboardMaxItems.coerceIn(20, 200)
        mutable[Keys.SCREENSHOT_SUGGESTION_CHIP] = next.screenshotSuggestionChip
        mutable.remove(booleanPreferencesKey("auto_copy_screenshots"))
        mutable[Keys.EMOJI_SCALE] = next.emojiScale.coerceIn(20, 34)
        mutable[Keys.CANDIDATE_EMOJI_SUGGESTIONS] = next.candidateEmojiSuggestions
    }

    companion object {
        fun isUserUnlocked(context: Context): Boolean {
            val um = context.getSystemService(UserManager::class.java)
            return um?.isUserUnlocked ?: true
        }

        internal fun parseThemeMode(raw: String?): ThemeMode = when (raw?.trim()) {
            null, "" -> ThemeMode.SLATE
            "PIXEL_SLATE" -> ThemeMode.SLATE
            "PIXEL_BLUE" -> ThemeMode.COBALT
            else -> parseEnum(raw, ThemeMode.SLATE)
        }

        internal fun parseStringSet(raw: String?): Set<String> =
            raw.orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

        private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T {
            if (raw.isNullOrBlank()) return default
            return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
        }
    }
}

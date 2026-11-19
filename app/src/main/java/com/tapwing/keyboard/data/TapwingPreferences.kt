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
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class KeyboardHeightMode(val rowHeightDp: Int, val defaultScalePercent: Int) {
    COMPACT(44, 88),
    NORMAL(48, 100),
    TALL(56, 116)
}

enum class ThemeMode {
    PIXEL_SLATE,
    EXPRESSIVE_LAVENDER,
    DYNAMIC,
    PIXEL_BLUE,
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

enum class VoiceDictationMode {
    NATURAL_CLEANUP,
    STANDARD
}

enum class AiProviderType {
    LOCAL_RULES,
    GEMINI_API,
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
    val showNumberRow: Boolean = true,
    val showGermanUmlautKeys: Boolean = false,
    val showSpacebarLanguageLabel: Boolean = false,
    val roundedExpressiveKeys: Boolean = true,
    val showKeyBorders: Boolean = false,
    val keyboardHeight: KeyboardHeightMode = KeyboardHeightMode.NORMAL,
    val keyboardScalePercent: Int = 100,
    val keyboardBottomLiftDp: Int = 0,
    val longPressDelayMs: Int = 280,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val hapticFeedback: Boolean = true,
    val soundOnKeypress: Boolean = false,

    val themeMode: ThemeMode = ThemeMode.PIXEL_SLATE,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val appleModeEnabled: Boolean = false,

    val autoCorrect: Boolean = true,
    val spellGrammarChips: Boolean = true,
    val proactiveUndo: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val learningEnabled: Boolean = true,
    val incognitoMode: Boolean = false,
    val inlineMathEnabled: Boolean = true,
    val swipeBackspaceDeleteWord: Boolean = true,
    val showWritingToolsIcon: Boolean = true,
    val showClipboardIcon: Boolean = true,
    val showScanTextIcon: Boolean = true,

    val voiceMode: VoiceDictationMode = VoiceDictationMode.NATURAL_CLEANUP,
    val voiceRemoveFillers: Boolean = true,
    val voiceAutoPunctuate: Boolean = true,
    val voiceStrictLanguageLock: Boolean = true,
    val voiceMinimizeButton: Boolean = true,
    val voiceRotationGraceMs: Long = 1500L,
    val voiceAiPolish: Boolean = false,
    val voiceOnboardingSeen: Boolean = false,

    val aiWritingToolsEnabled: Boolean = true,
    val aiSupportAllKeyboards: Boolean = true,
    val aiProvider: AiProviderType = AiProviderType.LOCAL_RULES,
    val aiModel: String = "gemini-2.5-flash",
    val aiCustomEndpoint: String = "",
    val aiOnboardingSeen: Boolean = false,

    val clipboardEnabled: Boolean = true,
    val clipboardRetention: ClipboardRetention = ClipboardRetention.DAYS_7,
    val clipboardMaxItems: Int = 100,
    val screenshotSuggestionChip: Boolean = true,

    val emojiScale: Int = 26,
    val candidateEmojiSuggestions: Boolean = true
)

private val Context.tapwingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tapwing_preferences"
)

class TapwingPreferencesRepository(context: Context) {

    // Non-secret keyboard configuration lives in device-protected storage so Direct Boot IME works cleanly.
    private val storageContext: Context =
        ContextCompat.createDeviceProtectedStorageContext(context.applicationContext)
            ?: context.applicationContext

    private object Keys {
        val ACTIVE_LOCALE = stringPreferencesKey("active_locale")
        val SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        val SHOW_GERMAN_UMLAUTS = booleanPreferencesKey("show_german_umlauts")
        val SHOW_SPACEBAR_LABEL = booleanPreferencesKey("show_spacebar_label")
        val ROUNDED_EXPRESSIVE_KEYS = booleanPreferencesKey("rounded_expressive_keys")
        val SHOW_KEY_BORDERS = booleanPreferencesKey("show_key_borders")
        val KEYBOARD_HEIGHT = stringPreferencesKey("keyboard_height")
        val KEYBOARD_SCALE_PERCENT = intPreferencesKey("keyboard_scale_percent")
        val KEYBOARD_BOTTOM_LIFT_DP = intPreferencesKey("keyboard_bottom_lift_dp")
        val LONG_PRESS_DELAY_MS = intPreferencesKey("long_press_delay_ms")
        val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_ON_KEYPRESS = booleanPreferencesKey("sound_on_keypress")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val APPLE_MODE_ENABLED = booleanPreferencesKey("apple_mode_enabled")

        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val SPELL_GRAMMAR_CHIPS = booleanPreferencesKey("spell_grammar_chips")
        val PROACTIVE_UNDO = booleanPreferencesKey("proactive_undo")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val INLINE_MATH_ENABLED = booleanPreferencesKey("inline_math_enabled")
        val SWIPE_BACKSPACE_WORD = booleanPreferencesKey("swipe_backspace_word")
        val SHOW_WRITING_TOOLS_ICON = booleanPreferencesKey("show_writing_tools_icon")
        val SHOW_CLIPBOARD_ICON = booleanPreferencesKey("show_clipboard_icon")
        val SHOW_SCAN_TEXT_ICON = booleanPreferencesKey("show_scan_text_icon")

        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val VOICE_REMOVE_FILLERS = booleanPreferencesKey("voice_remove_fillers")
        val VOICE_AUTO_PUNCTUATE = booleanPreferencesKey("voice_auto_punctuate")
        val VOICE_STRICT_LANGUAGE_LOCK = booleanPreferencesKey("voice_strict_language_lock")
        val VOICE_MINIMIZE_BUTTON = booleanPreferencesKey("voice_minimize_button")
        val VOICE_ROTATION_GRACE_MS = longPreferencesKey("voice_rotation_grace_ms")
        val VOICE_AI_POLISH = booleanPreferencesKey("voice_ai_polish")
        val VOICE_ONBOARDING_SEEN = booleanPreferencesKey("voice_onboarding_seen")

        val AI_WRITING_TOOLS_ENABLED = booleanPreferencesKey("ai_writing_tools_enabled")
        val AI_SUPPORT_ALL_KEYBOARDS = booleanPreferencesKey("ai_support_all_keyboards")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_CUSTOM_ENDPOINT = stringPreferencesKey("ai_custom_endpoint")
        val AI_ONBOARDING_SEEN = booleanPreferencesKey("ai_onboarding_seen")

        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIPBOARD_RETENTION = stringPreferencesKey("clipboard_retention")
        val CLIPBOARD_MAX_ITEMS = intPreferencesKey("clipboard_max_items")
        val SCREENSHOT_SUGGESTION_CHIP = booleanPreferencesKey("screenshot_suggestion_chip")

        val EMOJI_SCALE = intPreferencesKey("emoji_scale")
        val CANDIDATE_EMOJI_SUGGESTIONS = booleanPreferencesKey("candidate_emoji_suggestions")
    }

    val prefsFlow: Flow<TapwingPrefs> = storageContext.tapwingDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { fromPreferences(it) }

    suspend fun update(transform: (TapwingPrefs) -> TapwingPrefs) {
        storageContext.tapwingDataStore.edit { mutable ->
            val current = fromPreferences(mutable)
            val next = transform(current)
            writePreferences(mutable, next)
        }
    }

    private fun fromPreferences(p: Preferences): TapwingPrefs = TapwingPrefs(
        activeLocale = p[Keys.ACTIVE_LOCALE] ?: "de",
        showNumberRow = p[Keys.SHOW_NUMBER_ROW] ?: true,
        showGermanUmlautKeys = p[Keys.SHOW_GERMAN_UMLAUTS] ?: false,
        showSpacebarLanguageLabel = p[Keys.SHOW_SPACEBAR_LABEL] ?: false,
        roundedExpressiveKeys = p[Keys.ROUNDED_EXPRESSIVE_KEYS] ?: true,
        showKeyBorders = p[Keys.SHOW_KEY_BORDERS] ?: false,
        keyboardHeight = parseEnum(p[Keys.KEYBOARD_HEIGHT], KeyboardHeightMode.NORMAL),
        keyboardScalePercent = (p[Keys.KEYBOARD_SCALE_PERCENT] ?: 100).coerceIn(75, 135),
        keyboardBottomLiftDp = (p[Keys.KEYBOARD_BOTTOM_LIFT_DP] ?: 0).coerceIn(0, 48),
        longPressDelayMs = (p[Keys.LONG_PRESS_DELAY_MS] ?: 280).coerceIn(150, 500),
        oneHandedMode = parseEnum(p[Keys.ONE_HANDED_MODE], OneHandedMode.OFF),
        hapticFeedback = p[Keys.HAPTIC_FEEDBACK] ?: true,
        soundOnKeypress = p[Keys.SOUND_ON_KEYPRESS] ?: false,

        themeMode = parseEnum(p[Keys.THEME_MODE], ThemeMode.PIXEL_SLATE),
        darkMode = parseEnum(p[Keys.DARK_MODE], DarkModePreference.SYSTEM),
        appleModeEnabled = p[Keys.APPLE_MODE_ENABLED] ?: false,

        autoCorrect = p[Keys.AUTO_CORRECT] ?: true,
        spellGrammarChips = p[Keys.SPELL_GRAMMAR_CHIPS] ?: true,
        proactiveUndo = p[Keys.PROACTIVE_UNDO] ?: true,
        doubleSpacePeriod = p[Keys.DOUBLE_SPACE_PERIOD] ?: true,
        learningEnabled = p[Keys.LEARNING_ENABLED] ?: true,
        incognitoMode = p[Keys.INCOGNITO_MODE] ?: false,
        inlineMathEnabled = p[Keys.INLINE_MATH_ENABLED] ?: true,
        swipeBackspaceDeleteWord = p[Keys.SWIPE_BACKSPACE_WORD] ?: true,
        showWritingToolsIcon = p[Keys.SHOW_WRITING_TOOLS_ICON] ?: true,
        showClipboardIcon = p[Keys.SHOW_CLIPBOARD_ICON] ?: true,
        showScanTextIcon = p[Keys.SHOW_SCAN_TEXT_ICON] ?: true,

        voiceMode = parseEnum(p[Keys.VOICE_MODE], VoiceDictationMode.NATURAL_CLEANUP),
        voiceRemoveFillers = p[Keys.VOICE_REMOVE_FILLERS] ?: true,
        voiceAutoPunctuate = p[Keys.VOICE_AUTO_PUNCTUATE] ?: true,
        voiceStrictLanguageLock = p[Keys.VOICE_STRICT_LANGUAGE_LOCK] ?: true,
        voiceMinimizeButton = p[Keys.VOICE_MINIMIZE_BUTTON] ?: true,
        voiceRotationGraceMs = (p[Keys.VOICE_ROTATION_GRACE_MS] ?: 1500L).coerceIn(500L, 4000L),
        voiceAiPolish = p[Keys.VOICE_AI_POLISH] ?: false,
        voiceOnboardingSeen = p[Keys.VOICE_ONBOARDING_SEEN] ?: false,

        aiWritingToolsEnabled = p[Keys.AI_WRITING_TOOLS_ENABLED] ?: true,
        aiSupportAllKeyboards = p[Keys.AI_SUPPORT_ALL_KEYBOARDS] ?: true,
        aiProvider = parseEnum(p[Keys.AI_PROVIDER], AiProviderType.LOCAL_RULES),
        aiModel = p[Keys.AI_MODEL] ?: "gemini-2.5-flash",
        aiCustomEndpoint = p[Keys.AI_CUSTOM_ENDPOINT] ?: "",
        aiOnboardingSeen = p[Keys.AI_ONBOARDING_SEEN] ?: false,

        clipboardEnabled = p[Keys.CLIPBOARD_ENABLED] ?: true,
        clipboardRetention = parseEnum(p[Keys.CLIPBOARD_RETENTION], ClipboardRetention.DAYS_7),
        clipboardMaxItems = (p[Keys.CLIPBOARD_MAX_ITEMS] ?: 100).coerceIn(20, 200),
        screenshotSuggestionChip = p[Keys.SCREENSHOT_SUGGESTION_CHIP] ?: true,

        emojiScale = (p[Keys.EMOJI_SCALE] ?: 26).coerceIn(20, 34),
        candidateEmojiSuggestions = p[Keys.CANDIDATE_EMOJI_SUGGESTIONS] ?: true
    )

    private fun writePreferences(mutable: MutablePreferences, next: TapwingPrefs) {
        mutable[Keys.ACTIVE_LOCALE] = next.activeLocale
        mutable[Keys.SHOW_NUMBER_ROW] = next.showNumberRow
        mutable[Keys.SHOW_GERMAN_UMLAUTS] = next.showGermanUmlautKeys
        mutable[Keys.SHOW_SPACEBAR_LABEL] = next.showSpacebarLanguageLabel
        mutable[Keys.ROUNDED_EXPRESSIVE_KEYS] = next.roundedExpressiveKeys
        mutable[Keys.SHOW_KEY_BORDERS] = next.showKeyBorders
        mutable[Keys.KEYBOARD_HEIGHT] = next.keyboardHeight.name
        mutable[Keys.KEYBOARD_SCALE_PERCENT] = next.keyboardScalePercent.coerceIn(75, 135)
        mutable[Keys.KEYBOARD_BOTTOM_LIFT_DP] = next.keyboardBottomLiftDp.coerceIn(0, 48)
        mutable[Keys.LONG_PRESS_DELAY_MS] = next.longPressDelayMs.coerceIn(150, 500)
        mutable[Keys.ONE_HANDED_MODE] = next.oneHandedMode.name
        mutable[Keys.HAPTIC_FEEDBACK] = next.hapticFeedback
        mutable[Keys.SOUND_ON_KEYPRESS] = next.soundOnKeypress
        mutable[Keys.THEME_MODE] = next.themeMode.name
        mutable[Keys.DARK_MODE] = next.darkMode.name
        mutable[Keys.APPLE_MODE_ENABLED] = next.appleModeEnabled
        mutable[Keys.AUTO_CORRECT] = next.autoCorrect
        mutable[Keys.SPELL_GRAMMAR_CHIPS] = next.spellGrammarChips
        mutable[Keys.PROACTIVE_UNDO] = next.proactiveUndo
        mutable[Keys.DOUBLE_SPACE_PERIOD] = next.doubleSpacePeriod
        mutable[Keys.LEARNING_ENABLED] = next.learningEnabled
        mutable[Keys.INCOGNITO_MODE] = next.incognitoMode
        mutable[Keys.INLINE_MATH_ENABLED] = next.inlineMathEnabled
        mutable[Keys.SWIPE_BACKSPACE_WORD] = next.swipeBackspaceDeleteWord
        mutable[Keys.SHOW_WRITING_TOOLS_ICON] = next.showWritingToolsIcon
        mutable[Keys.SHOW_CLIPBOARD_ICON] = next.showClipboardIcon
        mutable[Keys.SHOW_SCAN_TEXT_ICON] = next.showScanTextIcon
        mutable[Keys.VOICE_MODE] = next.voiceMode.name
        mutable[Keys.VOICE_REMOVE_FILLERS] = next.voiceRemoveFillers
        mutable[Keys.VOICE_AUTO_PUNCTUATE] = next.voiceAutoPunctuate
        mutable[Keys.VOICE_STRICT_LANGUAGE_LOCK] = next.voiceStrictLanguageLock
        mutable[Keys.VOICE_MINIMIZE_BUTTON] = next.voiceMinimizeButton
        mutable[Keys.VOICE_ROTATION_GRACE_MS] = next.voiceRotationGraceMs.coerceIn(500L, 4000L)
        mutable[Keys.VOICE_AI_POLISH] = next.voiceAiPolish
        mutable[Keys.VOICE_ONBOARDING_SEEN] = next.voiceOnboardingSeen
        mutable[Keys.AI_WRITING_TOOLS_ENABLED] = next.aiWritingToolsEnabled
        mutable[Keys.AI_SUPPORT_ALL_KEYBOARDS] = next.aiSupportAllKeyboards
        mutable[Keys.AI_PROVIDER] = next.aiProvider.name
        mutable[Keys.AI_MODEL] = next.aiModel
        mutable[Keys.AI_CUSTOM_ENDPOINT] = next.aiCustomEndpoint
        mutable[Keys.AI_ONBOARDING_SEEN] = next.aiOnboardingSeen
        mutable[Keys.CLIPBOARD_ENABLED] = next.clipboardEnabled
        mutable[Keys.CLIPBOARD_RETENTION] = next.clipboardRetention.name
        mutable[Keys.CLIPBOARD_MAX_ITEMS] = next.clipboardMaxItems.coerceIn(20, 200)
        mutable[Keys.SCREENSHOT_SUGGESTION_CHIP] = next.screenshotSuggestionChip
        mutable[Keys.EMOJI_SCALE] = next.emojiScale.coerceIn(20, 34)
        mutable[Keys.CANDIDATE_EMOJI_SUGGESTIONS] = next.candidateEmojiSuggestions
    }

    companion object {
        fun isUserUnlocked(context: Context): Boolean {
            val um = context.getSystemService(UserManager::class.java)
            return um?.isUserUnlocked ?: true
        }

        private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T {
            if (raw.isNullOrBlank()) return default
            return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
        }
    }
}

package com.tapwing.keyboard.settings

import androidx.annotation.StringRes
import com.tapwing.keyboard.R

/**
 * Gboard / Pixel Material 3 Expressive top-level settings sections.
 * Ordered so Gboard and Pixel users immediately find every menu in its familiar place:
 * 1. Keyboard (Languages, Preferences, Theme)
 * 2. Typing & input (Text correction, Glide typing, Voice typing, Dictionary)
 * 3. Content & toolbar (Clipboard, Emojis, Quick Bar)
 * 4. AI & smart services (Writing tools & AI, API keys, Quick search, Passwords & autofill)
 * 5. Insights & system (Typing statistics, About Tapwing)
 */
internal enum class SettingsSection(
    @get:StringRes val titleRes: Int,
    val routes: List<SettingsRoute>
) {
    BASICS(
        titleRes = R.string.settings_section_basics,
        routes = listOf(
            SettingsRoute.LANGUAGES,
            SettingsRoute.PREFERENCES,
            SettingsRoute.APPEARANCE
        )
    ),
    TYPING(
        titleRes = R.string.settings_section_typing,
        routes = listOf(
            SettingsRoute.CORRECTIONS,
            SettingsRoute.GLIDE_TYPING,
            SettingsRoute.VOICE,
            SettingsRoute.DICTIONARY
        )
    ),
    CONTENT(
        titleRes = R.string.settings_section_content,
        routes = listOf(
            SettingsRoute.CLIPBOARD,
            SettingsRoute.EMOJI,
            SettingsRoute.QUICK_BAR
        )
    ),
    SMART_AI(
        titleRes = R.string.settings_section_ai,
        routes = listOf(
            SettingsRoute.WRITING_TOOLS,
            SettingsRoute.API_KEYS,
            SettingsRoute.QUICK_SEARCH,
            SettingsRoute.AUTOFILL
        )
    ),
    SYSTEM(
        titleRes = R.string.settings_section_system,
        routes = listOf(
            SettingsRoute.STATS,
            SettingsRoute.PRIVACY,
            SettingsRoute.LICENSES,
            SettingsRoute.ABOUT
        )
    );

    companion object {
        fun forRoute(route: SettingsRoute): SettingsSection? =
            entries.firstOrNull { route in it.routes }
    }
}

/**
 * Centralized bilingual search index for Tapwing's Gboard/Pixel-style Settings.
 * Supports multi-token queries across both localized route labels and sub-setting keywords.
 */
internal object SettingsCatalog {

    val allRoutes: List<SettingsRoute> = SettingsSection.entries.flatMap { it.routes }

    private val QUERY_SPLIT_REGEX = Regex("\\s+")

    private val keywordsByRoute: Map<SettingsRoute, String> = mapOf(
        SettingsRoute.LANGUAGES to "languages sprachen sprache qwertz qwerty azerty layout multilingual zweisprachig deutsch english swiss schweiz",
        SettingsRoute.PREFERENCES to "preferences einstellungen keyboard tastatur layout height höhe size größe offset abstand bottom haptic haptik vibration sound ton klick long press haltedauer one handed einhand split geteilt tablet foldable enter newline zeile barrierefreiheit accessibility kontrast contrast large labels große tasten",
        SettingsRoute.APPEARANCE to "theme design farbschema color farbe dark dunkel light hell system amoled slate cobalt schiefer kobalt lavender material you borders umrandung pill capsule tasten glass mode glas kontrast contrast barrierefreiheit accessibility",
        SettingsRoute.CORRECTIONS to "text correction textkorrektur autocorrect autokorrektur undo rückgängig backspace double space doppeltipp punkt period grammar grammatik spell rechtschreibung chips math taschenrechner learning lernen incognito inkognito",
        SettingsRoute.GLIDE_TYPING to "glide typing swipe wischen wisch tippen gesture geste delete word wort löschen cursor trackpad spacebar leertaste verschieben",
        SettingsRoute.VOICE to "voice typing spracheingabe diktat dictation speech mikrofon mic whisper sherpa nvidia nemotron fastconformer parakeet tdt kroko canary natural cleanup streaming live local offline android cloud gemini audio multilingual mehrsprachig stichpunkte bullet lists",
        SettingsRoute.DICTIONARY to "dictionary wörterbuch personal persönlich words wörter learn lernen adaptive per app pack paket offline 50k import export zip",
        SettingsRoute.CLIPBOARD to "clipboard zwischenablage copy kopieren paste einfügen history verlauf retention aufbewahrung screenshot ocr scan kamera camera",
        SettingsRoute.EMOJI to "emoji emojis smileys stickers scale größe suggestions vorschläge",
        SettingsRoute.QUICK_BAR to "quick bar schnellleiste toolbar leiste shortcuts kurzbefehle order reihenfolge customize anpassen",
        SettingsRoute.WRITING_TOOLS to "writing tools schreibhilfe ai ki proofread korrigieren rewrite umformulieren summarize zusammenfassen bullet stichpunkte translate übersetzen tone tonfall local model llm gguf lfm qwen gemma",
        SettingsRoute.API_KEYS to "api keys schlüssel vault tresor provider anbieter openai groq gemini grok openrouter anthropic claude",
        SettingsRoute.QUICK_SEARCH to "quick search schnellsuche web browser google duckduckgo bing startpage brave ecosia",
        SettingsRoute.AUTOFILL to "autofill passwords passwörter bitwarden login anmelden credentials 2fa otp inline profil profile vorname nachname name first last telefon telefonnummer handy phone email mail adresse address strasse street plz zip stadt city geburtsdatum birthday alter age firma company website smart fill",
        SettingsRoute.STATS to "statistics statistik stats wpm speed tempo words wörter saved gespart keystrokes",
        SettingsRoute.PRIVACY to "privacy datenschutz consent einwilligung cloud revoke widerruf incognito inkognito direct boot storage speicher local lokal",
        SettingsRoute.LICENSES to "licenses lizenzen open source oss modelle models third party drittanbieter apache mit cc-by attribution",
        SettingsRoute.ABOUT to "about über tapwing version license lizenz privacy datenschutz local lokal system enable aktivieren"
    )

    fun keywordsFor(route: SettingsRoute): String = keywordsByRoute[route].orEmpty()

    fun search(
        query: String,
        labelProvider: (SettingsRoute) -> String
    ): List<SettingsRoute> {
        val tokens = query.trim().lowercase().split(QUERY_SPLIT_REGEX).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return allRoutes
        return allRoutes.filter { route ->
            val haystack = "${labelProvider(route).lowercase()} ${keywordsFor(route)}"
            tokens.all { token -> haystack.contains(token) }
        }
    }
}

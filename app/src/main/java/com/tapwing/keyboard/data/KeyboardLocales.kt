package com.tapwing.keyboard.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

data class KeyboardLocaleSpec(
    val code: String,
    val baseLanguage: String,
    val bcp47Tag: String,
    val nativeLabel: String,
    val layoutLabel: String,
    val currencySymbol: String,
    val datePattern: String,
    val usesCommaDecimal: Boolean
)

data class AppUiLanguageSpec(
    val code: String,
    val nativeLabel: String
)

object KeyboardLocales {

    val allTypingLocales: List<KeyboardLocaleSpec> = listOf(
        KeyboardLocaleSpec(
            code = "de",
            baseLanguage = "de",
            bcp47Tag = "de-DE",
            nativeLabel = "Deutsch (DE/AT)",
            layoutLabel = "QWERTZ",
            currencySymbol = "€",
            datePattern = "dd.MM.yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "de_CH",
            baseLanguage = "de",
            bcp47Tag = "de-CH",
            nativeLabel = "Deutsch (Schweiz)",
            layoutLabel = "QWERTZ CH",
            currencySymbol = "CHF",
            datePattern = "dd.MM.yyyy",
            usesCommaDecimal = false
        ),
        KeyboardLocaleSpec(
            code = "en",
            baseLanguage = "en",
            bcp47Tag = "en-US",
            nativeLabel = "English (US)",
            layoutLabel = "QWERTY",
            currencySymbol = "$",
            datePattern = "yyyy-MM-dd",
            usesCommaDecimal = false
        ),
        KeyboardLocaleSpec(
            code = "en_GB",
            baseLanguage = "en",
            bcp47Tag = "en-GB",
            nativeLabel = "English (UK)",
            layoutLabel = "QWERTY UK",
            currencySymbol = "£",
            datePattern = "dd/MM/yyyy",
            usesCommaDecimal = false
        ),
        KeyboardLocaleSpec(
            code = "fr",
            baseLanguage = "fr",
            bcp47Tag = "fr-FR",
            nativeLabel = "Français",
            layoutLabel = "AZERTY",
            currencySymbol = "€",
            datePattern = "dd/MM/yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "es",
            baseLanguage = "es",
            bcp47Tag = "es-ES",
            nativeLabel = "Español",
            layoutLabel = "QWERTY",
            currencySymbol = "€",
            datePattern = "dd/MM/yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "it",
            baseLanguage = "it",
            bcp47Tag = "it-IT",
            nativeLabel = "Italiano",
            layoutLabel = "QWERTY",
            currencySymbol = "€",
            datePattern = "dd/MM/yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "pt",
            baseLanguage = "pt",
            bcp47Tag = "pt-PT",
            nativeLabel = "Português",
            layoutLabel = "QWERTY",
            currencySymbol = "€",
            datePattern = "dd/MM/yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "nl",
            baseLanguage = "nl",
            bcp47Tag = "nl-NL",
            nativeLabel = "Nederlands",
            layoutLabel = "QWERTY",
            currencySymbol = "€",
            datePattern = "dd-MM-yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "pl",
            baseLanguage = "pl",
            bcp47Tag = "pl-PL",
            nativeLabel = "Polski",
            layoutLabel = "QWERTY",
            currencySymbol = "zł",
            datePattern = "dd.MM.yyyy",
            usesCommaDecimal = true
        ),
        KeyboardLocaleSpec(
            code = "tr",
            baseLanguage = "tr",
            bcp47Tag = "tr-TR",
            nativeLabel = "Türkçe",
            layoutLabel = "Türkçe Q",
            currencySymbol = "₺",
            datePattern = "dd.MM.yyyy",
            usesCommaDecimal = true
        )
    )

    val appUiLanguages: List<AppUiLanguageSpec> = listOf(
        AppUiLanguageSpec("system", "System"),
        AppUiLanguageSpec("de", "Deutsch"),
        AppUiLanguageSpec("en", "English"),
        AppUiLanguageSpec("fr", "Français"),
        AppUiLanguageSpec("es", "Español"),
        AppUiLanguageSpec("it", "Italiano"),
        AppUiLanguageSpec("pt", "Português"),
        AppUiLanguageSpec("nl", "Nederlands"),
        AppUiLanguageSpec("pl", "Polski"),
        AppUiLanguageSpec("tr", "Türkçe")
    )

    private val byCode = allTypingLocales.associateBy { it.code }

    fun specFor(code: String): KeyboardLocaleSpec {
        byCode[code]?.let { return it }
        val base = baseLang(code)
        return allTypingLocales.firstOrNull { it.baseLanguage == base } ?: allTypingLocales.first()
    }

    fun baseLang(code: String): String {
        val clean = code.trim().lowercase().replace('-', '_')
        return clean.substringBefore('_').ifEmpty { "en" }
    }

    fun nextEnabledLocale(current: String, enabled: List<String>): String {
        val pool = enabled.filter { byCode.containsKey(it) }.ifEmpty { listOf("de", "en") }
        val idx = pool.indexOf(current)
        return if (idx == -1) pool.first() else pool[(idx + 1) % pool.size]
    }

    fun parseEnabledLocales(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return listOf("de", "en")
        val parsed = raw.split(',').map { it.trim() }.filter { byCode.containsKey(it) }.distinct()
        return parsed.ifEmpty { listOf("de", "en") }
    }

    fun serializeEnabledLocales(list: List<String>): String {
        return list.filter { byCode.containsKey(it) }.distinct().ifEmpty { listOf("de", "en") }.joinToString(",")
    }

    fun systemLanguageTag(): String {
        val loc = Locale.getDefault()
        return loc.toLanguageTag().ifBlank { "en-US" }
    }

    fun systemLanguageDisplayName(): String {
        val loc = Locale.getDefault()
        val eng = loc.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
        val native = loc.getDisplayLanguage(loc).ifBlank { eng }
        return if (eng.equals(native, ignoreCase = true)) eng else "$native ($eng)"
    }

    fun localizedContext(base: Context, uiLocalePref: String): Context {
        if (uiLocalePref == "system" || uiLocalePref.isBlank()) return base
        val locale = Locale.forLanguageTag(uiLocalePref.replace('_', '-'))
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

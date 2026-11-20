package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TextSnippet(
    val id: String,
    val trigger: String,
    val title: String,
    val body: String
)

/**
 * Resolves multi-line snippet templates with dynamic placeholders:
 * {DATUM}/{DATE}, {ZEIT}/{TIME}, {WOCHE}/{WEEK}, {MONAT}/{MONTH}, {JAHR}/{YEAR},
 * {WOCHENTAG}/{WEEKDAY}, {ZWISCHENABLAGE}/{CLIPBOARD}, {NAME}, {EMAIL}.
 */
object SnippetPlaceholderResolver {

    fun resolve(
        template: String,
        locale: String,
        latestClipboardText: String = "",
        userName: String = "",
        userEmail: String = "",
        now: Date = Date()
    ): String {
        val spec = KeyboardLocales.specFor(locale)
        val javaLocale = Locale.forLanguageTag(spec.bcp47Tag)
        val cal = Calendar.getInstance().apply { time = now }

        val dateStr = SimpleDateFormat(spec.datePattern, javaLocale).format(now)
        val timeStr = SimpleDateFormat("HH:mm", javaLocale).format(now)
        val weekStr = if (spec.baseLanguage == "de") "KW ${cal.get(Calendar.WEEK_OF_YEAR)}" else "Week ${cal.get(Calendar.WEEK_OF_YEAR)}"
        val monthStr = SimpleDateFormat("MMMM", javaLocale).format(now)
        val yearStr = SimpleDateFormat("yyyy", javaLocale).format(now)
        val weekdayStr = SimpleDateFormat("EEEE", javaLocale).format(now)

        val replacements = listOf(
            Regex("""\{(?:DATUM|DATE)\}""", RegexOption.IGNORE_CASE) to dateStr,
            Regex("""\{(?:ZEIT|TIME)\}""", RegexOption.IGNORE_CASE) to timeStr,
            Regex("""\{(?:WOCHE|WEEK)\}""", RegexOption.IGNORE_CASE) to weekStr,
            Regex("""\{(?:MONAT|MONTH)\}""", RegexOption.IGNORE_CASE) to monthStr,
            Regex("""\{(?:JAHR|YEAR)\}""", RegexOption.IGNORE_CASE) to yearStr,
            Regex("""\{(?:WOCHENTAG|WEEKDAY)\}""", RegexOption.IGNORE_CASE) to weekdayStr,
            Regex("""\{(?:ZWISCHENABLAGE|CLIPBOARD)\}""", RegexOption.IGNORE_CASE) to latestClipboardText.trim(),
            Regex("""\{NAME\}""", RegexOption.IGNORE_CASE) to userName.trim(),
            Regex("""\{EMAIL\}""", RegexOption.IGNORE_CASE) to userEmail.trim()
        )

        var out = template
        for ((regex, value) in replacements) {
            out = out.replace(regex, value)
        }
        return out
    }
}

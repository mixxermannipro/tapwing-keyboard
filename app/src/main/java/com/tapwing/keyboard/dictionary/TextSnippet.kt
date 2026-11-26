package com.tapwing.keyboard.dictionary

import com.tapwing.keyboard.data.KeyboardLocales
import com.tapwing.keyboard.data.PersonalProfile
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
 * {WOCHENTAG}/{WEEKDAY}, {ZWISCHENABLAGE}/{CLIPBOARD}, {NAME}, {VORNAME}/{FIRSTNAME},
 * {NACHNAME}/{LASTNAME}, {EMAIL}, {TELEFON}/{PHONE}, {ADRESSE}/{ADDRESS},
 * {PLZ}/{ZIP}, {STADT}/{CITY}, {GEBURTSDATUM}/{BIRTHDATE}, {ALTER}/{AGE}.
 */
object SnippetPlaceholderResolver {

    private val DATE_REGEX = Regex("""\{(?:DATUM|DATE)\}""", RegexOption.IGNORE_CASE)
    private val TIME_REGEX = Regex("""\{(?:ZEIT|TIME)\}""", RegexOption.IGNORE_CASE)
    private val WEEK_REGEX = Regex("""\{(?:WOCHE|WEEK)\}""", RegexOption.IGNORE_CASE)
    private val MONTH_REGEX = Regex("""\{(?:MONAT|MONTH)\}""", RegexOption.IGNORE_CASE)
    private val YEAR_REGEX = Regex("""\{(?:JAHR|YEAR)\}""", RegexOption.IGNORE_CASE)
    private val WEEKDAY_REGEX = Regex("""\{(?:WOCHENTAG|WEEKDAY)\}""", RegexOption.IGNORE_CASE)
    private val CLIPBOARD_REGEX = Regex("""\{(?:ZWISCHENABLAGE|CLIPBOARD)\}""", RegexOption.IGNORE_CASE)
    private val NAME_REGEX = Regex("""\{NAME\}""", RegexOption.IGNORE_CASE)
    private val FIRST_NAME_REGEX = Regex("""\{(?:VORNAME|FIRSTNAME)\}""", RegexOption.IGNORE_CASE)
    private val LAST_NAME_REGEX = Regex("""\{(?:NACHNAME|LASTNAME)\}""", RegexOption.IGNORE_CASE)
    private val EMAIL_REGEX = Regex("""\{EMAIL\}""", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("""\{(?:TELEFON|PHONE)\}""", RegexOption.IGNORE_CASE)
    private val ADDRESS_REGEX = Regex("""\{(?:ADRESSE|ADDRESS)\}""", RegexOption.IGNORE_CASE)
    private val POSTAL_REGEX = Regex("""\{(?:PLZ|ZIP)\}""", RegexOption.IGNORE_CASE)
    private val CITY_REGEX = Regex("""\{(?:STADT|ORT|CITY)\}""", RegexOption.IGNORE_CASE)
    private val BIRTHDATE_REGEX = Regex("""\{(?:GEBURTSDATUM|BIRTHDATE)\}""", RegexOption.IGNORE_CASE)
    private val AGE_REGEX = Regex("""\{(?:ALTER|AGE)\}""", RegexOption.IGNORE_CASE)

    fun resolve(
        template: String,
        locale: String,
        latestClipboardText: String = "",
        userName: String = "",
        userEmail: String = "",
        now: Date = Date(),
        profile: PersonalProfile? = null
    ): String {
        if ('{' !in template) return template
        val spec = KeyboardLocales.specFor(locale)
        val javaLocale = Locale.forLanguageTag(spec.bcp47Tag)
        val cal = Calendar.getInstance().apply { time = now }

        val dateStr = SimpleDateFormat(spec.datePattern, javaLocale).format(now)
        val timeStr = SimpleDateFormat("HH:mm", javaLocale).format(now)
        val weekStr = if (spec.baseLanguage == "de") "KW ${cal.get(Calendar.WEEK_OF_YEAR)}" else "Week ${cal.get(Calendar.WEEK_OF_YEAR)}"
        val monthStr = SimpleDateFormat("MMMM", javaLocale).format(now)
        val yearStr = SimpleDateFormat("yyyy", javaLocale).format(now)
        val weekdayStr = SimpleDateFormat("EEEE", javaLocale).format(now)

        val effectiveFullName = userName.trim().ifEmpty { profile?.fullName.orEmpty() }
        val effectiveEmail = userEmail.trim().ifEmpty { profile?.primaryEmail.orEmpty() }
        val effectiveFirst = profile?.firstName?.trim().orEmpty().ifEmpty { effectiveFullName.substringBefore(' ') }
        val effectiveLast = profile?.lastName?.trim().orEmpty().ifEmpty {
            effectiveFullName.substringAfter(' ', "")
        }
        val effectivePhone = profile?.primaryPhone.orEmpty()
        val effectiveAddress = profile?.fullAddress.orEmpty()
        val effectivePostal = profile?.postalCode?.trim().orEmpty()
        val effectiveCity = profile?.city?.trim().orEmpty()
        val effectiveBirthDate = profile?.formattedBirthDate(locale).orEmpty()
        val effectiveAge = profile?.computedAgeYears(cal)?.toString().orEmpty()

        var out = template
        out = DATE_REGEX.replace(out) { dateStr }
        out = TIME_REGEX.replace(out) { timeStr }
        out = WEEK_REGEX.replace(out) { weekStr }
        out = MONTH_REGEX.replace(out) { monthStr }
        out = YEAR_REGEX.replace(out) { yearStr }
        out = WEEKDAY_REGEX.replace(out) { weekdayStr }
        out = CLIPBOARD_REGEX.replace(out) { latestClipboardText.trim() }
        out = NAME_REGEX.replace(out) { effectiveFullName }
        out = FIRST_NAME_REGEX.replace(out) { effectiveFirst }
        out = LAST_NAME_REGEX.replace(out) { effectiveLast }
        out = EMAIL_REGEX.replace(out) { effectiveEmail }
        out = PHONE_REGEX.replace(out) { effectivePhone }
        out = ADDRESS_REGEX.replace(out) { effectiveAddress }
        out = POSTAL_REGEX.replace(out) { effectivePostal }
        out = CITY_REGEX.replace(out) { effectiveCity }
        out = BIRTHDATE_REGEX.replace(out) { effectiveBirthDate }
        out = AGE_REGEX.replace(out) { effectiveAge }
        return out
    }
}

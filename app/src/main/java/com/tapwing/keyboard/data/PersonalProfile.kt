package com.tapwing.keyboard.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

enum class SmartFillKind {
    NAME,
    EMAIL,
    PHONE,
    ADDRESS,
    BIRTHDATE,
    AGE,
    COMPANY,
    WEBSITE
}

enum class SmartFieldType {
    NONE,
    FIRST_NAME,
    LAST_NAME,
    FULL_NAME,
    EMAIL,
    PHONE,
    STREET_ADDRESS,
    POSTAL_CODE,
    CITY,
    COUNTRY,
    BIRTHDATE,
    AGE,
    COMPANY,
    WEBSITE;

    fun fillKind(): SmartFillKind? = when (this) {
        FIRST_NAME, LAST_NAME, FULL_NAME -> SmartFillKind.NAME
        EMAIL -> SmartFillKind.EMAIL
        PHONE -> SmartFillKind.PHONE
        STREET_ADDRESS, POSTAL_CODE, CITY, COUNTRY -> SmartFillKind.ADDRESS
        BIRTHDATE -> SmartFillKind.BIRTHDATE
        AGE -> SmartFillKind.AGE
        COMPANY -> SmartFillKind.COMPANY
        WEBSITE -> SmartFillKind.WEBSITE
        NONE -> null
    }

    fun prefersLeadingCapitalization(): Boolean = when (this) {
        FIRST_NAME, LAST_NAME, FULL_NAME, STREET_ADDRESS, CITY, COUNTRY, COMPANY -> true
        else -> false
    }
}

/**
 * Privacy-first, 100% on-device Personal Smart-Fill Profile.
 * Supports multiple email addresses and phone numbers, full address components,
 * and dynamic real-time age computation from birthDate (never goes stale on birthdays).
 */
data class PersonalProfile(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
    val country: String = "",
    val company: String = "",
    val website: String = ""
) {
    val fullName: String
        get() = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    val primaryEmail: String
        get() = emails.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    val primaryPhone: String
        get() = phones.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    val fullAddress: String
        get() {
            val streetPart = street.trim()
            val cityLine = listOf(postalCode.trim(), city.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            val countryPart = country.trim()
            return listOf(streetPart, cityLine, countryPart).filter { it.isNotEmpty() }.joinToString(", ")
        }

    val isEmpty: Boolean
        get() = firstName.isBlank() &&
            lastName.isBlank() &&
            birthDate.isBlank() &&
            emails.none { it.isNotBlank() } &&
            phones.none { it.isNotBlank() } &&
            street.isBlank() &&
            postalCode.isBlank() &&
            city.isBlank() &&
            country.isBlank() &&
            company.isBlank() &&
            website.isBlank()

    val filledFieldCount: Int
        get() = listOf(
            firstName.isNotBlank(),
            lastName.isNotBlank(),
            birthDate.isNotBlank(),
            emails.any { it.isNotBlank() },
            phones.any { it.isNotBlank() },
            street.isNotBlank(),
            postalCode.isNotBlank(),
            city.isNotBlank(),
            company.isNotBlank(),
            website.isNotBlank()
        ).count { it }

    /**
     * Parses [birthDate] in common formats (dd.MM.yyyy, yyyy-MM-dd, dd/MM/yyyy)
     * and returns (year, month 1..12, day 1..31), or null if unparseable.
     */
    fun parsedBirthDate(): Triple<Int, Int, Int>? = parseDateParts(birthDate)

    /**
     * Dynamically calculates the user's exact current age in years from [birthDate].
     * Automatically increments on the user's birthday without manual updates.
     */
    fun computedAgeYears(now: Calendar = Calendar.getInstance()): Int? {
        val (year, month, day) = parsedBirthDate() ?: return null
        val curYear = now.get(Calendar.YEAR)
        val curMonth = now.get(Calendar.MONTH) + 1
        val curDay = now.get(Calendar.DAY_OF_MONTH)
        var age = curYear - year
        if (curMonth < month || (curMonth == month && curDay < day)) {
            age--
        }
        return age.takeIf { it in 1..125 }
    }

    /**
     * Formats [birthDate] cleanly according to the active locale while falling back to the raw value.
     */
    fun formattedBirthDate(locale: String): String {
        val clean = birthDate.trim()
        if (clean.isEmpty()) return ""
        val (year, month, day) = parsedBirthDate() ?: return clean
        val base = KeyboardLocales.baseLang(locale)
        return when (base) {
            "de" -> String.format(Locale.ROOT, "%02d.%02d.%04d", day, month, year)
            "en" -> if (locale == "en_GB") {
                String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year)
            } else {
                String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
            }
            else -> String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year)
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("firstName", firstName.trim())
        obj.put("lastName", lastName.trim())
        obj.put("birthDate", birthDate.trim())
        obj.put("emails", JSONArray().apply {
            emails.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { put(it) }
        })
        obj.put("phones", JSONArray().apply {
            phones.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { put(it) }
        })
        obj.put("street", street.trim())
        obj.put("postalCode", postalCode.trim())
        obj.put("city", city.trim())
        obj.put("country", country.trim())
        obj.put("company", company.trim())
        obj.put("website", website.trim())
        return obj.toString()
    }

    companion object {
        private val ISO_DATE_REGEX = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""")
        private val DMY_DATE_REGEX = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$""")

        internal fun parseDateParts(raw: String): Triple<Int, Int, Int>? {
            val clean = raw.trim()
            if (clean.isEmpty()) return null
            ISO_DATE_REGEX.matchEntire(clean)?.let { m ->
                val y = m.groupValues[1].toIntOrNull() ?: return null
                val mo = m.groupValues[2].toIntOrNull() ?: return null
                val d = m.groupValues[3].toIntOrNull() ?: return null
                if (y in 1900..2100 && mo in 1..12 && d in 1..31) return Triple(y, mo, d)
            }
            DMY_DATE_REGEX.matchEntire(clean)?.let { m ->
                val d = m.groupValues[1].toIntOrNull() ?: return null
                val mo = m.groupValues[2].toIntOrNull() ?: return null
                val y = m.groupValues[3].toIntOrNull() ?: return null
                if (y in 1900..2100 && mo in 1..12 && d in 1..31) return Triple(y, mo, d)
            }
            return null
        }

        fun fromJson(
            rawJson: String?,
            legacyUserName: String = "",
            legacyUserEmail: String = ""
        ): PersonalProfile {
            val parsed = if (!rawJson.isNullOrBlank()) {
                runCatching {
                    val obj = JSONObject(rawJson)
                    val emailList = buildList {
                        val arr = obj.optJSONArray("emails")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val v = arr.optString(i, "").trim()
                                if (v.isNotEmpty()) add(v)
                            }
                        }
                    }
                    val phoneList = buildList {
                        val arr = obj.optJSONArray("phones")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val v = arr.optString(i, "").trim()
                                if (v.isNotEmpty()) add(v)
                            }
                        }
                    }
                    PersonalProfile(
                        firstName = obj.optString("firstName", "").trim(),
                        lastName = obj.optString("lastName", "").trim(),
                        birthDate = obj.optString("birthDate", "").trim(),
                        emails = emailList,
                        phones = phoneList,
                        street = obj.optString("street", "").trim(),
                        postalCode = obj.optString("postalCode", "").trim(),
                        city = obj.optString("city", "").trim(),
                        country = obj.optString("country", "").trim(),
                        company = obj.optString("company", "").trim(),
                        website = obj.optString("website", "").trim()
                    )
                }.getOrNull()
            } else null

            val base = parsed ?: PersonalProfile()
            // Seamless compatibility with legacy snippetUserName & snippetUserEmail
            var first = base.firstName
            var last = base.lastName
            if (first.isBlank() && last.isBlank() && legacyUserName.isNotBlank()) {
                val parts = legacyUserName.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    first = parts.first()
                    last = parts.drop(1).joinToString(" ")
                } else if (parts.size == 1) {
                    first = parts.first()
                }
            }
            val emails = if (base.emails.isEmpty() && legacyUserEmail.isNotBlank()) {
                listOf(legacyUserEmail.trim())
            } else {
                base.emails
            }
            return base.copy(firstName = first, lastName = last, emails = emails)
        }
    }
}

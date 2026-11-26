package com.tapwing.keyboard.ime

import android.text.InputType
import com.tapwing.keyboard.data.SmartFieldType

/**
 * Multi-signal semantic field & conversational intent detector for Tapwing Smart Fill.
 * Recognizes First Name, Last Name, Full Name, Email, Phone, Address, Postal Code, City,
 * Country, Birthdate, Age, Company, and Website across 9 languages — both from Android
 * form metadata AND from natural conversational prompts in free-text fields.
 */
object SmartFieldDetector {

    private val CAMEL_CASE_REGEX = Regex("([a-z])([A-Z])")
    private val SEPARATORS_REGEX = Regex("[^\\p{L}\\p{N}]+")

    private val FIRST_NAME_HINTS = setOf(
        "vorname", "vornamen", "first name", "firstname", "given name", "givenname", "fname",
        "forename", "prenom", "prénom", "nombre", "primer nombre", "nome", "voornaam",
        "imie", "imię", "isim", "ad", "adınız", "rufname"
    )
    private val LAST_NAME_HINTS = setOf(
        "nachname", "nachnamen", "familienname", "zuname", "last name", "lastname",
        "family name", "familyname", "surname", "lname", "nom de famille", "apellido",
        "apellidos", "cognome", "sobrenome", "achternaam", "nazwisko", "soyad", "soyadınız"
    )
    private val FULL_NAME_HINTS = setOf(
        "name", "vollständiger name", "vollstaendiger name", "voller name", "vor und nachname",
        "vor- und nachname", "ihr name", "dein name", "full name", "fullname", "your name",
        "contact name", "recipient", "empfänger", "empfaenger", "absender", "sender",
        "nom complet", "nombre completo", "nome completo", "volledige naam", "imię i nazwisko"
    )
    private val EXCLUDED_NAME_MODIFIERS = setOf(
        "user", "benutzer", "nutzer", "login", "account", "file", "datei", "domain", "host",
        "server", "company", "firma", "product", "produkt", "project", "projekt", "search",
        "suche", "card", "karte", "bank", "wifi", "wlan", "device", "gerät", "geraet", "nick", "spitz"
    )
    private val EMAIL_HINTS = setOf(
        "email", "e mail", "email address", "email adresse", "e mail address", "e mail adresse",
        "mailadresse", "mail adresse", "mail", "correo", "correo electronico", "adresse e mail",
        "e mail oder benutzername", "email or username", "e posta"
    )
    private val PHONE_HINTS = setOf(
        "phone", "phone number", "phonenumber", "mobile", "mobile number", "cell", "cell phone",
        "telephone", "tel", "telefon", "telefonnummer", "handy", "handynummer", "mobil",
        "mobilnummer", "rufnummer", "kontaktnummer", "nummer", "telefono", "teléfono",
        "téléphone", "numero de telephone", "telefoon", "telefoonnummer", "numer telefonu", "telefon numarası"
    )
    private val STREET_HINTS = setOf(
        "street", "street address", "address", "address line 1", "address 1", "billing address",
        "shipping address", "delivery address", "straße", "strasse", "straße und hausnummer",
        "strasse und hausnummer", "straße hausnummer", "strasse hausnummer", "anschrift",
        "adresse", "lieferadresse", "rechnungsadresse", "wohnadresse", "rue", "adresse postale",
        "calle", "direccion", "dirección", "indirizzo", "via", "straat", "adres", "ulica"
    )
    private val POSTAL_CODE_HINTS = setOf(
        "plz", "postleitzahl", "zip", "zip code", "zipcode", "postal", "postal code",
        "postalcode", "postcode", "code postal", "codigo postal", "código postal", "cap",
        "kod pocztowy", "posta kodu"
    )
    private val CITY_HINTS = setOf(
        "stadt", "ort", "wohnort", "city", "town", "locality", "ville", "ciudad",
        "citta", "città", "cidade", "plaats", "miasto", "şehir", "sehir"
    )
    private val COUNTRY_HINTS = setOf(
        "land", "country", "nation", "pays", "pais", "país", "paese", "kraj", "ülke", "ulke"
    )
    private val BIRTHDATE_HINTS = setOf(
        "geburtsdatum", "geburtstag", "geboren am", "date of birth", "birthdate", "birth date",
        "birthday", "dob", "date de naissance", "fecha de nacimiento", "data di nascita",
        "geboortedatum", "data urodzenia", "doğum tarihi"
    )
    private val AGE_HINTS = setOf(
        "alter", "dein alter", "ihr alter", "age", "your age", "âge", "edad", "età", "leeftijd", "wiek", "yaş"
    )
    private val COMPANY_HINTS = setOf(
        "firma", "unternehmen", "arbeitgeber", "organisation", "company", "company name",
        "organization", "employer", "entreprise", "société", "empresa", "azienda", "bedrijf"
    )
    private val WEBSITE_HINTS = setOf(
        "website", "webseite", "homepage", "portfolio", "linkedin", "github", "web address"
    )

    private val EMAIL_VALUE_REGEX = Regex(
        """^[A-Za-z0-9._%+\-]{1,64}@[A-Za-z0-9.\-]{1,253}\.[A-Za-z]{2,24}$"""
    )
    private val PHONE_ALLOWED_REGEX = Regex("""^[+\d][\d\s\-/()]{5,22}$""")
    private val POSTAL_VALUE_REGEX = Regex("""^[A-Za-z0-9][A-Za-z0-9 \-]{2,10}$""")
    private val PERSON_NAME_TOKEN_REGEX = Regex("""^[\p{Lu}][\p{L}'\-]{1,28}(?:\s+[\p{Lu}][\p{L}'\-]{1,28}){0,2}$""")

    private val NON_NAME_COMMON_WORDS = setOf(
        "hallo", "hello", "hey", "danke", "thanks", "bitte", "please", "guten", "morgen",
        "abend", "heute", "morgen", "gestern", "test", "admin", "user", "guest", "none", "null"
    )

    private fun normalizeLabel(value: String?): String = value.orEmpty()
        .replace(CAMEL_CASE_REGEX, "$1 $2")
        .lowercase()
        .replace(SEPARATORS_REGEX, " ")
        .trim()

    fun detectField(
        inputType: Int,
        fieldName: String? = null,
        hint: String? = null,
        label: String? = null,
        autofillHints: Array<String>? = null
    ): SmartFieldType {
        if (inputType == InputType.TYPE_NULL) return SmartFieldType.NONE
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        // Never classify password fields
        if (typeClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
            return SmartFieldType.NONE
        }
        if (typeClass == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            )
        ) {
            return SmartFieldType.NONE
        }

        // 1. Android explicit Autofill hints (highest precision when host app provides them)
        autofillHints?.forEach { rawHint ->
            val h = rawHint.lowercase()
            when {
                h.contains("password") || h.contains("creditcard") || h.contains("otp") -> return SmartFieldType.NONE
                h == "persongivenname" || h.contains("firstname") || h.contains("givenname") -> return SmartFieldType.FIRST_NAME
                h == "personfamilyname" || h.contains("lastname") || h.contains("familyname") -> return SmartFieldType.LAST_NAME
                h == "personname" || h == "name" -> return SmartFieldType.FULL_NAME
                h.contains("email") -> return SmartFieldType.EMAIL
                h.contains("phone") -> return SmartFieldType.PHONE
                h.contains("postalcode") || h.contains("zipcode") -> return SmartFieldType.POSTAL_CODE
                h.contains("addresslocality") || h == "city" -> return SmartFieldType.CITY
                h.contains("addresscountry") || h == "country" -> return SmartFieldType.COUNTRY
                h.contains("streetaddress") || h.contains("postaladdress") -> return SmartFieldType.STREET_ADDRESS
                h.contains("birthdate") || h.contains("birthday") -> return SmartFieldType.BIRTHDATE
            }
        }

        // 2. Explicit InputType signals
        if (typeClass == InputType.TYPE_CLASS_PHONE) return SmartFieldType.PHONE
        if (typeClass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            ) {
                return SmartFieldType.EMAIL
            }
        }

        val candidates = listOfNotNull(
            normalizeLabel(fieldName).takeIf { it.isNotEmpty() },
            normalizeLabel(hint).takeIf { it.isNotEmpty() },
            normalizeLabel(label).takeIf { it.isNotEmpty() }
        )

        // Check normalized metadata against precise bilingual field vocabularies
        for (raw in candidates) {
            val tokens = raw.split(' ')
            if (tokens.size > 5) continue // Skip full-sentence prose hints like "Write an email to the team"

            if (raw in EMAIL_HINTS || (tokens.size <= 3 && tokens.any { it == "email" || it == "mailadresse" })) {
                return SmartFieldType.EMAIL
            }
            if (raw in PHONE_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("telefonnummer", "handynummer", "mobilnummer", "rufnummer", "phonenumber") })) {
                return SmartFieldType.PHONE
            }
            if (raw in FIRST_NAME_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("vorname", "firstname", "givenname", "fname") })) {
                return SmartFieldType.FIRST_NAME
            }
            if (raw in LAST_NAME_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("nachname", "familienname", "lastname", "familyname", "surname", "lname") })) {
                return SmartFieldType.LAST_NAME
            }
            if (raw in POSTAL_CODE_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("plz", "postleitzahl", "zipcode", "postcode") })) {
                return SmartFieldType.POSTAL_CODE
            }
            if (raw in BIRTHDATE_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("geburtsdatum", "geburtstag", "birthdate", "birthday", "dob") })) {
                return SmartFieldType.BIRTHDATE
            }
            if (raw in AGE_HINTS) {
                return SmartFieldType.AGE
            }
            if (raw in STREET_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("straße", "strasse", "anschrift", "hausnummer", "lieferadresse", "rechnungsadresse") })) {
                return SmartFieldType.STREET_ADDRESS
            }
            if (raw in CITY_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("wohnort", "stadt") })) {
                return SmartFieldType.CITY
            }
            if (raw in COUNTRY_HINTS) {
                return SmartFieldType.COUNTRY
            }
            if (raw in COMPANY_HINTS || (tokens.size <= 3 && tokens.any { it in setOf("firma", "unternehmen", "arbeitgeber") })) {
                return SmartFieldType.COMPANY
            }
            if (raw in WEBSITE_HINTS) {
                return SmartFieldType.WEBSITE
            }
            if (raw in FULL_NAME_HINTS && tokens.none { it in EXCLUDED_NAME_MODIFIERS }) {
                return SmartFieldType.FULL_NAME
            }
        }

        // 3. Fallback InputType variations when hint didn't narrow down first/last/street/city
        if (typeClass == InputType.TYPE_CLASS_TEXT) {
            if (variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME) return SmartFieldType.FULL_NAME
            if (variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS) return SmartFieldType.STREET_ADDRESS
        }
        return SmartFieldType.NONE
    }

    data class ConversationalIntent(
        val fieldType: SmartFieldType,
        val partialTyped: String
    )

    private val EMAIL_CUES = listOf(
        "meine e-mail ist", "meine email ist", "meine mail ist", "meine mailadresse ist",
        "meine e-mail-adresse ist", "e-mail-adresse:", "email-adresse:", "e-mail:", "email:",
        "schreib mir an", "schick es an", "schicke es an", "senden an",
        "my email is", "my email address is", "email me at", "send it to", "reach me at", "mail:"
    )
    private val PHONE_CUES = listOf(
        "meine nummer ist", "meine telefonnummer ist", "meine handynummer ist", "meine mobilnummer ist",
        "ruf mich an unter", "erreichst mich unter", "erreichbar unter", "telefonnummer:", "handynummer:",
        "telefon:", "mobil:", "handy:", "tel:", "tel.:",
        "my number is", "my phone is", "my phone number is", "call me at", "text me at", "phone:", "mobile:"
    )
    private val FULL_NAME_CUES = listOf(
        "ich heiße", "ich heisse", "mein name ist", "my name is", "name:"
    )
    private val SIGNATURE_LINE_CUES = listOf(
        "mit freundlichen grüßen", "mit freundlichen gruessen", "freundliche grüße",
        "viele grüße", "viele gruesse", "liebe grüße", "liebe gruesse", "beste grüße",
        "herzliche grüße", "schöne grüße",
        "best regards", "kind regards", "warm regards", "sincerely", "cheers", "thanks", "best"
    )
    private val ADDRESS_CUES = listOf(
        "meine adresse ist", "meine anschrift ist", "adresse:", "anschrift:", "lieferadresse:",
        "my address is", "address:"
    )
    private val CITY_CUES = listOf(
        "ich wohne in", "ich lebe in", "ich komme aus", "wohnort:", "stadt:",
        "i live in", "i'm from", "i am from", "city:"
    )
    private val BIRTHDATE_CUES = listOf(
        "geboren am", "geburtsdatum:", "mein geburtsdatum ist", "geburtstag:",
        "born on", "date of birth:", "birthday:", "dob:"
    )
    private val AGE_CUES = listOf(
        "mein alter ist", "alter:", "my age is", "age:"
    )

    /**
     * Detects natural conversational prompts in free-text fields (WhatsApp, Email, SMS, Notes)
     * so Tapwing proactively offers your email, phone, name, address, birthdate, or age right
     * in the suggestion strip as soon as you type "Meine Nummer ist " or "Viele Grüße\n"!
     */
    fun detectConversationalIntent(textBeforeCursor: String): ConversationalIntent? {
        if (textBeforeCursor.length < 4) return null
        val tail = if (textBeforeCursor.length > 90) textBeforeCursor.substring(textBeforeCursor.length - 90) else textBeforeCursor

        // Extract optional trailing partial token being typed right after the cue
        val endsWithSpaceOrNewline = tail.last().isWhitespace()
        val partial: String
        val promptPart: String
        if (endsWithSpaceOrNewline) {
            partial = ""
            promptPart = tail
        } else {
            val lastWs = tail.indexOfLast { it.isWhitespace() }
            if (lastWs <= 0) return null
            partial = tail.substring(lastWs + 1)
            if (partial.length > 32) return null
            promptPart = tail.substring(0, lastWs + 1)
        }

        // Check signature greeting followed by newline (e.g. "Viele Grüße,\n" or "Best regards,\nMa")
        val lastNewline = promptPart.lastIndexOf('\n')
        if (lastNewline >= 0 && promptPart.substring(lastNewline + 1).isBlank()) {
            val prevLine = promptPart.substring(0, lastNewline)
                .substringAfterLast('\n')
                .trim()
                .trimEnd(',', '!', '.')
                .lowercase()
            if (prevLine in SIGNATURE_LINE_CUES) {
                return ConversationalIntent(SmartFieldType.FULL_NAME, partial)
            }
        }

        val normalizedPrompt = promptPart.trimEnd().lowercase()
        if (normalizedPrompt.isEmpty()) return null

        fun matchesAny(cues: List<String>): Boolean {
            for (cue in cues) {
                if (normalizedPrompt.endsWith(cue)) {
                    val boundaryIdx = normalizedPrompt.length - cue.length - 1
                    if (boundaryIdx < 0 || !normalizedPrompt[boundaryIdx].isLetterOrDigit()) {
                        return true
                    }
                }
            }
            return false
        }

        return when {
            matchesAny(EMAIL_CUES) -> ConversationalIntent(SmartFieldType.EMAIL, partial)
            matchesAny(PHONE_CUES) -> ConversationalIntent(SmartFieldType.PHONE, partial)
            matchesAny(FULL_NAME_CUES) -> ConversationalIntent(SmartFieldType.FULL_NAME, partial)
            matchesAny(ADDRESS_CUES) -> ConversationalIntent(SmartFieldType.STREET_ADDRESS, partial)
            matchesAny(CITY_CUES) -> ConversationalIntent(SmartFieldType.CITY, partial)
            matchesAny(BIRTHDATE_CUES) -> ConversationalIntent(SmartFieldType.BIRTHDATE, partial)
            matchesAny(AGE_CUES) -> ConversationalIntent(SmartFieldType.AGE, partial)
            else -> null
        }
    }

    /**
     * Validates a candidate value before dynamically auto-learning it into the user's
     * on-device profile suggestions pool. Rejects passwords, OTPs, random prose, and noise.
     */
    fun isValidAutoLearnValue(fieldType: SmartFieldType, raw: String): Boolean {
        val clean = raw.trim()
        if (clean.length < 2 || clean.length > 90) return false
        return when (fieldType) {
            SmartFieldType.EMAIL -> EMAIL_VALUE_REGEX.matches(clean)
            SmartFieldType.PHONE -> {
                if (!PHONE_ALLOWED_REGEX.matches(clean)) return false
                val digits = clean.count(Char::isDigit)
                digits in 7..18
            }
            SmartFieldType.POSTAL_CODE -> {
                POSTAL_VALUE_REGEX.matches(clean) && clean.any(Char::isDigit)
            }
            SmartFieldType.FIRST_NAME, SmartFieldType.LAST_NAME -> {
                clean.length in 2..30 &&
                    !clean.contains(' ') &&
                    PERSON_NAME_TOKEN_REGEX.matches(clean) &&
                    clean.lowercase() !in NON_NAME_COMMON_WORDS
            }
            SmartFieldType.FULL_NAME -> {
                clean.length in 4..60 &&
                    clean.contains(' ') &&
                    PERSON_NAME_TOKEN_REGEX.matches(clean)
            }
            SmartFieldType.CITY -> {
                clean.length in 2..40 &&
                    clean.first().isUpperCase() &&
                    clean.all { it.isLetter() || it == ' ' || it == '-' || it == '.' }
            }
            SmartFieldType.STREET_ADDRESS -> {
                clean.length in 5..70 && clean.any(Char::isLetter) && clean.any(Char::isDigit)
            }
            SmartFieldType.BIRTHDATE -> {
                com.tapwing.keyboard.data.PersonalProfile.parseDateParts(clean) != null
            }
            SmartFieldType.COMPANY -> {
                clean.length in 2..50 && clean.any(Char::isLetter)
            }
            else -> false
        }
    }

    /**
     * Extracts a clean email or phone number token from free-text input when typed cleanly,
     * allowing dynamic auto-recognition even outside explicit form fields.
     */
    fun detectInlineEntity(token: String): Pair<SmartFieldType, String>? {
        val clean = token.trim().trimEnd(',', '.', ';', '!', '?', ')').trimStart('(')
        if (clean.length < 5) return null
        if ('@' in clean && EMAIL_VALUE_REGEX.matches(clean)) {
            return SmartFieldType.EMAIL to clean
        }
        if (clean.startsWith("+") || clean.startsWith("00") || (clean.startsWith("0") && clean.length >= 9)) {
            if (isValidAutoLearnValue(SmartFieldType.PHONE, clean)) {
                return SmartFieldType.PHONE to clean
            }
        }
        return null
    }
}

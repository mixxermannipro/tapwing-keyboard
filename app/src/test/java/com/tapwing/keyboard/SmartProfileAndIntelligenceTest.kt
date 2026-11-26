package com.tapwing.keyboard

import android.text.InputType
import com.tapwing.keyboard.data.PersonalProfile
import com.tapwing.keyboard.data.SmartFieldType
import com.tapwing.keyboard.data.SmartFillKind
import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import com.tapwing.keyboard.dictionary.LanguageVocabularies
import com.tapwing.keyboard.dictionary.SnippetPlaceholderResolver
import com.tapwing.keyboard.dictionary.SuggestionEngine
import com.tapwing.keyboard.ime.SmartFieldDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartProfileAndIntelligenceTest {

    private val sampleProfile = PersonalProfile(
        firstName = "Max",
        lastName = "Mustermann",
        birthDate = "15.06.1995",
        emails = listOf("max@privat.de", "m.mustermann@firma.de"),
        phones = listOf("+49 170 1234567", "030 9876543"),
        street = "Musterstraße 42",
        postalCode = "10115",
        city = "Berlin",
        country = "Deutschland",
        company = "Tapwing GmbH",
        website = "https://tapwing.de"
    )

    @Test
    fun personalProfileComputesDynamicAgeBeforeAndAfterBirthday() {
        val beforeBirthday = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 14) }
        assertEquals(30, sampleProfile.computedAgeYears(beforeBirthday))

        val onBirthday = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 15) }
        assertEquals(31, sampleProfile.computedAgeYears(onBirthday))

        val afterBirthday = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 1) }
        assertEquals(31, sampleProfile.computedAgeYears(afterBirthday))

        assertEquals("15.06.1995", sampleProfile.formattedBirthDate("de"))
        assertEquals("1995-06-15", sampleProfile.formattedBirthDate("en"))
        assertEquals("15/06/1995", sampleProfile.formattedBirthDate("en_GB"))
    }

    @Test
    fun personalProfileJsonRoundTripAndLegacyMigration() {
        val json = sampleProfile.toJson()
        val restored = PersonalProfile.fromJson(json)
        assertEquals(sampleProfile, restored)

        val migrated = PersonalProfile.fromJson(
            rawJson = null,
            legacyUserName = "Anna Schmidt",
            legacyUserEmail = "anna@example.com"
        )
        assertEquals("Anna", migrated.firstName)
        assertEquals("Schmidt", migrated.lastName)
        assertEquals(listOf("anna@example.com"), migrated.emails)
    }

    @Test
    fun smartFieldDetectorRecognizesAllPersonalFormFieldsAndRejectsPasswords() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(SmartFieldType.FIRST_NAME, SmartFieldDetector.detectField(text, hint = "Vorname"))
        assertEquals(SmartFieldType.LAST_NAME, SmartFieldDetector.detectField(text, hint = "Nachname"))
        assertEquals(SmartFieldType.FULL_NAME, SmartFieldDetector.detectField(text, hint = "Full name"))
        assertEquals(SmartFieldType.EMAIL, SmartFieldDetector.detectField(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(SmartFieldType.PHONE, SmartFieldDetector.detectField(InputType.TYPE_CLASS_PHONE))
        assertEquals(SmartFieldType.STREET_ADDRESS, SmartFieldDetector.detectField(text, hint = "Straße und Hausnummer"))
        assertEquals(SmartFieldType.POSTAL_CODE, SmartFieldDetector.detectField(text, hint = "PLZ"))
        assertEquals(SmartFieldType.CITY, SmartFieldDetector.detectField(text, hint = "Stadt"))
        assertEquals(SmartFieldType.BIRTHDATE, SmartFieldDetector.detectField(text, hint = "Geburtsdatum"))
        assertEquals(SmartFieldType.AGE, SmartFieldDetector.detectField(text, hint = "Alter"))
        assertEquals(SmartFieldType.COMPANY, SmartFieldDetector.detectField(text, hint = "Firma"))

        // Security: never classify password fields or prose hints
        assertEquals(
            SmartFieldType.NONE,
            SmartFieldDetector.detectField(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, hint = "Vorname")
        )
        assertEquals(
            SmartFieldType.NONE,
            SmartFieldDetector.detectField(text, hint = "Benutzername")
        )
    }

    @Test
    fun smartFieldDetectorRecognizesConversationalIntentsAndInlineEntities() {
        val emailIntent = SmartFieldDetector.detectConversationalIntent("Meine E-Mail ist ")
        assertNotNull(emailIntent)
        assertEquals(SmartFieldType.EMAIL, emailIntent!!.fieldType)
        assertEquals("", emailIntent.partialTyped)

        val phoneIntent = SmartFieldDetector.detectConversationalIntent("Meine Nummer ist +49")
        assertNotNull(phoneIntent)
        assertEquals(SmartFieldType.PHONE, phoneIntent!!.fieldType)
        assertEquals("+49", phoneIntent.partialTyped)

        val sigIntent = SmartFieldDetector.detectConversationalIntent("Viele Grüße,\n")
        assertNotNull(sigIntent)
        assertEquals(SmartFieldType.FULL_NAME, sigIntent!!.fieldType)

        val addressIntent = SmartFieldDetector.detectConversationalIntent("Meine Adresse ist ")
        assertNotNull(addressIntent)
        assertEquals(SmartFieldType.STREET_ADDRESS, addressIntent!!.fieldType)

        val inlineEmail = SmartFieldDetector.detectInlineEntity("kontakt@tapwing.de,")
        assertEquals(SmartFieldType.EMAIL to "kontakt@tapwing.de", inlineEmail)

        val inlinePhone = SmartFieldDetector.detectInlineEntity("+491701234567")
        assertEquals(SmartFieldType.PHONE to "+491701234567", inlinePhone)
        assertNull(SmartFieldDetector.detectInlineEntity("1234"))
    }

    @Test
    fun suggestionEngineOffersSmartProfileChipsInFormFieldsAndConversations() {
        val firstNameChips = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "",
            locale = "de",
            smartFieldType = SmartFieldType.FIRST_NAME,
            personalProfile = sampleProfile,
            smartFillEnabled = true
        )
        assertTrue(firstNameChips.first().isSmartFill)
        assertEquals("Max", firstNameChips.first().text)
        assertEquals(SmartFillKind.NAME, firstNameChips.first().smartFillKind)

        val lastNameChips = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "Mu",
            locale = "de",
            smartFieldType = SmartFieldType.LAST_NAME,
            personalProfile = sampleProfile,
            smartFillEnabled = true
        )
        assertEquals("Mustermann", lastNameChips.first().text)
        assertEquals(2, lastNameChips.first().replaceCharCount)

        val emailChips = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "",
            locale = "de",
            isEmailField = true,
            smartFieldType = SmartFieldType.EMAIL,
            personalProfile = sampleProfile,
            smartFillEnabled = true
        )
        assertEquals("max@privat.de", emailChips.first().text)
        assertTrue(emailChips.any { it.text == "m.mustermann@firma.de" })

        val chatPhoneChips = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "Meine Nummer ist ",
            locale = "de",
            personalProfile = sampleProfile,
            smartFillEnabled = true
        )
        assertTrue("Expected smart phone chip in $chatPhoneChips", chatPhoneChips.first().isSmartFill)
        assertEquals("+49 170 1234567", chatPhoneChips.first().text)
    }

    @Test
    fun dynamicProfileLearningAndPerAppPreferenceRankingWork() {
        val store = AdaptiveDictionaryStore(storageFile = null)
        store.recordLearnedProfileValue(
            fieldType = SmartFieldType.EMAIL,
            rawValue = "work@company.com",
            appPackage = "com.microsoft.office.outlook"
        )
        store.recordProfileSelection(
            fieldType = SmartFieldType.EMAIL,
            value = "m.mustermann@firma.de",
            appPackage = "com.microsoft.office.outlook"
        )

        val outlookSuggestions = store.suggestProfileValues(
            fieldType = SmartFieldType.EMAIL,
            profile = sampleProfile,
            locale = "de",
            appPackage = "com.microsoft.office.outlook"
        )
        assertEquals("m.mustermann@firma.de", outlookSuggestions.first())
        assertTrue("work@company.com" in outlookSuggestions)

        val chromeSuggestions = store.suggestProfileValues(
            fieldType = SmartFieldType.EMAIL,
            profile = sampleProfile,
            locale = "de",
            appPackage = "com.android.chrome"
        )
        assertEquals("max@privat.de", chromeSuggestions.first())
    }

    @Test
    fun gboardLevelMissingSpaceSplittingDoubleLetterAndNounCapitalizationWork() {
        // Missing-space split ("ichbin" -> "ich bin", "thankyou" -> "thank you")
        assertEquals("ich bin", SuggestionEngine.autoCorrectionFor("ichbin", "de", atSentenceStart = false))
        assertEquals("thank you", SuggestionEngine.autoCorrectionFor("thankyou", "en", atSentenceStart = false))

        // Fat-finger double letters ("halllo" -> "hallo")
        val doubleFix = SuggestionEngine.autoCorrectionFor("halllo", "de", atSentenceStart = false)
        assertTrue("Expected hallo/Hallo for halllo, got $doubleFix", doubleFix.equals("hallo", ignoreCase = true))

        // German morphological noun suffix capitalization ("entscheidung" -> "Entscheidung")
        assertTrue(LanguageVocabularies.isLikelyGermanNounBySuffix("entscheidung"))
        assertTrue(LanguageVocabularies.isLikelyGermanNounBySuffix("möglichkeit"))
        assertFalse(LanguageVocabularies.isLikelyGermanNounBySuffix("machen"))
        assertEquals("Entscheidung", SuggestionEngine.autoCorrectionFor("entscheidung", "de", atSentenceStart = false))

        // Bilingual sentence auto-detection: English sentence while German keyboard is active
        val enChips = SuggestionEngine.suggestionsFor(
            textBeforeCursor = "I think you are ",
            locale = "de",
            secondaryLocales = listOf("en")
        )
        assertTrue("Expected English next-word prediction in $enChips", enChips.isNotEmpty())
    }

    @Test
    fun richerGrammarHintsAndProfileSnippetPlaceholdersWork() {
        val dassFix = SuggestionEngine.suggestionsFor("Ich glaube das ", "de")
        assertTrue("Expected 'glaube, dass' grammar hint in $dassFix", dassFix.any { it.isGrammarFix && it.text == "glaube, dass" })

        val anFix = SuggestionEngine.suggestionsFor("I ate a apple ", "en")
        assertTrue("Expected 'an apple' grammar hint in $anFix", anFix.any { it.isGrammarFix && it.text == "an apple" })

        val resolved = SnippetPlaceholderResolver.resolve(
            template = "Hi {VORNAME} {NACHNAME}, Tel: {TELEFON}, Ort: {PLZ} {STADT}",
            locale = "de",
            profile = sampleProfile
        )
        assertEquals("Hi Max Mustermann, Tel: +49 170 1234567, Ort: 10115 Berlin", resolved)
    }

    @Test
    fun adaptiveDictionaryPrefixIndexRemainsFastAtScale() {
        val store = AdaptiveDictionaryStore(storageFile = null)
        for (i in 0 until 2000) {
            store.addCustomWord("Projektwort$i", 10)
        }
        store.addCustomWord("TapwingSuperFast", 500)

        repeat(20) {
            store.rankedPrefixCandidates("Tapw", limit = 3)
        }
        val t0 = System.nanoTime()
        var top = ""
        repeat(200) {
            top = store.rankedPrefixCandidates("Tapw", limit = 3).firstOrNull().orEmpty()
        }
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        assertEquals("TapwingSuperFast", top)
        assertTrue("200 indexed prefix lookups over 2,000 words took ${elapsedMs}ms (expected < 500ms)", elapsedMs < 500.0)
    }
}

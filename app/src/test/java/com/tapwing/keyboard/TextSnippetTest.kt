package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Date

class TextSnippetTest {

    @Test
    fun resolvesMultiLinePlaceholdersForGermanAndEnglish() {
        val template = "Hallo {NAME},\nDatum: {DATUM} um {ZEIT}\nMail: {EMAIL}\nClip: {ZWISCHENABLAGE}"
        val resolved = SnippetPlaceholderResolver.resolve(
            template = template,
            locale = "de",
            latestClipboardText = "  TAP-42  ",
            userName = "Manuel",
            userEmail = "manuel@example.com",
            now = Date(1700000000000L)
        )

        assertTrue(resolved.contains("Hallo Manuel,"))
        assertTrue(resolved.contains("Mail: manuel@example.com"))
        assertTrue(resolved.contains("Clip: TAP-42"))
        assertTrue(resolved.lines().size == 4)
    }

    @Test
    fun storesPersistsAndSuggestsMultiLineSnippetsInAdaptiveDictionaryStore() {
        val tmpFile = File.createTempFile("tapwing_dict_snippets_", ".json")
        try {
            tmpFile.delete()
            val store = AdaptiveDictionaryStore(tmpFile)
            store.addOrUpdateSnippet(
                trigger = "angebot",
                title = "Angebot Vorlage",
                body = "Hallo,\nanbei wie besprochen.\nViele Grüße\n{NAME}"
            )

            val saved = store.lookupSnippet("/angebot")
            assertNotNull(saved)
            assertEquals("Angebot Vorlage", saved!!.title)

            val reloaded = AdaptiveDictionaryStore(tmpFile)
            assertNotNull(reloaded.lookupSnippet("/angebot"))

            val chips = SuggestionEngine.suggestionsFor(
                textBeforeCursor = "/angebot",
                locale = "de",
                dictionaryStore = reloaded,
                userName = "Erika"
            )
            assertTrue(chips.any { it.isShortcut && it.text.contains("Viele Grüße\nErika") })
        } finally {
            tmpFile.delete()
        }
    }
}

package com.tapwing.keyboard.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDictionaryParserTest {

    @Test
    fun parsesPlainWordsCsvGboardTsvArrowsAndJson() {
        val mixed = """
            # Kommentarzeile
            Tapwing
            Quantencomputer,42
            mfg => Mit freundlichen Grüßen
            addr	Musterstraße 12	de
            	Kubernetes	en
        """.trimIndent()

        val parsed = CustomDictionaryParser.parse(mixed)
        assertEquals(10, parsed.words["Tapwing"])
        assertEquals(42, parsed.words["Quantencomputer"])
        assertEquals(10, parsed.words["Kubernetes"])
        assertEquals("Mit freundlichen Grüßen", parsed.shortcuts["mfg"])
        assertEquals("Musterstraße 12", parsed.shortcuts["addr"])

        val json = CustomDictionaryParser.exportToJson(parsed.words, parsed.shortcuts)
        val roundTrip = CustomDictionaryParser.parse(json)
        assertEquals(parsed.words, roundTrip.words)
        assertEquals(parsed.shortcuts, roundTrip.shortcuts)
    }
}

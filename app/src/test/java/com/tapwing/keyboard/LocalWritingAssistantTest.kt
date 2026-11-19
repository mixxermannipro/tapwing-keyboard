package com.tapwing.keyboard.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWritingAssistantTest {

    @Test
    fun proofreadFixesTyposDuplicatesAndPunctuation() {
        val de = LocalWritingAssistant.transform("ihc bin bin heut spät dran", WritingAction.PROOFREAD, "de")
        assertEquals("Ich bin heute spät dran.", de)

        val en = LocalWritingAssistant.transform("teh meeting is at at noon", WritingAction.PROOFREAD, "en")
        assertEquals("The meeting is at noon.", en)
    }

    @Test
    fun professionalRewritesInformalGreeting() {
        val de = LocalWritingAssistant.transform("hey kannst du mir das schicken? lg", WritingAction.PROFESSIONAL, "de")
        assertTrue(de.startsWith("Guten Tag"))
        assertTrue(de.contains("könnten Sie"))
        assertTrue(de.contains("Mit freundlichen Grüßen"))
    }

    @Test
    fun conciseRemovesFillers() {
        val en = LocalWritingAssistant.transform(
            "I basically just really want to actually verify this",
            WritingAction.CONCISE,
            "en"
        )
        assertFalse(en.contains("basically", ignoreCase = true))
        assertFalse(en.contains("actually", ignoreCase = true))
    }

    @Test
    fun emotiveAddsWarmEnding() {
        val de = LocalWritingAssistant.transform("Danke für die Hilfe.", WritingAction.EMOTIVE, "de")
        assertTrue(de.endsWith("! 😊"))
    }
}

package com.tapwing.keyboard.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CleanupPromptBuilderTest {

    @Test
    fun defaultPromptFollowsDeviceSystemLanguageAndCoversRamblerRules() {
        val dePrompt = CleanupPromptBuilder.buildDefaultPrompt(
            systemLocale = Locale.GERMANY,
            activeKeyboardLocale = "en",
            useSystemLanguage = true
        )
        assertTrue(dePrompt.contains("de-DE"))
        assertTrue(dePrompt.contains("self-corrections", ignoreCase = true))
        assertTrue(dePrompt.contains("disfluencies", ignoreCase = true))

        val custom = CleanupPromptBuilder.resolveSystemPrompt(
            customPrompt = "Clean cleanly in {LANGUAGE}.",
            useSystemLanguage = true,
            customVocabulary = setOf("TapwingAI"),
            systemLocale = Locale.FRANCE
        )
        assertTrue(custom.contains("fr-FR"))
        assertTrue(custom.contains("TapwingAI"))
    }

    @Test
    fun stripsQwenThinkTagsAndQuotes() {
        val raw = "<think>analyzing grammar...</think>\n\"Hallo Welt, das klappt.\""
        assertEquals("Hallo Welt, das klappt.", CleanupPromptBuilder.sanitizeModelOutput(raw))
    }
}

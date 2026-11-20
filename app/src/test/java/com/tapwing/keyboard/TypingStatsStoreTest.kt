package com.tapwing.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TypingStatsStoreTest {

    @Test
    fun tracksStatsComputesWpmAndPausesInIncognito() {
        var now = 1_000_000L
        val tmpFile = File.createTempFile("tapwing_stats_", ".json")
        try {
            tmpFile.delete()
            val store = TypingStatsStore(tmpFile, clockMs = { now })

            // Simulate 15 seconds of steady typing (~5 chars every 1000 ms => 75 chars in 14s)
            repeat(15) {
                store.recordKeystroke(chars = 5, incognito = false)
                now += 1_000L
            }
            store.recordWordCompleted(incognito = false)
            store.recordAutocorrect(incognito = false)
            store.recordSuggestionAccepted(savedChars = 6, incognito = false)
            store.recordSwipeWord(wordLength = 7, incognito = false)
            store.recordVoiceDictation(insertedChars = 40, incognito = false)
            store.recordSnippetInserted(insertedChars = 25, incognito = false)

            // Incognito activity must be ignored completely
            store.recordKeystroke(chars = 100, incognito = true)
            store.recordWordCompleted(incognito = true)
            store.recordVoiceDictation(insertedChars = 200, incognito = true)

            val stats = store.stats.value
            assertEquals(75L, stats.charactersTyped)
            assertEquals(3L, stats.wordsTyped) // 1 completed + 1 suggestion + 1 swipe
            assertEquals(1L, stats.autocorrectionsApplied)
            assertEquals(1L, stats.suggestionsAccepted)
            assertEquals(1L, stats.swipesDecoded)
            assertEquals(1L, stats.voiceDictations)
            assertEquals(1L, stats.snippetsInserted)
            assertEquals(6L + 6L + 40L + 25L, stats.charactersSaved)
            assertTrue(stats.estimatedWpm > 0)

            val reloaded = TypingStatsStore(tmpFile)
            assertEquals(stats, reloaded.stats.value)

            store.reset()
            assertEquals(0L, store.stats.value.wordsTyped)
            assertEquals(0L, store.stats.value.charactersTyped)
        } finally {
            tmpFile.delete()
        }
    }
}

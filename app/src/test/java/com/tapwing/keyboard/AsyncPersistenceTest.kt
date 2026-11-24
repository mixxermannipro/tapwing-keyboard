package com.tapwing.keyboard.data

import com.tapwing.keyboard.dictionary.AdaptiveDictionaryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AsyncPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun asyncDictionaryStoreFlushesAtomicallyAndPreservesDirtyStateOnReload() {
        val file = tempFolder.newFile("dict_async.json")
        val store = AdaptiveDictionaryStore(file, synchronousPersistence = false)

        store.recordTypedWord(
            word = "TapwingTestwort",
            previousWord = "hallo",
            learningEnabled = true,
            incognito = false
        )
        // Reload while dirty must NOT wipe in-memory changes
        store.reloadIfModifiedOnDisk()
        assertTrue(store.allKnownVocabularyWords().contains("TapwingTestwort"))

        // Flush persists atomically to disk
        store.flush()
        val reloaded = AdaptiveDictionaryStore(file, synchronousPersistence = true)
        assertTrue(reloaded.allKnownVocabularyWords().contains("TapwingTestwort"))
    }

    @Test
    fun asyncTypingStatsStoreFlushesAtomically() {
        val file = tempFolder.newFile("stats_async.json")
        var now = 1_000L
        val store = TypingStatsStore(file, clockMs = { now }, synchronousPersistence = false)

        store.recordKeystroke(5, incognito = false)
        now += 500L
        store.recordKeystroke(4, incognito = false)
        store.recordWordCompleted(incognito = false)
        store.reloadIfModifiedOnDisk()
        assertEquals(9L, store.stats.value.charactersTyped)

        store.flush()
        val reloaded = TypingStatsStore(file, synchronousPersistence = true)
        assertEquals(9L, reloaded.stats.value.charactersTyped)
        assertEquals(1L, reloaded.stats.value.wordsTyped)
    }
}

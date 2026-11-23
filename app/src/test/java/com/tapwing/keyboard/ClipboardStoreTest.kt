package com.tapwing.keyboard.clipboard

import com.tapwing.keyboard.data.ClipboardRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClipboardStoreTest {

    @Test
    fun copyingTheSameTextKeepsItsIdAndExactWhitespace() {
        var now = 50_000L
        val store = ClipboardStore(null) { now }
        store.addClip("  First line\nSecond line  ")
        val original = store.items.value.single()
        now++
        store.addClip(original.text)

        assertEquals("  First line\nSecond line  ", original.text)
        assertEquals(original.copy(timestampMs = now), store.items.value.single())
    }

    @Test
    fun sameImageAtAnotherPathKeepsPinAndOcrAcrossRestart() {
        val folder = kotlin.io.path.createTempDirectory("tapwing-clips").toFile()
        try {
            val history = File(folder, "history.json")
            val screenshot = File(folder, "screenshot.png").apply { writeText("same image bytes") }
            val copy = File(folder, "copied.png").apply { writeText("same image bytes") }
            val different = File(folder, "other.png").apply { writeText("different bytes") }
            val store = ClipboardStore(history)
            val original = requireNotNull(store.addImageClip(screenshot.path, ocrText = "Recognized"))
            store.togglePin(original.id, ClipboardRetention.NEVER, 100)

            val reloaded = ClipboardStore(history)
            assertEquals(original.id, reloaded.addImageClip(copy.path)?.id)
            reloaded.addImageClip(different.path)
            assertEquals(2, reloaded.items.value.size)
            assertEquals("Recognized", reloaded.items.value.first().ocrText)
            assertTrue(reloaded.items.value.first().pinned)
            assertTrue(screenshot.isFile)
            assertTrue(!copy.exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun storesAllCopiedTextIncludingApiKeysOtpUrlsAndMultiline() {
        val tempFile = File.createTempFile("tapwing_clip_test", ".json")
        tempFile.deleteOnExit()
        val store = ClipboardStore(storageFile = tempFile)
        val key1 = "AIza" + "x".repeat(35)
        val key2 = "sk-proj-" + "x".repeat(40)
        val otp = "482910"
        val url = "https://example.com/path?q=1"
        val multiline = "Zeile 1\nZeile 2"

        store.addClip(key1)
        store.addClip(key2)
        store.addClip(otp)
        store.addClip(url)
        store.addClip(multiline)
        store.addClip("A normal sentence")

        val texts = store.items.value.map { it.text }
        assertTrue(texts.contains(key1))
        assertTrue(texts.contains(key2))
        assertTrue(texts.contains(otp))
        assertTrue(texts.contains(url))
        assertTrue(texts.contains(multiline))
        assertTrue(texts.contains("A normal sentence"))

        val reloaded = ClipboardStore(storageFile = tempFile)
        assertEquals(texts.toSet(), reloaded.items.value.map { it.text }.toSet())
    }

    @Test
    fun enforcesMax100UnpinnedAndPreservesPinned() {
        var now = 1_000_000L
        val store = ClipboardStore(storageFile = null, clockMs = { now })

        store.addClip("PINNED_KEEP", ClipboardRetention.DAYS_7, maxUnpinnedItems = 100)
        val pinnedId = store.items.value.first().id
        store.togglePin(pinnedId, ClipboardRetention.DAYS_7, maxUnpinnedItems = 100)

        for (i in 1..120) {
            now += 10L
            store.addClip("clip-$i", ClipboardRetention.DAYS_7, maxUnpinnedItems = 100)
        }

        val items = store.items.value
        assertEquals(101, items.size)
        assertTrue(items.any { it.text == "PINNED_KEEP" && it.pinned })
        assertEquals(100, items.count { !it.pinned })
        assertTrue(items.any { it.text == "clip-120" })
        assertTrue(items.none { it.text == "clip-1" })
    }

    @Test
    fun prunesExpiredUnpinnedByRetentionDuration() {
        var now = 10_000L
        val store = ClipboardStore(storageFile = null, clockMs = { now })

        store.addClip("old-clip", ClipboardRetention.HOUR_1, maxUnpinnedItems = 100)
        now += ClipboardRetention.HOUR_1.durationMs + 5_000L
        store.addClip("fresh-clip", ClipboardRetention.HOUR_1, maxUnpinnedItems = 100)

        val items = store.items.value
        assertEquals(1, items.size)
        assertEquals("fresh-clip", items.first().text)
    }

    @Test
    fun storesImageClipsAndUpdatesOcrMetadata() {
        val store = ClipboardStore(storageFile = null, clockMs = { 50_000L })
        val clip = store.addImageClip(
            imagePath = "/tmp/fake_shot.png",
            mimeType = "image/png",
            displayLabel = "Screenshot_1.png"
        )
        assertTrue(clip != null && clip.isImage)
        store.updateClipOcrText(clip!!.id, "Rechnung 42 EUR", ClipboardRetention.DAYS_7, 100)
        val stored = store.items.value.first()
        assertEquals("Rechnung 42 EUR", stored.ocrText)
    }
}

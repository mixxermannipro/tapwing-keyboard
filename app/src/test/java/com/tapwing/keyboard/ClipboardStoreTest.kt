package com.tapwing.keyboard.clipboard

import com.tapwing.keyboard.data.ClipboardRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardStoreTest {

    @Test
    fun leavesApiKeysOutOfClipboardHistory() {
        val store = ClipboardStore(storageFile = null)
        store.addClip("AIza" + "x".repeat(35))
        store.addClip("AQ." + "x".repeat(40))
        store.addClip("sk-proj-" + "x".repeat(40))
        store.addClip("A normal sentence")
        assertEquals(listOf("A normal sentence"), store.items.value.map { it.text })
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

package com.tapwing.keyboard.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UndoManagerTest {

    @Test
    fun undoRequiresTheSameCaretAndTextOnBothSides() {
        val originalLocation = UndoAnchor("Hallo Welt ", "", "danach", 11)
        val manager = UndoManager()
        manager.record("", "Welt ", UndoReason.VOICE_INSERT, "Undo", anchor = originalLocation)

        assertNull(manager.matchingUndo(originalLocation.copy(offset = 30)))
        assertNull(manager.matchingUndo(originalLocation.copy(after = "neu")))
        assertNull(manager.matchingUndo(originalLocation.copy(selected = "danach")))
        assertEquals("Welt ", manager.matchingUndo(originalLocation)?.afterText)
        assertNotNull(manager.activeBanner())
    }

    @Test
    fun deletingTextCanBeUndoneOnlyWhereItWasDeleted() {
        val manager = UndoManager()
        val location = UndoAnchor("Erster Satz.", "", " Dritter Satz.", 12)
        manager.record(" Zweiter Satz.", "", UndoReason.BULK_DELETE, "Restore", anchor = location)

        assertNull(manager.matchingUndo(location.copy(before = "")))
        assertEquals(" Zweiter Satz.", manager.matchingUndo(location)?.beforeText)
    }

    @Test
    fun bannerAutoDismissesAfterTwoUnrelatedEditsWhileKeepingHistory() {
        val manager = UndoManager()
        manager.record("ihc", "ich", UndoReason.AUTOCORRECT, "Undo: ihc")

        assertNotNull(manager.activeBanner())
        manager.notifyUnrelatedTyping()
        assertNotNull(manager.activeBanner())
        manager.notifyUnrelatedTyping()
        assertNull(manager.activeBanner())

        val popped = manager.popUndo()
        assertNotNull(popped)
        assertEquals("ihc", popped?.beforeText)
    }
}

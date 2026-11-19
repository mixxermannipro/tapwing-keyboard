package com.tapwing.keyboard.undo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UndoManagerTest {

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

package com.openstudio.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditHistoryTest {
    @Test fun undoAndRedoRestoreSnapshotsInOrder() {
        val history = EditHistory<String>()
        history.record("original")
        assertEquals("original", history.undo("brightness"))
        assertEquals("brightness", history.redo("original"))
    }

    @Test fun recordingNewEditClearsRedoBranch() {
        val history = EditHistory<String>()
        history.record("A")
        assertEquals("A", history.undo("B"))
        history.record("C")
        assertNull(history.redo("D"))
    }

    @Test fun historyIsBounded() {
        val history = EditHistory<Int>(limit = 2)
        history.record(1); history.record(2); history.record(3)
        assertEquals(2, history.undoCount())
        assertEquals(3, history.undo(4))
        assertEquals(2, history.undo(3))
        assertNull(history.undo(2))
    }

    @Test fun emptyHistoryReturnsNullAndClearResetsBothStacks() {
        val history = EditHistory<String>()
        assertNull(history.undo("A"))
        history.record("A")
        assertEquals("A", history.undo("B"))
        history.clear()
        assertNull(history.undo("C"))
        assertNull(history.redo("C"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveLimit() { EditHistory<String>(0) }
}
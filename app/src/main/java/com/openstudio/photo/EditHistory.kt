package com.openstudio.photo

/** Bounded edit history; snapshots are supplied by the caller and remain immutable. */
internal class EditHistory<T>(private val limit: Int = 50) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    init { require(limit > 0) { "History limit must be positive" } }

    fun record(stateBeforeEdit: T) {
        undoStack.addLast(stateBeforeEdit)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(currentState: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentState)
        return undoStack.removeLast()
    }

    fun redo(currentState: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentState)
        while (undoStack.size > limit) undoStack.removeFirst()
        return redoStack.removeLast()
    }

    fun clear() { undoStack.clear(); redoStack.clear() }
    internal fun undoCount(): Int = undoStack.size
    internal fun redoCount(): Int = redoStack.size
}
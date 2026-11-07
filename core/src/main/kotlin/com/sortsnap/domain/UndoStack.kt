package com.sortsnap.domain

/** Bounded LIFO used by swipe mode: only the last [capacity] actions can be undone. */
class UndoStack<T>(private val capacity: Int = 10) {
    init { require(capacity > 0) }
    private val items = ArrayDeque<T>()

    val size: Int get() = items.size
    val canUndo: Boolean get() = items.isNotEmpty()

    fun push(item: T) {
        items.addLast(item)
        while (items.size > capacity) items.removeFirst()
    }

    fun pop(): T? = items.removeLastOrNull()

    fun peek(): T? = items.lastOrNull()

    fun clear() = items.clear()

    fun toList(): List<T> = items.toList()
}

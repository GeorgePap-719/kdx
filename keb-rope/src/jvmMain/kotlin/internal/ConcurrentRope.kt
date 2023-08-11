package keb.ropes.internal

import keb.ropes.Rope
import keb.ropes.RopeIterator
import kotlinx.atomicfu.atomic

internal class ConcurrentRope(text: Rope) {
    private val _text = atomic(text)

    val value get() = _text.value // snapshot

    val length: Int
        get() {
            val cur = value
            return cur.length
        }

    operator fun get(index: Int): Char? {
        val cur = value
        return cur[index]
    }

    operator fun plus(other: Rope) {
        while (true) {
            val cur = value
            val newValue = cur.plus(other)
            if (_text.compareAndSet(cur, newValue)) break
        }
    }

    fun set(value: Rope) {
        while (true) {
            val cur = this.value
            if (_text.compareAndSet(cur, value)) break
        }
    }

    operator fun iterator(): RopeIterator = value.iterator()
}
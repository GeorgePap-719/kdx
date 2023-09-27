package kdx.internal

import java.util.concurrent.ConcurrentHashMap

/**
 * Concurrent set implemented on top of [ConcurrentHashMap].
 * Inspired from ktor's `ConcurrentSet` implementation.
 */
@Suppress("FunctionName")
internal fun <Key : Any> ConcurrentSet(): MutableSet<Key> = object : MutableSet<Key> {
    // Note: `ConcurrentHashMap` specifies
    // functions should not depend on its ordering.
    // Though, we support mostly on its key's view.
    private val delegate = ConcurrentHashMap<Key, Unit>() // alternative TreeSet

    override fun add(element: Key): Boolean {
        if (delegate.containsKey(element)) return false
        delegate[element] = Unit
        return true
    }

    override fun addAll(elements: Collection<Key>): Boolean = elements.all { add(it) }

    override val size: Int get() = delegate.size

    override fun clear() {
        delegate.clear()
    }

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun containsAll(elements: Collection<Key>): Boolean = elements.containsAll(delegate.keys)

    override fun contains(element: Key): Boolean = delegate.containsKey(element)

    override fun iterator(): MutableIterator<Key> = delegate.keys.iterator()

    override fun retainAll(elements: Collection<Key>): Boolean {
        val removedElements = mutableSetOf<Key>()
        for (key in delegate.keys) {
            if (key !in elements) removedElements.add(key)
        }
        return removeAll(removedElements)
    }

    override fun removeAll(elements: Collection<Key>): Boolean = elements.all { remove(it) }

    override fun remove(element: Key): Boolean = delegate.remove(element) != null
}

internal fun <K : Any> Set<K>.toConcurrentSet(): MutableSet<K> {
    return ConcurrentSet<K>().also { it.addAll(this) }
}
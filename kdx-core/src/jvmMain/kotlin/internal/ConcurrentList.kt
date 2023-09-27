package keb.ropes.internal

import kotlinx.atomicfu.locks.synchronized

/**
 * Concurrent linked-list with lock-free reads and synchronized modifications.
 */
internal class ConcurrentLinkedList<T : Any>(initialLength: Int) : AbstractMutableList<T>() {
    // Not the proper structure for this,
    // as we have to synchronize everything on write.
    // If JvmEngine does not use any operations with indexes,
    // use ready concurrent list from `java.util.concurrent`.
    //TODO: add a better atomic array structure if there is time.
    private val array = AtomicResizeableArray<T?>(initialLength.coerceAtLeast(1))

    override val size: Int get() = array.size

    // for nullable variant see [getOrNull].
    override fun get(index: Int): T {
        return getOrNull(index) ?: throw NoSuchElementException()
    }

    fun getOrNull(index: Int): T? {
        checkElementIndex(index)
        return array[index]
    }

    override fun removeAt(index: Int): T {
        synchronized(array) {
            checkElementIndex(index)
            val cur = array[index] ?: throw NoSuchElementException()
            array.setSynchronized(index, null)
            return cur
        }
    }

    override fun set(index: Int, element: T): T {
        synchronized(array) {
            checkElementIndex(index)
            val cur = array[index] ?: throw NoSuchElementException()
            array.setSynchronized(index, element)
            return cur
        }
    }

    override fun add(index: Int, element: T) {
        synchronized(array) {
            checkPositionIndex(index)
            array.setSynchronized(index, element)
        }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        synchronized(array) {
            var curIndex = size
            return elements.all {
                array.setSynchronized(curIndex++, it)
                true
            }
        }
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        synchronized(array) {
            checkPositionIndex(index)
            var curIndex = index
            return elements.all {
                array.setSynchronized(curIndex++, it)
                true
            }
        }
    }

    override fun clear() {
        synchronized(array) {
            for (i in 0..<array.size) {
                array.setSynchronized(i, null)
            }
        }
    }

    override fun isEmpty(): Boolean {
        val curArray = array // volatile read
        for (i in 0..<curArray.size) {
            if (curArray[i] != null) return false
        }
        return true
    }

    private fun checkElementIndex(index: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
    }

    private fun checkPositionIndex(index: Int) {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
    }

    private fun checkRangeIndexes(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || toIndex > size) {
            throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex, size: $size")
        }
        if (fromIndex > toIndex) {
            throw IllegalArgumentException("fromIndex: $fromIndex > toIndex: $toIndex")
        }
    }
}

internal fun <T : Any> List<T>.toConcurrentLinkedList(): MutableList<T> {
    return ConcurrentLinkedList<T>(this.size).also { it.addAll(this) }
}
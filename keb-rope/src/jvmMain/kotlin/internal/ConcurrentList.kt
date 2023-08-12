package keb.ropes.internal

import kotlinx.atomicfu.locks.synchronized

class ConcurrentLinkedList<T : Any> : AbstractMutableList<T>() {
    // Not the proper structure for this,
    // as we have to synchronize everything on write.
    //TODO: add a better atomic array structure if there is time.
    private val array = AtomicResizeableArray<T?>(1)

    override val size: Int get() = array.size

    override fun clear() {
        synchronized(array) {
            for (i in 0..<array.size) {
                array.setSynchronized(i, null)
            }
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
            var curIndex = index
            return elements.all {
                array.setSynchronized(curIndex++, it)
                true
            }
        }
    }

    override fun add(index: Int, element: T) {
        synchronized(array) { array.setSynchronized(index, element) }
    }

    override fun add(element: T): Boolean {
        add(size, element)
        return true
    }

    // for nullable variant see [getOrNull].
    override fun get(index: Int): T {
        checkElementIndex(index)
        return array[index] ?: throw NoSuchElementException()
    }

    fun getOrNull(index: Int): T? {
        checkElementIndex(index)
        return array[index]
    }


    override fun isEmpty(): Boolean {
        val curArray = array // volatile read
        for (i in 0..<curArray.size) {
            if (curArray[i] != null) return false
        }
        return true
    }

    override fun iterator(): MutableIterator<T> {
        TODO("Not yet implemented")
    }

    override fun listIterator(): MutableListIterator<T> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        TODO("Not yet implemented")
    }

    override fun removeAt(index: Int): T {
        synchronized(array) {
            val cur = array[index] ?: throw NoSuchElementException()
            array.setSynchronized(index, null)
            return cur
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        val curArray = array
        if ()
            for (i in fromIndex..<toIndex) {

            }
    }

    override fun set(index: Int, element: T): T {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: T): Int {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(element: T): Boolean {
        TODO("Not yet implemented")
    }

    private fun checkElementIndex(index: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
    }

    internal fun checkPositionIndex(index: Int) {
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


internal fun <T> List<T>.toConcurrentLinkedList(): MutableList<T> {
//    return ConcurrentLinkedList<T>().also { it.addAll(this) }
}
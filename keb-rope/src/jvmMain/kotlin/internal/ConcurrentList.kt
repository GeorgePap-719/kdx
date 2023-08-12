package keb.ropes.internal

import kotlinx.atomicfu.locks.synchronized

@Suppress("FunctionName")
internal fun <T : Any> ConcurrentLinkedList(): MutableList<T> = object : MutableList<T> {
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

    override fun get(index: Int): T = array[index] ?: throw NoSuchElementException()

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
        TODO("Not yet implemented")
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
}


internal fun <T> List<T>.toConcurrentLinkedList(): MutableList<T> {
    return ConcurrentLinkedList<T>().also { it.addAll(this) }
}
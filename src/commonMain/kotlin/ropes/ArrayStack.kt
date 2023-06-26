package keb.ropes

open class ArrayStack<T>(initialLength: Int) {
    protected var array = arrayOfNulls<Any?>(initialLength.coerceAtLeast(1))

    var size = 0
        private set

    fun push(value: T) {
        val curArray = array // defensive-copy. TODO: not sure if it's worth it
        val curLen = size
        if (curLen + 1 <= array.size) {
            curArray[size++] = value
            return
        }
        // needs resize
        val newArray = arrayOfNulls<Any?>((curLen * 2).coerceAtLeast(curLen + 1))
        for (i in 0 until curLen) newArray[i] = array[i]
        newArray[size++] = value
        array = newArray
    }

    fun popOrNull(): T? {
        if (size == 0) return null
        @Suppress("UNCHECKED_CAST")
        val value = array[size - 1] as T
        array[size - 1] = null
        size--
        return value
    }
}

class PeekableArrayStack<T>(initialLength: Int) : ArrayStack<T>(initialLength) {
    fun peek(): T? {
        if (size == 0) return null
        @Suppress("UNCHECKED_CAST")
        return array[size - 1] as T
    }

    fun peekAll(): List<T> {
        @Suppress("UNCHECKED_CAST")
        return if (size == 0) return emptyList() else array.toList() as List<T>
    }

    // iterates elements as we would pop from stack
    internal inline fun peekEach(action: (T) -> Unit) {
        var curIndex = 0
        while (curIndex != size) {
            @Suppress("UNCHECKED_CAST")
            action(array[size - curIndex - 1] as T)
            curIndex++
        }
    }
}
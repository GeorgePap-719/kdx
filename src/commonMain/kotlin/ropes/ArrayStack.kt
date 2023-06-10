package keb.ropes

class ArrayStack<T>(initialLength: Int) {
    private var array = arrayOfNulls<Any?>(initialLength)

    var size = 0

    fun push(value: T) {
        val curArray = array // defensive-copy. TODO: not sure if it's worth it
        val curLen = size
        if (size + 1 <= array.size) {
            curArray[size++] = value
            return
        }
        // needs resize
        val newArray = arrayOfNulls<Any?>(size * 2)
        for (i in 0 until curLen) newArray[i] = array[i]
        newArray[size++] = value
        array = newArray
    }

    fun popOrNull(): T? {
        if (size <= 0) return null
        @Suppress("UNCHECKED_CAST")
        val value = array[size - 1] as T
        array[size - 1] = null
        size--
        return value
    }
}
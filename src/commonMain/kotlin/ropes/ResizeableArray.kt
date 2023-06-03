package keb.ropes

class ResizeableArray<T>(initialLength: Int) {
    private var array = arrayOfNulls<Any?>(initialLength)

    val size: Int get() = array.size

    operator fun get(index: Int): T? {
        if (index > size) return null
        @Suppress("UNCHECKED_CAST")
        return array[index] as T?
    }

    operator fun set(index: Int, value: T) {
        val curArray = array
        val curLen = size
        if (index < size) {
            curArray[index] = value
            return
        }
        // needs resize
        val newArray = arrayOfNulls<Any?>((index + 1).coerceAtLeast(2 * curLen))
        for (i in 0 until curLen) newArray[i] = curArray[i]
        newArray[index] = value
        array = newArray
    }
}
package keb.ropes

class ResizeableArray<T>(initialLength: Int) {
    private var array = arrayOfNulls<Any?>(initialLength.coerceAtLeast(1))

    val size: Int get() = array.size // for debug output

    operator fun get(index: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return if (index < size) array[index] as T? else null
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
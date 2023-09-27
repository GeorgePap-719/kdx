package keb.ropes.internal

import java.util.concurrent.atomic.AtomicReferenceArray

// Copied from `kotlinx.coroutines`.
internal class AtomicResizeableArray<T>(initialLength: Int) {
    @Volatile
    private var array = AtomicReferenceArray<T>(initialLength)

    val size: Int get() = array.length() // for debug output

    operator fun get(index: Int): T? {
        val array = this.array // volatile read
        return if (index < array.length()) array[index] else return null
    }

    // Must not be called concurrently,
    // e.g. always use synchronized(this) to call this function.
    fun setSynchronized(index: Int, value: T) {
        val curArray = array
        val curLen = curArray.length()
        if (index < curLen) {
            curArray[index] = value
            return
        }
        val newArray = AtomicReferenceArray<T>((index + 1).coerceAtLeast(curLen * 2))
        for (i in 0..<curLen) newArray[i] = array[i]
        newArray[index] = value
        array = newArray
    }
}
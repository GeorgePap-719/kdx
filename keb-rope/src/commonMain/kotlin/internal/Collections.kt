package keb.ropes.internal

/**
 * Replaces all the [elements] of the specified collection to this list.
 */
fun <T> MutableList<T>.replaceAll(elements: Collection<T>) {
    clear()
    addAll(elements)
}
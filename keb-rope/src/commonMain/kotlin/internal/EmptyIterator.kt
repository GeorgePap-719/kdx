package keb.internal

internal object EmptyIterator : Iterator<Nothing> {
    override fun hasNext(): Boolean = false
    override fun next(): Nothing = throw NoSuchElementException()
}
package keb.ropes.internal

/**
 * Returns an [Iterable] that visits the elements representing the symmetric difference,
 * i.e., the elements that are in `this` or in `other` but not in both, in ascending order.
 *
 * @throws IllegalArgumentException if the provided sets are of different sizes.
 */
internal fun <T> Set<T>.symmetricDifference(other: Set<T>): SymmetricDifference<T> {
    require(this.size == other.size) { "cannot produce symmetric difference for sets of different size" }
    val thisIterator = this.iterator()
    val otherIterator = other.iterator()
    return SymmetricDifference(thisIterator, otherIterator)
}

internal class SymmetricDifference<T>(
    private val thisIterator: Iterator<T>,
    private val otherIterator: Iterator<T>,
) : Iterable<T> {
    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private var _index = 2
            private val index get() = _index.mod(2)

            private fun getIndexAndIncrease(): Int {
                val cur = index
                _index++
                return cur
            }

            override fun hasNext(): Boolean = thisIterator.hasNext()

            override fun next(): T = when (getIndexAndIncrease()) {
                0 -> {
                    val next = thisIterator.next()
                    otherIterator.next()
                    next
                }

                1 -> {
                    val next = otherIterator.next()
                    thisIterator.next()
                    next
                }

                else -> error("unexpected")
            }
        }
    }
}
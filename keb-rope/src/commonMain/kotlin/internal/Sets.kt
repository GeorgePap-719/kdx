package keb.ropes.internal

/**
 * Returns an [Iterable] that visits the elements representing the intersection,
 * i.e., the elements that are both in `this` and `other`, in ascending order.
 */
internal fun <T> Set<T>.intersection(other: Set<T>): Intersection<T> {
    val thisIterator = this.iterator()
    val otherIterator = other.iterator()
    return Intersection(thisIterator, otherIterator)
}

internal class Intersection<T>(
    private val thisIterator: Iterator<T>,
    private val otherIterator: Iterator<T>,
) : Iterable<T> {
    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            override fun hasNext(): Boolean {
                TODO("Not yet implemented")
            }

            override fun next(): T {
                TODO("Not yet implemented")
            }
        }
    }
}

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
        //TODO: this actually can optimized just a bit if there time,
        // to avoid an ever increasing Int.
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

private class MergedIterators<T>(
    private val leftIterator: Iterator<T>,
    private val rightIterator: Iterator<T>,
    private val comparator: BooleanComparator<T>
) : Iterable<T> {
    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private val left get() = leftIterator.next()
            private val right get() = rightIterator.next()

            override fun hasNext(): Boolean {
                return leftIterator.hasNext() || rightIterator.hasNext()
            }

            override fun next(): T {
                val leftPeek = leftIterator.hasNext()
                val rightPeek = rightIterator.hasNext()
                return when {
                    leftPeek && rightPeek -> if (comparator.compare(left, right)) left else right
                    leftPeek && !rightPeek -> left
                    !leftPeek && rightPeek -> right
                    else -> error("unexpected")
                }
            }
        }
    }
}

/**
 * Provides a comparison function similar to [Comparator], but returning a [Boolean].
 */
private fun interface BooleanComparator<T> {
    /**
     * Compares its two arguments for order.
     * Returns `true` if the first argument is greater than the second,
     * or `false` if the first argument is less than the second.
     */
    fun compare(a: T, b: T): Boolean
}
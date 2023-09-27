package kdx.internal

/**
 * Returns an [Iterable] that visits the elements representing the intersection,
 * i.e., the elements that are both in `this` and `other`, in ascending order.
 */
//TODO: delete if not used
internal fun <T> Set<T>.intersection(other: Set<T>): Intersection<T> {
    val thisIterator = this.iterator()
    val otherIterator = other.iterator()
    return Intersection(thisIterator, otherIterator)
}

internal class Intersection<T>(
    private val leftIterator: Iterator<T>,
    private val rightIterator: Iterator<T>,
) : Iterable<T> {
    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            private val leftOrNull get() = if (leftIterator.hasNext()) leftIterator.next() else null
            private val rightOrNull get() = if (rightIterator.hasNext()) rightIterator.next() else null
            private val map = mutableMapOf<T, Int>()

            private var next: T? = null

            private var calledHasNext = false

            override fun hasNext(): Boolean {
                calledHasNext = true
                while (true) {
                    val curLeft = leftOrNull
                    curLeft?.let { if (put(it)) return true }
                    val curRight = rightOrNull
                    curRight?.let { if (put(it)) return true }
                    if (curLeft == null && curRight == null) return false
                }
            }

            private fun put(value: T): Boolean {
                val occ = map[value]
                return when (occ) {
                    null -> {
                        map[value] = 1
                        false
                    }

                    1 -> {
                        map.remove(value)
                        next = value
                        true
                    }

                    else -> error("unexpected")
                }
            }

            override fun next(): T {
                if (!calledHasNext) hasNext() // in case next() is called before hasNext()
                val value = next ?: throw NoSuchElementException()
                next = null
                return value
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

            override fun hasNext(): Boolean = thisIterator.hasNext() && otherIterator.hasNext()

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
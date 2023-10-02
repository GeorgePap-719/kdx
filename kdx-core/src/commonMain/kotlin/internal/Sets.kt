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
 * Returns a [Set] representing the symmetric difference,
 * i.e., the elements that are in `this` or in `other` but not in both, in arbitrary order.
 */
internal fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> {
    val symmetry = mutableSetOf<T>()
    for (item in this) {
        if (other.contains(item)) continue
        if (symmetry.contains(item)) continue
        symmetry.add(item)
    }
    for (item in other) {
        if (this.contains(item)) continue
        if (symmetry.contains(item)) continue
        symmetry.add(item)
    }
    return symmetry
}
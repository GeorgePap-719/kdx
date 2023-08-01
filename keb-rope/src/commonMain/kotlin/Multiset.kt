package keb.ropes

/**
 * Creates an "empty" [Subset] of a string with [length].
 */
fun Subset(length: Int): Subset {
    return Subset(listOf(Segment(length, 0)))
}

/**
 * Represents a [multi-subset](https://en.wiktionary.org/wiki/multisubset#English) of a string.
 * It is primarily used to efficiently represent inserted and deleted regions of a document.
 */
class Subset(val segments: List<Segment>) {
    init {
        // Invariant, maintained by `SubsetBuilder`: all `Segment`s have non-zero
        // length, and no `Segment` has the same count as the one before it.
        assert {
            checkForInvariants()
            true // workaround to use assert{}
        }
    }

    private fun checkForInvariants() {
        var prevCount = -1
        for (segment in segments) {
            require(segment.count != prevCount) { "Each segment must not have the same `count` as the one before it" }
            require(segment.length > 0) { "All segments should have non-zero length." }
            prevCount = segment.count
        }
    }

    /// The length of the resulting sequence after deleting this subset. A
    /// convenience alias for `self.count(CountMatcher::Zero)` to reduce
    /// thinking about what that means in the cases where the length after
    /// delete is what you want to know.
    ///
    /// `self.delete_from_string(s).len() = self.len(s.len())`
    fun lengthAfterDelete(): Int = count(CountMatcher.ZERO)

    /**
     * Returns the total length of this [Subset].
     * Convenient alias for [count(CountMatcher.ALL)][count].
     */
    fun length(): Int {
        var len = 0
        for (seg in segments) len += seg.length
        return len
    }

    /**
     * Counts the total length of all the segments matching [matcher].
     */
    fun count(matcher: CountMatcher): Int {
        var len = 0
        for (seg in segments) if (matcher.matches(seg)) len += seg.length
        return len
    }

    /**
     * Returns true if the subset is empty, false otherwise.
     * In this case, deleting it would do nothing.
     */
    fun isEmpty(): Boolean = segments.isEmpty() || (segments.size == 1 && segments.first().count == 0)

    /**
     * Computes the union of two [subsets][Subset].
     * The count of an element in the result is the sum of the counts in the inputs.
     */
    fun union(other: Subset): Subset = buildSubset {
        for (zseg in zip(other)) add(zseg.length, zseg.leftCount + zseg.rightCount)
    }

    fun zip(other: Subset): ZipIterator {
        return ZipIterator(this, other)
    }

    /// Map the contents of `self` into the 0-regions of `other`.
    /// Precondition: `self.count(CountMatcher::All) == other.count(CountMatcher::Zero)`
    fun transform(other: Subset, union: Boolean): Subset {
        return buildSubset {
            val iterator = segments.iterator()
            var curSeg = Segment(0, 0)
            for (otherSeg in other.segments) {
                if (otherSeg.count > 0) {
                    add(otherSeg.length, if (union) otherSeg.count else 0)
                } else {
                    // Fill 0-region with segments from `this`.
                    var consumable = otherSeg.length
                    while (consumable > 0) {
                        if (curSeg.length == 0) {
                            // `iterator.next()` should not throw `NoSuchElementException`,
                            // because this must cover all 0-regions of `other`.
                            curSeg = iterator.next()
                        }
                        val consumed = minOf(curSeg.length, consumable)
                        add(consumed, curSeg.count)
                        consumable -= consumed
                        curSeg = Segment(curSeg.length - consumed, curSeg.count)
                    }
                }
            }
            // The 0-regions of `other` must be the size of `this`.
            assert { curSeg.length == 0 }
            // The 0-regions of `other` must be the size of `this`.
            assert { !iterator.hasNext() }
        }
    }
}

enum class CountMatcher {
    ZERO,
    NON_ZERO,
    ALL;

    fun matches(segment: Segment): Boolean = when (this) {
        ZERO -> segment.count == 0
        NON_ZERO -> segment.count != 0
        ALL -> true
    }
}

class ZipSegment(val length: Int, val leftCount: Int, val rightCount: Int)

class ZipIterator(
    leftSubset: Subset,
    rightSubset: Subset,
    private var leftI: Int = 0,
    private var rightI: Int = 0,
    private var leftConsumed: Int = 0,
    private var rightConsumed: Int = 0,
    private var consumed: Int = 0
) : Iterator<ZipSegment> {
    init {
        require(leftSubset.segments.size == rightSubset.segments.size) {
            "Cannot zip Subsets with different lengths."
        }
    }

    private val leftSegments = leftSubset.segments
    private val rightSegments = rightSubset.segments

    override operator fun hasNext(): Boolean {
        if (leftSegments.getOrNull(leftI) != null && rightSegments.getOrNull(rightI) != null) return true
        if (leftSegments.getOrNull(leftI) == null && rightSegments.getOrNull(rightI) == null) return false
        error("Cannot zip Subsets with different lengths.")
    }

    //TODO: research this.
    override operator fun next(): ZipSegment {
        val left = leftSegments[leftI]
        val right = rightSegments[rightI]
        val result = (left.length + leftConsumed).compareTo(right.length + rightConsumed)
        val length = when {
            result == 0 -> {
                leftConsumed += left.length
                leftI++
                rightConsumed += right.length
                rightI++
                leftConsumed - consumed
            }

            result > 0 -> {
                leftConsumed += left.length
                leftI++
                leftConsumed - consumed
            }

            result < 0 -> {
                rightConsumed += right.length
                rightI++
                rightConsumed - consumed
            }

            else -> error("unexpected")
        }
        consumed += length
        return ZipSegment(length, left.count, right.count)
    }
}

class SubsetBuilder {
    private val segments: MutableList<Segment> = mutableListOf()
    private var totalLength: Int = 0

    /// Intended for use with `add_range` to ensure the total length of the
    /// `Subset` corresponds to the document length.
    fun paddingToLength(totalLength: Int) {
        val curLen = this.totalLength
        if (totalLength > curLen) add(Segment(totalLength - curLen, 0))
    }

    //TODO: research this.
    fun add(element: Segment) {
        require(element.length > 0) { "Cannot add empty segment." }
        totalLength += element.length
        // Merge into previous segment if possible.
        val last = segments.last()
        if (last.count == element.count) {
            segments[segments.lastIndex] = Segment(last.length + element.length, last.count)
        } else {
            segments.plus(element)
        }
    }

    /// Sets the count for a given range. This method must be called with a
    /// non-empty range with `begin` not before the largest range or segment added
    /// so far. Gaps will be filled with a 0-count segment.
    //TODO: research this.
    fun add(startIndex: Int, endIndex: Int, count: Int) {
        require(startIndex >= totalLength) { "Ranges must be added in non-decreasing order." }
        checkRangeIndexes(startIndex, endIndex)
        val len = endIndex - startIndex
        // Add 0-count segment to fill any gap.
        if (startIndex > totalLength) add(Segment(startIndex - totalLength, 0))
        add(Segment(len, count))
    }

    private fun checkRangeIndexes(startIndex: Int, endIndex: Int) {
        if (startIndex < 0) throw IndexOutOfBoundsException("startIndex:$startIndex")
        if (endIndex < startIndex) {
            throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
        }
    }


    fun build(): Subset = Subset(segments)
}

fun SubsetBuilder.add(length: Int, count: Int) {
    add(Segment(length, count))
}

fun buildSubset(action: SubsetBuilder.() -> Unit): Subset {
    val builder = SubsetBuilder()
    builder.action()
    return builder.build()
}

class Segment(
    /**
     * The length of the string which this segment represents.
     */
    val length: Int,
    /**
     * The number of times the character, which this segment represents, is included in the subset.
     */
    val count: Int
)
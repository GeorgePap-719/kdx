package keb.ropes

/**
 * Creates an empty [Subset] of a string with [length].
 */
fun Subset(length: Int): Subset {
    return buildSubset { paddingToLength(length) }
}

/**
 * Represents a [multi-subset](https://en.wiktionary.org/wiki/multisubset#English) of a string.
 * It is primarily used to efficiently represent inserted and deleted regions of a document.
 */
class Subset(val segments: List<Segment>) {
    init {
        /// Invariant, maintained by `SubsetBuilder`: all `Segment`s have non-zero
        /// length, and no `Segment` has the same count as the one before it.
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

    fun union(): Subset {
        TODO()
    }

    fun zip() {
        TODO()
    }
}

class ZipSegment(length: Int, leftCount: Int, rightCount: Int)

class ZipIterator(
    private val leftSubset: Subset,
    private val rightSubset: Subset,
    private val leftIndex: Int,
    private val rightIndex: Int,
    private var leftConsumed: Int,
    private var rightConsumed: Int,
    var consumed: Int
) : Iterator<ZipSegment> {
    init {
        require(leftSubset.segments.size == rightSubset.segments.size) {
            "Both segments must have equal size."
        }
    }

    private val leftIterator = leftSubset.segments.iterator()
    private val rightIterator = rightSubset.segments.iterator()

    override operator fun hasNext(): Boolean {
        val leftHasNext = leftIterator.hasNext()
        val rightHasNext = rightIterator.hasNext()
        when {
            leftHasNext && rightHasNext -> return true
            !leftHasNext && !rightHasNext -> return false
            leftHasNext && !rightHasNext || !leftHasNext && rightHasNext -> {
                error("Can't zip Subsets of different base lengths.")
            }
        }
        error("unexpected")
    }

    override operator fun next(): ZipSegment {
        val left = leftIterator.next()
        val right = rightIterator.next()
        when ((left.length + leftConsumed).compareTo(right.length + rightConsumed)) {
            0 -> {}
            1 -> {}
            -1 -> {}
            else -> error("unexpected")
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
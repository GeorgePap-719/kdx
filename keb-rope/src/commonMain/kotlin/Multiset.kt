package keb.ropes

/**
 * Represents a [multi-subset](https://en.wiktionary.org/wiki/multisubset#English) of a string.
 * It is primarily used to efficiently represent inserted and deleted regions of a document.
 *
 * For more details see [xi-editor Subset](https://xi-editor.io/docs/crdt-details.html#subset).
 */
class Subset internal constructor(private val segments: List<Segment>) {
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

    fun zip(other: Subset): Iterator<ZipSegment> {
        return ZipIterator(this, other)
    }

    /**
     * Maps the contents of [this][Subset] into the 0-regions of [other].
     * By extension, it is a precondition that the 0-regions of [other] to be of equal size with [this][Subset].
     *
     * When input [union] is true, the result is the same as [transformExpand]
     * and then taking the union with the "transform", but more efficient.
     *
     * @param union indicates if operation should preserve the non-zero segments of the "transform"
     *   instead of mapping them to 0-segments.
     * @param other the "transform" [Subset].
     */
    // Precondition: `self.count(CountMatcher::All) == other.count(CountMatcher::Zero)`
    fun transform(other: Subset, union: Boolean): Subset {
        return buildSubset {
            val iterator = segments.iterator()
            var curSeg = Segment(0, 0)
            for (transformSeg in other.segments) {
                if (transformSeg.count > 0) {
                    add(transformSeg.length, if (union) transformSeg.count else 0)
                } else {
                    // Fill 0-region with segments from `this`.
                    var consumable = transformSeg.length
                    while (consumable > 0) {
                        if (curSeg.length == 0) {
                            // `iterator.next()` should not throw `NoSuchElementException`,
                            // because this must cover all 0-regions of `other`.
                            curSeg = iterator.next()
                        }
                        // Consume as much of the segment as possible and necessary.
                        val consumed = minOf(curSeg.length, consumable)
                        add(consumed, curSeg.count)
                        consumable -= consumed
                        // Hypothesis:
                        // Since, we cannot know what `count` represents for this segment,
                        // we just copy it.
                        // It is associated with its length anyway,
                        // even if we break it into parts.
                        curSeg = Segment(curSeg.length - consumed, curSeg.count)
                    }
                }
            }
            // The 0-regions of `other` must be the size of `this`.
            assert { curSeg.length == 0 }
            assert { !iterator.hasNext() }
        }
    }

    private inner class ZipIterator(
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

        override operator fun hasNext(): Boolean = when {
            leftSegments.getOrNull(leftI) != null && rightSegments.getOrNull(rightI) != null -> true
            leftSegments.getOrNull(leftI) == null && rightSegments.getOrNull(rightI) == null -> false
            else -> error("Cannot zip Subsets with different lengths.")
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
}

/**
 * Creates an "empty" [Subset] of a string with [length].
 *
 * @throws IllegalArgumentException if length is less than or equal to zero.
 */
fun Subset(length: Int): Subset {
    require(length > 0) { "`length` cannot be zero. All segments should have non-zero length." }
    return Subset(listOf(Segment(length, 0)))
}

// The reverse of Subset::transform_expand.
// It takes a Subset and a transform Subset that are based on the same string
// and removes sections of the former that align with non-zero segments of the latter.
// In most uses these sections of the former always have count 0 (otherwise this transform would lose information),
// but there are some things like garbage collection that intentionally use this to discard information.
fun Subset.transformShrink(other: Subset): Subset = buildSubset {
    for (zseg in zip(other)) {
        // Discard `ZipSegments` where the shrinking set has positive count.
        if (zseg.rightCount == 0) {
            add(zseg.length, zseg.leftCount)
        }
    }
}

/**
 * Computes the union of two [subsets][Subset].
 * The count of a [Segment] in the result is the sum of the counts in the inputs.
 */
fun Subset.union(other: Subset): Subset = buildSubset {
    for (zseg in zip(other)) add(zseg.length, zseg.leftCount + zseg.rightCount)
}

/**
 * Like [transformExpand] except it preserves the non-zero segments of the transform
 * instead of mapping them to 0-segments (see [Subset.transform]).
 *
 * This function  is shorthand for:
 * ```kotlin
 *  val expandedSubset = transformExpand(b).union(b)
 * ```
 */
fun Subset.transformUnion(other: Subset): Subset = transform(other, true)

/**
 * "Expands" the indices in a [Subset] after each insert by the size of that insert,
 * where the inserted characters are the “transform”.
 *
 * Conceptually, if a [Subset] represents the set of characters in a string that were inserted by an edit,
 * then it can be used as a "transform" from the coordinate space before that edit to after that edit
 * by mapping a [Subset] of the string before the insertion onto the 0-count regions of the "transform" [Subset].
 *
 * One example of how this can be used is to find the characters
 * that were inserted by a past [Revision][todo] in the coordinates of the current union string instead of the past one.
 */
/// Transform through coordinate transform represented by other.
/// The equation satisfied is as follows:
///
/// s1 = other.delete_from_string(s0)
///
/// s2 = self.delete_from_string(s1)
///
/// element in self.transform_expand(other).delete_from_string(s0) if (not in s1) or in s2
fun Subset.transformExpand(other: Subset): Subset = transform(other, false)

/**
 * Computes the bitwise "xor" operation of two subsets,
 * useful as a reversible difference.
 *
 * The count of an element in the result is the bitwise "xor" of the counts of the inputs.
 * Unchanged segments will be 0.
 */
fun Subset.xor(other: Subset): Subset = buildSubset {
    for (zseg in zip(other)) add(zseg.length, zseg.leftCount.xor(zseg.rightCount))
}

/**
 * Returns an "empty" [Subset].
 */
//TODO: delete if not used
//internal fun emptySubset(): Subset = Subset(listOf())

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

class SubsetBuilder {
    private val segments: MutableList<Segment> = mutableListOf()
    private var totalLength: Int = 0

    /// Intended for use with `add(range)` to ensure the total length of the
    /// `Subset` corresponds to the document length.
    fun paddingToLength(totalLength: Int) {
        val curLen = this.totalLength
        if (totalLength > curLen) add(Segment(totalLength - curLen, 0))
    }

    /**
     * Adds the specified [element] to the end of this [Subset].
     * Before appending the [element], it tries to merge into the previous segment if it has the same count.
     *
     * @throws IllegalArgumentException if the [element] has empty length.
     */
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

    /**
     * Sets the count for the given range.
     * The "gaps" will be filled with a 0-count segment.
     *
     * @throws IllegalArgumentException if the [startIndex] is before the largest range or segment added so far.
     * @throws IllegalArgumentException if the given range is empty.
     */
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
    val count: Int
)
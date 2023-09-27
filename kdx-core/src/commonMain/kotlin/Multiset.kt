package kdx

/**
 * Represents a [multi-subset](https://en.wiktionary.org/wiki/multisubset#English) of a string.
 * It is primarily used to efficiently represent inserted and deleted regions of a document.
 *
 * For more details see [xi-editor Subset](https://xi-editor.io/docs/crdt-details.html#subset).
 */
class Subset internal constructor(private val segments: List<Segment>) {
    init {
        // Invariant, maintained by subset-builders: all `Segment`s have non-zero
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

    /**
     * The length of the resulting sequence after deleting this subset from the string.
     * It provides a convenient way to learn the length after the `delete`.
     *
     * This function is a shorthand for `count(CountMatcher.ZERO)` (see [count]).
     */
    fun lengthAfterDelete(): Int = count(CountMatcher.ZERO)

    /**
     * Returns the total length of this [Subset].
     *
     * This function is a shorthand for `count(CountMatcher.ALL)` (see [count]).
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
     * Maps the contents of [this][Subset] subset onto the 0-regions of the [transform] subset.
     * By extension, it is a precondition that the 0-regions of [transform] subset to be of equal size with [this][Subset] subset.
     * Otherwise, it throws [IllegalArgumentException].
     *
     * When input [union] is true, the result is the same as [transformExpand]
     * and then taking the union with the "transform", but more efficient.
     *
     * @param union indicates if operation should preserve the non-zero segments of the [transform]
     *   instead of mapping them to 0-segments.
     * @param transform the other "transform" [Subset].
     */
    fun transform(transform: Subset, union: Boolean): Subset {
        return buildSubset {
            val iterator = segments.iterator()
            var curSeg = Segment(0, 0)
            for (transformSeg in transform.segments) {
                if (transformSeg.count > 0) {
                    add(transformSeg.length, if (union) transformSeg.count else 0)
                } else {
                    // Fill 0-region with segments from `this`,
                    // until it fills its size.
                    var consumable = transformSeg.length
                    while (consumable > 0) {
                        if (curSeg.length == 0) {
                            // `iterator.next()` should not throw `IllegalArgumentException`,
                            // because this must cover all 0-regions of `other`.
                            curSeg = requireNotNull(iterator.nextOrNull()) { ZERO_REGIONS_MUST_BE_OF_EQUAL_SIZE }
                        }
                        val consumed = minOf(curSeg.length, consumable)
                        add(consumed, curSeg.count)
                        consumable -= consumed
                        // Length is associated with its `count`,
                        // that's why we copy the same count for all the remaining `length`.
                        curSeg = Segment(curSeg.length - consumed, curSeg.count)
                    }
                }
            }
            require(curSeg.length == 0 && !iterator.hasNext()) { ZERO_REGIONS_MUST_BE_OF_EQUAL_SIZE }
        }
    }

    private fun Iterator<Segment>.nextOrNull(): Segment? = if (hasNext()) next() else null

    /**
     * Returns an [Iterator] over `ZipSegments`
     * where each [ZipSegment] contains the count for both `this` and `other` subset in that range.
     * Both subsets must have the same "base" total length,
     * otherwise the iterator will throw [IllegalArgumentException].
     *
     * Note that each returned [ZipSegment] will differ in at least one of the two counts.
     */
    //TODO: future design improvement:
    // To be somewhat consistent with stdlib, zip() like operators should return a list.
    fun zip(other: Subset): Iterator<ZipSegment> = ZipIterator(this, other)

    private inner class ZipIterator(
        leftSubset: Subset,
        rightSubset: Subset,
        private var leftI: Int = 0,
        private var rightI: Int = 0,
        private var leftConsumed: Int = 0,
        private var rightConsumed: Int = 0,
        private var consumed: Int = 0
    ) : Iterator<ZipSegment> {
        private val leftSegments = leftSubset.segments
        private val rightSegments = rightSubset.segments

        override operator fun hasNext(): Boolean = when {
            leftSegments.getOrNull(leftI) != null && rightSegments.getOrNull(rightI) != null -> true
            leftSegments.getOrNull(leftI) == null && rightSegments.getOrNull(rightI) == null -> false
            else -> throw diffSubBaseLenException
        }

        /**
         * Consumes as far as possible from [consumed] until reaching a segment boundary in either [Subset],
         * and returns the resulting [ZipSegment].
         *
         * @throws IllegalArgumentException if the two subsets have different base lengths.
         */
        override operator fun next(): ZipSegment {
            val left = leftSegments.getOrNull(leftI) ?: throw diffSubBaseLenException
            val right = rightSegments.getOrNull(rightI) ?: throw diffSubBaseLenException
            val result = (left.length + leftConsumed).compareTo(right.length + rightConsumed)
            val length = when {
                result == 0 -> {
                    leftConsumed += left.length
                    leftI++
                    rightConsumed += right.length
                    rightI++
                    leftConsumed - consumed
                }

                result < 0 -> {
                    leftConsumed += left.length
                    leftI++
                    leftConsumed - consumed
                }

                result > 0 -> {
                    rightConsumed += right.length
                    rightI++
                    rightConsumed - consumed
                }

                else -> error("unexpected")
            }
            consumed += length
            return ZipSegment(length, left.count, right.count)
        }

        private val diffSubBaseLenException = IllegalArgumentException(DIFFERENT_SUBSETS_BASE_LENGTH_MESSAGE)
    }

    /**
     * Returns the complement of this [Subset].
     * Every 0-count element will have a count of one
     * and every non-zero element will have a count of 0.
     */
    fun complement(): Subset = buildSubset {
        for (seg in segments) {
            if (seg.count == 0) {
                add(seg.length, 1)
            } else {
                add(seg.length, 0)
            }
        }
    }

    fun rangeIterator(matcher: CountMatcher): RangeIterator {
        return RangeIterator(
            segments.iterator(),
            matcher,
            0
        )
    }

    override fun toString(): String = "Subset($segments)"
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

/**
 * Returns a [Mapper] that can be used to map "coordinates" in the document to coordinates in this [Subset].
 */
fun Subset.mapper(matcher: CountMatcher): Mapper {
    return Mapper(
        rangeIterator(matcher),
        0,
        0 to 0,
        0
    )
}

/**
 * Returns an [RangeIterator] that iterates through the "complement", that is the zero-segments.
 */
//TODO: this fun could be named better, e.g. zeroRangeIterator.
fun Subset.complementIterator(): RangeIterator = rangeIterator(CountMatcher.ZERO)

fun Subset.isNotEmpty(): Boolean = !isEmpty()

/**
 * "Shrinks" (transforms) [this] subset through the coordinate transform represented by the [transform].
 * Both subsets must be based on the same string.
 * This function removes sections of [this] subset that align with the non-zero segments of the [transform].
 * In most cases, these sections (of `this`) always have count zero (otherwise this transform would lose information),
 * but there are some cases like garbage-collection that intentionally uses this to discard information.
 *
 * This function is the reverse of [transformExpand].
 */
fun Subset.transformShrink(transform: Subset): Subset = buildSubset {
    for (zseg in zip(transform)) {
        // Discard `ZipSegments` that align with transform's non-zero `count` (leftCount).
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
 * Transforms [this] subset through the coordinate transform represented by the [transform].
 *
 * Like [transformExpand] except it preserves the non-zero segments of the [transform]
 * instead of mapping them to 0-segments (see [Subset.transform]).
 *
 * This function is shorthand for:
 * ```kotlin
 *  val expandedSubset = transformExpand(b).union(b)
 * ```
 */
fun Subset.transformUnion(transform: Subset): Subset = transform(transform, true)

/**
 * Transforms [this] subset through the coordinate transform represented by the [transform].
 * This is achieved by "expanding" the indices in a [Subset] after each insert by the size of that insert,
 * where the inserted characters are the “transform”.
 *
 * Conceptually, if a [Subset] represents the set of characters in a string that were inserted by an edit,
 * then it can be used as a "transform" from the coordinate space before that edit to after that edit
 * by mapping a [Subset] of the string before the insertion onto the 0-count regions of the "transform" [Subset].
 *
 * One example of how this can be used is to find the characters
 * that were inserted by a past [Revision] in the coordinates of the current union string instead of the past one.
 */
fun Subset.transformExpand(transform: Subset): Subset = transform(transform, false)

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
 * Computes the difference of two subsets.
 *
 * The `count` of an element in the result is the subtraction of the counts of `other` from `this`.
 */
fun Subset.subtract(other: Subset): Subset = buildSubset {
    for (zseg in zip(other)) {
        // Otherwise, it cannot subtract from `other`.
        assert { zseg.leftCount >= zseg.rightCount }
        add(zseg.length, zseg.leftCount - zseg.rightCount)
    }
}

/**
 * Returns an "empty" [Subset], which is an **invariant**.
 * Used in our own operations where we trust the context of usage.
 */
internal fun emptySubset(): Subset = Subset(listOf())

// Used in testing.
internal fun Subset.deleteFromString(input: String): String {
    val sb = StringBuilder()
    val iterator = rangeIterator(CountMatcher.ZERO)
    while (iterator.hasNext()) {
        val next = iterator.next() ?: continue
        sb.append(input.subSequence(next.first, next.second))
    }
    return sb.toString()
}

/**
 * Builds a version of [node] with all the elements in this [Subset] removed from it.
 */
// Actually, this is a builder for btree,
// and probably makes more sense for the btree
// to be the receiver.
fun <T : NodeInfo> Subset.deleteFrom(node: BTreeNode<T>): BTreeNode<T> = buildBTree {
    val iterator = rangeIterator(CountMatcher.ZERO)
    for (range in iterator) {
        val (start, end) = range ?: continue
        add(node, start..<end)
    }
}

// Second represents the current length we have consumed.
// First represents the current length without the current step.
// The difference between second and first can give us the current step.
private typealias Range = Pair<Int, Int>

internal val Range.step: Int get() = second - first
internal val Range.curLen: Int get() = second
internal val Range.prevLen: Int get() = first

//TODO: this can be redesigned to avoid false-positives from hasNext().
class RangeIterator(
    private val segmentIterator: Iterator<Segment>,
    private val matcher: CountMatcher,
    consumed: Int
) : Iterator<Range?> {
    var consumed: Int = consumed
        private set

    override operator fun hasNext(): Boolean = segmentIterator.hasNext()

    /**
     * Returns the next item in iteration.
     * This function allows a nullable result because even though [hasNext] might return `true`
     * it is not a guarantee that this will yield an item.
     * The result is based on the provided [matcher].
     */
    override operator fun next(): Range? {
        for (segment in segmentIterator) {
            consumed += segment.length
            if (matcher.matches(segment)) return consumed - segment.length to consumed
        }
        return null
    }
}

/**
 * A mapper that maps a coordinate in the document this subset corresponds to,
 * to a coordinate in the given subset matched by [CountMatcher] in [RangeIterator].
 *
 * In order to guarantee good performance, [documentIndexToSubset]
 * must be called with `index` values in non-decreasing order.
 * This allows the total const to be `O(n)` where `n = max(calls,ranges)` over all times called on a single [Mapper].
 */
class Mapper(
    private val rangeIterator: RangeIterator,
    /**
     * It is used to validate the non-decreasing order.
     */
    private var lastIndex: Int,
    private var curRange: Range,
    subsetAmountConsumed: Int
) {
    private var curIndex = subsetAmountConsumed

    @Suppress("unused") //TODO: delete if it stays unused.
    val subsetAmountConsumed: Int get() = curIndex

    /**
     * Maps the given [index] (coordinate) in the document,
     * to a coordinate in the subset matched by the [CountMatcher] in [RangeIterator].
     * For example, if the subset represents a set of "deletions" and the matcher is [CountMatcher.NON_ZERO],
     * this function would map indices in the "union string" to indices in the "tombstones" string.
     *
     * If the [index] is not in the subset, it returns the closest coordinate in the subset.
     * If the [index] (coordinate) is past the end of the subset,
     * it will return one more than the largest index in the subset (i.e. the length).
     * This behavior is suitable for mapping closed-open ranges in a string to ranges in a subset of the string.
     *
     * @throws IllegalArgumentException if [index] is not provided in non-decreasing order, for performance reasons.
     */
    fun documentIndexToSubset(index: Int): Int {
        require(index >= lastIndex) {
            "index must be in non-decreasing order, but got:$index, with lastIndex:$lastIndex"
        }
        lastIndex = index
        while (index >= curRange.curLen) {
            curIndex += curRange.step
            val nextRange = rangeIterator.next()
            // Target [index] is past the end of this subset.
            if (nextRange == null) {
                // Insert a flag like value,
                // to ensure we do not parse again.
                curRange = Int.MAX_VALUE to Int.MAX_VALUE
                // Return the "largest" index,
                // i.e. the "curIndex".
                return curIndex
            }
            curRange = nextRange
        }
        // If `index >= curRange.prevLen` is not true,
        // it means [RangeIterator.next] moved more than two steps.
        return if (index >= curRange.prevLen) {
            val distanceInRange = index - curRange.prevLen
            distanceInRange + curIndex
        } else {
            // Not in the subset,
            // return the closest index.
            curIndex
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

// This builder, by design, fills any gaps with "zero" count segments,
// which means it can be used in cases where we know there are not "deletes" in the target "ranges".
class SubsetBuilder {
    private val segments: MutableList<Segment> = mutableListOf()
    private var totalLength: Int = 0

    /**
     * Ensures the total length of this [Subset] corresponds to the document's [length].
     * It's typically used along with `add(startIndex, endIndex, count)`.
     */
    fun growLengthIfNeeded(length: Int) {
        val curLen = this.totalLength
        if (length > curLen) add(Segment(length - curLen, 0))
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
        val last = segments.lastOrNull()
        // Merge into previous segment if possible.
        if (last?.count == element.count) {
            segments[segments.lastIndex] = Segment(last.length + element.length, last.count)
        } else {
            segments.add(element)
        }
    }

    /**
     * Adds a [Segment] with the given range being the length, to the end of this [Subset].
     * The "gaps" will be filled with a 0-count segment.
     * Technically, this function "sets" the count for the given range.
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

/**
 * Adds a [Segment] with the given [length] and [count], to the end of this [Subset].
 * Technically, it assigns [count] to the next [length] elements in the [Subset].
 *
 * This function is a shorthand for `add(Segment(length, count))` (see [SubsetBuilder.add]).
 *
 * @throws IllegalArgumentException if the [length] is empty.
 */
fun SubsetBuilder.add(length: Int, count: Int) {
    add(Segment(length, count))
}

fun buildSubset(action: SubsetBuilder.() -> Unit): Subset {
    val builder = SubsetBuilder()
    builder.action()
    return builder.build()
}

/**
 * Each segment has a count representing how many times is in the [Subset].
 *
 * **NB** If the [Subset] represents the "deletes" in a string,
 * then the count represents the presence or absence in the final string.
 * For example, count "one" indicates the absence (as it exists in the subset) and count "zero" indicates the presence
 * (as it does not exist in the subset).
 */
data class Segment(
    /**
     * The length of the string which this segment represents.
     */
    val length: Int,
    /**
     * The "count" of this segment.
     * Zero marks the absence in the set and one marks the presence.
     * It can also have a value of greater than one.
     */
    val count: Int
)

// Error message for [ZipIterator].
private const val DIFFERENT_SUBSETS_BASE_LENGTH_MESSAGE = "Cannot zip Subsets with different base lengths."

// Error message for transform() operation.
private const val ZERO_REGIONS_MUST_BE_OF_EQUAL_SIZE =
    "the 0-regions of the `other` subset must be of equal size with `this` subset"
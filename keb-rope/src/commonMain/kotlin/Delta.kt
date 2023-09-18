package keb.ropes

import keb.ropes.internal.intoInterval

internal typealias NodeInfo = LeafInfo

/**
 * Represents changes to a document by describing the new document as a sequence of sections copied from the base document and of new inserted text.
 * Deletions are represented by gaps in the ranges copied from the old document.
 * All the indices of the copied sections are non-decreasing.
 *
 * For example, Editing "abcd" into "acde" could be represented as:
 * `[Copy(0,1),Copy(2,4),Insert("e")]`
 *
 * See [xi-editor Delta](https://xi-editor.io/docs/crdt-details.html#delta), for more details.
 */
interface Delta<T : NodeInfo> {
    /**
     * The sequence that describes the new document.
     */
    val changes: List<DeltaElement<T>>

    /**
     * The total length of the **base document**.
     * It is typically used for validations in operations.
     */
    val baseLength: Int
}

/**
 * Returns `true` if applying this [Delta] will cause no change.
 */
val <T : NodeInfo> Delta<T>.isIdentity: Boolean
    get() {
        val len = changes.size
        // Case 1: Everything from beginning to end is getting copied.
        if (len == 1) {
            val element = changes.first() as? Copy
            element?.let { return element.startIndex == 0 && element.endIndex == baseLength }
        }
        // Case 2: The rope is empty
        // and the entire rope is getting deleted.
        return len == 0 && baseLength == 0
    }

/**
 * Apply this [Delta] to the given [node].
 *
 * Note: May not work well if the length of the node
 * is not compatible with the construction of the delta.
 */
fun <T : NodeInfo> Delta<T>.applyTo(node: BTreeNode<T>): BTreeNode<T> {
    assert { node.weight == baseLength } //TODO: should this be require?
    return buildBTree {
        for (element in changes) {
            when (element) {
                is Copy -> add(node, IntRange(element.startIndex, element.endIndex))
                is Insert -> add(element.input)
            }
        }
    }
}

/**
 * "Factor" the delta into an [InsertDelta] and a [Subset] representing deletions.
 * Applying the [InsertDelta] and then the deletions, yields the same result as the [original][this] [Delta].
 */
fun <T : NodeInfo> Delta<T>.factor(): Pair<InsertDelta<T>, Subset> {
    val insertions = mutableListOf<DeltaElement<T>>()
    val subsetBuilder = SubsetBuilder()
    // Tracks the offset of copied ranges against `endIndex`.
    var startIndex = 0
    // Tracks the document's length.
    var endIndex = 0
    for (element in changes) {
        when (element) {
            // `Delta` represents "deletes" as gaps in the ranges copied from the old document.
            // That's why Copy-branch is the only place we can create the subset of "deletes".
            is Copy -> {
                // If there are "deletes" `element.startIndex` will be greater than `endIndex`.
                // Since, `endIndex` is getting updated by each copy-element.
                if (element.startIndex > endIndex) subsetBuilder.add(endIndex, element.startIndex, 1)
                endIndex = element.endIndex
            }

            is Insert -> {
                // We only need to know the traversed length
                // to copy the characters in delta.
                if (endIndex > startIndex) {
                    // We track the "deletes" through the subset,
                    // therefore, we can simply add all ranges as `Copy` elements,
                    // and then add the `Insert` element.
                    insertions.add(Copy(startIndex, endIndex))
                }
                startIndex = endIndex
                insertions.add(Insert(element.input))
            }
        }
    }
    // Iterate one more time to the end of the document,
    // as last-element might be a `Copy`, or there might be "gaps" in the tail.
    if (startIndex < baseLength) insertions.add(Copy(startIndex, baseLength))
    // Similarly as above, check for "gaps" in the tail.
    if (endIndex < baseLength) subsetBuilder.add(endIndex, baseLength, 1)
    // Grow builder to match document's length.
    subsetBuilder.growLengthIfNeeded(baseLength)
    val deletes = subsetBuilder.build()
    return InsertDelta(insertions, baseLength) to deletes
}

/**
 * Creates a [Delta] representing an edit.
 * The typical use-case is to apply this [Delta] through [applyTo].
 */
//TODO: rename to edit()?
internal fun <T : NodeInfo> simpleEdit(
    /* The range which the edit will be applied on. */
    range: IntRange,
    node: BTreeNode<T>,
    /* The length of the editing document. */
    baseLength: Int
): Delta<T> = buildDelta(baseLength) {
    if (node.isEmpty) {
        delete(range)
    } else {
        replace(range, node)
    }
}

/// Do a coordinate transformation on an insert-only delta. The `after` parameter
/// controls whether the insertions in `this` come after those specific in the
/// coordinate transform.
fun <T : NodeInfo> InsertDelta<T>.transformExpand(xform: Subset, after: Boolean): InsertDelta<T> {
    val curChanges = changes
    val changes = mutableListOf<DeltaElement<T>>()
    var x = 0 // coordinate within self
    var y = 0 // coordinate within xform
    var i = 0 // index into `curChanges`
    var b1 = 0
    val xformRanges = xform.complementIterator()
    var lastXform = xformRanges.next()
    val len = xform.length()
    while (y < len || i < curChanges.size) {
        val nextIvBeg = lastXform?.first ?: len
        if (after && y < nextIvBeg) y = nextIvBeg
        while (i < curChanges.size) {
            when (val content = curChanges[i]) {
                is Copy -> {
                    if (y >= nextIvBeg) {
                        var nextY = content.endIndex + y - x
                        lastXform?.second?.let { nextY = minOf(nextY, it) }
                        x += nextY - y
                        y = nextY
                        if (x == content.endIndex) i++
                        lastXform?.second?.let { if (y == it) lastXform = xformRanges.next() }
                    }
                    break
                }

                is Insert -> {
                    if (y > b1) changes.add(Copy(b1, y))
                    b1 = y
                    changes.add(Insert(content.input))
                    i++
                }
            }
        }
        if (!after && y < nextIvBeg) y = nextIvBeg
    }
    if (y > b1) changes.add(Copy(b1, y))
    return InsertDelta(changes, len)
}


/// Shrink a delta through a deletion of some of its copied regions with
/// the same base. For example, if `self` applies to a union string, and
/// `xform` is the deletions from that union, the resulting Delta will
/// apply to the text.
fun <T : NodeInfo> InsertDelta<T>.transformShrink(xform: Subset): InsertDelta<T> {
    val mapper = xform.mapper(CountMatcher.ZERO)
    val changes = changes.map {
        when (val content = it) {
            is Copy -> Copy(
                mapper.documentIndexToSubset(content.startIndex),
                mapper.documentIndexToSubset(content.endIndex)
            )

            is Insert -> Insert(content.input)
        }
    }
    return InsertDelta(changes, xform.lengthAfterDelete())
}

/**
 * Returns a [Subset] containing the inserted ranges.
 */
fun <T : NodeInfo> InsertDelta<T>.getInsertedSubset(): Subset = buildSubset {
    for (change in changes) {
        when (change) {
            is Copy -> add(change.endIndex - change.startIndex, 0)
            // Inserted characters do not exist in the base document.
            // That's why they are represented as "deletes".
            is Insert -> add(change.input.weight, 1)
        }
    }
}

/**
 * Represents a [Delta] that contains only insertions.
 * That is, it copies all of the `base` document in the same order, and then new insertions follow.
 */
class InsertDelta<T : NodeInfo>(override val changes: List<DeltaElement<T>>, override val baseLength: Int) : Delta<T> {
    override fun toString(): String = "InsertDelta(changes=$changes,baseLength=$baseLength)"
}

/**
 * Type for a sequence in a [Delta].
 */
sealed class DeltaElement<out T : NodeInfo>

/**
 * Represents a range in the base document.
 */
class Copy(val startIndex: Int, val endIndex: Int /*`endIndex` exclusive*/) : DeltaElement<Nothing>() {
    override fun toString(): String = "Copy(startIndex=$startIndex,endIndex=$endIndex)"
}

/**
 * Represents an insert in the new document.
 */
class Insert<T : NodeInfo>(val input: BTreeNode<T>) : DeltaElement<T>() {
    override fun toString(): String = "Insert(input=$input)"
}

fun <T : LeafInfo> buildDelta(baseLen: Int, action: DeltaBuilder<T>.() -> Unit): Delta<T> {
    val builder = DeltaBuilder<T>(baseLen)
    builder.action()
    return builder.build()
}

/**
 * A builder for creating new deltas.
 *
 * Note that all edit operations must be sorted;
 * the start point of each range must be no less than the end point of the previous one.
 */
class DeltaBuilder<T : LeafInfo> internal constructor(baseLen: Int) {
    private var delta: Delta<T> = DeltaSupport(listOf(), baseLen)
    private var lastOffset = 0

    fun delete(range: IntRange) {
        val closedOpenRange = range.intoInterval(delta.baseLength)
        val start = closedOpenRange.first
        val end = closedOpenRange.last + 1 // + 1 because it represents closed-**open** range
        require(start >= lastOffset) { "ranges are not sorted properly" }
        if (start > lastOffset) addIntoDeltaElements(Copy(lastOffset, start))
        lastOffset = end
    }

    fun replace(range: IntRange, node: BTreeNode<T>) {
        delete(range)
        if (node.isNotEmpty) addIntoDeltaElements(Insert(node))
    }

    fun isEmpty(): Boolean {
        return lastOffset == 0 && delta.changes.isEmpty()
    }

    fun build(): Delta<T> {
        if (lastOffset < delta.baseLength) addIntoDeltaElements(Copy(lastOffset, delta.baseLength))
        return delta
    }

    private fun addIntoDeltaElements(element: DeltaElement<T>) {
        val newChanges = delta.changes.plus(element)
        delta = DeltaSupport(newChanges, delta.baseLength)
    }
}

internal open class DeltaSupport<T : NodeInfo>(
    override val changes: List<DeltaElement<T>>,
    override val baseLength: Int
) : Delta<T> {
    override fun toString(): String = "DeltaSupport(changes=$changes,baseLength=$baseLength)"
}

internal typealias DeltaRopeNode = Delta<RopeLeaf>
package keb.ropes

internal typealias NodeInfo = LeafInfo

/**
 * Represents changes to a document by describing the new document as a sequence of sections copied from the old document and of new inserted text.
 * Deletions are represented by gaps in the ranges copied from the old document.
 *
 * For example, Editing "abcd" into "acde" could be represented as:
 * `[Copy(0,1),Copy(2,4),Insert("e")]`
 *
 * See [xi-editor Delta](https://xi-editor.io/docs/crdt-details.html#delta), for more details.
 */
//TODO: improve kdoc
interface Delta<T : NodeInfo> {
    val changes: List<DeltaElement<T>>

    // The total length of the base document,
    // used for checks in some operations.
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
    assert { node.weight == baseLength }
    return buildBTree {
        for (element in changes) {
            when (element) {
                is Copy -> add(node, IntRange(element.startIndex, element.endIndex))
                is Insert -> add(element.input)
            }
        }
    }
}

/// Factor the delta into an insert-only delta and a subset representing deletions.
/// Applying the insert then the delete yields the same result as the original delta:
///
/// ```no_run
/// # use xi_rope::rope::{Rope, RopeInfo};
/// # use xi_rope::delta::Delta;
/// # use std::str::FromStr;
/// fn test_factor(d : &Delta<RopeInfo>, r : &Rope) {
///     let (ins, del) = d.clone().factor();
///     let del2 = del.transform_expand(&ins.inserted_subset());
///     assert_eq!(String::from(del2.delete_from(&ins.apply(r))), String::from(d.apply(r)));
/// }
/// ```
fun <T : NodeInfo> Delta<T>.factor(): Pair<InsertDelta<T>, Subset> {
    val insertions = mutableListOf<DeltaElement<T>>()
    val subset = buildSubset {
        var b1 = 0
        var e1 = 0
        for (element in changes) {
            when (element) {
                is Copy -> {
                    add(e1, element.startIndex, 1)
                    e1 = element.endIndex
                }

                is Insert -> {
                    if (e1 > b1) insertions.add(Copy(b1, e1))
                    b1 = e1
                    insertions.add(Insert(element.input))
                }
            }
        }
        if (b1 < baseLength) insertions.add(Copy(b1, baseLength))
        // Add only non-empty ranges.
        if (e1 < baseLength) add(e1, baseLength, 1)
        paddingToLength(baseLength)
    }
    return InsertDelta(DeltaSupport(insertions, baseLength)) to subset
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
    return InsertDelta(DeltaSupport(changes, len))
}


/// Shrink a delta through a deletion of some of its copied regions with
/// the same base. For example, if `self` applies to a union string, and
/// `xform` is the deletions from that union, the resulting Delta will
/// apply to the text.
fun <T : NodeInfo> InsertDelta<T>.transformShrink(xform: Subset): InsertDelta<T> {
    val mapper = xform.mapper(CountMatcher.ZERO)
    val changes = changes.map {
        when (val content = it) {
            is Copy -> Copy(mapper.docIndexToSubset(content.startIndex), mapper.docIndexToSubset(content.endIndex))
            is Insert -> Insert(content.input)
        }
    }
    return InsertDelta(DeltaSupport(changes, xform.lengthAfterDelete()))
}

/**
 * Returns a [Subset] containing the inserted ranges.
 */
// `d.inserted_subset().delete_from_string(d.apply_to_string(s)) == s`
fun <T : NodeInfo> InsertDelta<T>.getInsertedSubset(): Subset = buildSubset {
    for (change in changes) {
        when (change) {
            is Copy -> add(change.endIndex - change.startIndex, 0)
            is Insert -> add(change.input.weight, 1)
        }
    }
}

/**
 * Represents a [Delta] that contains only insertions.
 * That is, it copies all of the `old` document in the same order.
 *
 * It is a `Deref` implementation to allow all [Delta] operations on it.
 */
class InsertDelta<T : NodeInfo>(val value: Delta<T>) : Delta<T> {
    override val changes: List<DeltaElement<T>> = value.changes
    override val baseLength: Int = value.baseLength
}

sealed class DeltaElement<out T : NodeInfo>

// Represents a range of text in the base document.
class Copy(val startIndex: Int, val endIndex: Int /*`endIndex` exclusive*/) : DeltaElement<Nothing>()
class Insert<T : NodeInfo>(val input: BTreeNode<T>) : DeltaElement<T>()

open class DeltaSupport<T : NodeInfo>(
    override val changes: List<DeltaElement<T>>,
    override val baseLength: Int
) : Delta<T>

fun <T : LeafInfo> buildDelta(baseLen: Int, action: DeltaBuilder<T>.() -> Unit): Delta<T> {
    val builder = DeltaBuilder<T>(baseLen)
    builder.action()
    return builder.build()
}

class DeltaBuilder<T : LeafInfo> internal constructor(baseLen: Int) {
    private var delta: Delta<T> = DeltaSupport(listOf(), baseLen)
    private var lastOffset = 0

    fun delete(range: IntRange) {
        val start = range.first
        val end = delta.baseLength
        // Intervals are not properly sorted.
        assert { start >= lastOffset }
        if (start > lastOffset) addIntoDeltaElements(Copy(lastOffset, start))
        lastOffset = end
    }

    // A bit of weird signature,
    // even though everything is `T`,
    // here we acknowledge the fact that t is a [Rope].
    // Fix: cannot work like it was,
    // cause of generics doing their work properly.
    fun replace(range: IntRange, node: BTreeNode<T>) {
        delete(range)
        if (node.isEmpty) addIntoDeltaElements(Insert(node))
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

internal typealias DeltaRopeNode = Delta<RopeLeaf>

class DeltaRope(override val changes: List<DeltaElement<RopeLeaf>>, override val baseLength: Int) : DeltaRopeNode
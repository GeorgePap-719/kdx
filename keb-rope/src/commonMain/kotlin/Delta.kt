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
    val baseLen: Int
}

/**
 * Apply this [Delta] to the given [node].
 *
 * Note: May not work well if the length of the node
 * is not compatible with the construction of the delta.
 */
fun <T : NodeInfo> Delta<T>.applyTo(node: BTreeNode<T>): BTreeNode<T> {
    assert { node.weight == baseLen }
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
        if (b1 < baseLen) insertions.add(Copy(b1, baseLen))
        add(e1, baseLen, 1)
        paddingToLength(baseLen)
    }
    return InsertDelta(DeltaSupport(insertions, baseLen)) to subset
}

/**
 * Represents a [Delta] that contains only insertions.
 * That is, it copies all of the `old` document in the same order.
 *
 * It is a `Deref` implementation to allow all [Delta] operations on it.
 */
class InsertDelta<T : NodeInfo>(val value: Delta<T>) : Delta<T> {
    override val changes: List<DeltaElement<T>> = value.changes
    override val baseLen: Int = value.baseLen
}

sealed class DeltaElement<out T : NodeInfo>

// Represents a range of text in the base document.
class Copy(val startIndex: Int, val endIndex: Int /*`endIndex` exclusive*/) : DeltaElement<Nothing>()
class Insert<T : NodeInfo>(val input: BTreeNode<T>) : DeltaElement<T>()

open class DeltaSupport<T : NodeInfo>(
    override val changes: List<DeltaElement<T>>,
    override val baseLen: Int
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
        val end = delta.baseLen
        // intervals not properly sorted
        assert { start >= lastOffset }
        if (start > lastOffset) addIntoDeltaElements(Copy(lastOffset, start))
        lastOffset = end
    }

    // A bit of weird signature,
    // even though everything is `T`
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
        if (lastOffset < delta.baseLen) addIntoDeltaElements(Copy(lastOffset, delta.baseLen))
        return delta
    }

    private fun addIntoDeltaElements(element: DeltaElement<T>) {
        val newChanges = delta.changes.plus(element)
        delta = DeltaSupport(newChanges, delta.baseLen)
    }
}

private typealias DeltaRopeNode = Delta<RopeLeaf>

class DeltaRope(override val changes: List<DeltaElement<RopeLeaf>>, override val baseLen: Int) : DeltaRopeNode
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
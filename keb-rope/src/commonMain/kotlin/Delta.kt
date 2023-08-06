package keb.ropes

private typealias NodeInfo = LeafInfo

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

    // The total length of the base document, used for checks in some operations
    val baseLen: Int
}

sealed class DeltaElement<T : NodeInfo>

// Represents a range of text in the base document.
class Copy(val startIndex: Int, val endIndex: Int /*`endIndex` exclusive*/) : DeltaElement<Nothing>()
class Insert<T : NodeInfo>(input: BTreeNode<T>) : DeltaElement<T>()

class DeltaRope(override val changes: List<DeltaElement<RopeLeaf>>, override val baseLen: Int) : Delta<RopeLeaf> {
    //TODO:
}
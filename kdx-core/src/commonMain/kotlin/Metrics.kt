package kdx

import kdx.btree.BTreeNode
import kdx.btree.LeafInfo

/**
 * Based on blog post [Rope science, part 2: metrics](https://xi-editor.io/docs/rope_science_02.html).
 */
interface Metric<T : BTreeNode<R>, R : LeafInfo> {
    fun measure(): Int
    val isBoundary: Boolean
    fun prev(): Int?
    fun next(): Int?
    fun canFragment(): Boolean
}
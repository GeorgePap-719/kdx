package keb.ropes

/**
 * Based on [blog post, Rope science, part 2: metrics](https://github.com/google/xi-editor/blob/master/docs/docs/rope_science_02.md).
 */
interface Metric<T : BTreeNode<R>, R : LeafInfo> {
    fun measure(): Int
    val isBoundary: Boolean
    fun prev(): Int?
    fun next(): Int?
    fun canFragment(): Boolean
}
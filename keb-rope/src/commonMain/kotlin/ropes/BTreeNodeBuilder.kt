package keb.ropes

// Notes: maybe we need a builder rather than a mutable version.
// After all, in `delete` operations we cannot avoid rebuilding the tree in each step (I think).

//internal class BTreeNodeBuilder(private var root: BTreeNode) {
//    val weight: Int get() = root.weight
//    val height: Int get() = root.height
//    val isLegal: Boolean get() = root.isLegal
//    val isEmpty: Boolean get() = root.isEmpty
//
//    fun build(): BTreeNode = root
//}
package keb.ropes

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

open class Rope(value: String) {
    private val root: AtomicRef<RootBTreeNode> = atomic(RootBTreeNode())

}

/**
 * Represents a self-balancing tree node.
 */
open class BTreeNode(
    val value: String? = null,
    left: BTreeNode? = null,
    right: BTreeNode? = null
) {
    val left: AtomicRef<BTreeNode?> = atomic(left)
    val right: AtomicRef<BTreeNode?> = atomic(right)

    val isLeafNode: Boolean get() = left.value == null && right.value == null
}

class RootBTreeNode : BTreeNode()

class LeafBTreeNode(value: String) : BTreeNode(value)

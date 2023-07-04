package keb.ropes

sealed interface PersistentBTreeNode : BTreeNode

/**
 * Adds the [other] tree to the right side of this tree, and creates a new balanced btree.
 */
operator fun PersistentBTreeNode.plus(other: PersistentBTreeNode): PersistentInternalNode = merge(this, other)

/**
 * Checks if tree needs rebalancing and rebuilds it from the bottom-up.
 * In case it is balanced, then it returns the same tree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
fun PersistentBTreeNode.rebalance(): PersistentBTreeNode {
    if (isBalanced()) return this
    val leaves = this.mapNotNull { if (it.isEmpty) null else it }
    return merge(leaves)
}


class PersistentLeafNode(override val value: String) : LeafNode(), PersistentBTreeNode {
    override val weight: Int = value.length
    override val isEmpty: Boolean = value.isEmpty()
    override val isLegal: Boolean = weight <= MAX_SIZE_LEAF
}

class PersistentInternalNode(
    override val weight: Int,
    override val height: Int,
    override val children: List<BTreeNode>
) : InternalNode(), PersistentBTreeNode {
    override val isLegal: Boolean = super.isLegal // compute it once

    /**
     * Returns a new expanded [node][InternalNode] by a factor of 2.
     * This operation splits the tree in half and creates a new parent node for them.
     *
     * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
     */
    fun expand(): InternalNode {
        if (children.size == 1) return this
        val half = children.size / 2
        val left = children.subList(0, half)
        val right = children.subList(half, children.size)
        val leftParent = merge(left)
        val rightParent = merge(right)
        return merge(leftParent, rightParent) //TODO: check if we better use merge or unsafe merge
    }

    /**
     * Adds the [other] tree to the right side of this tree, and creates a new balanced btree.
     *
     * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
     */
    operator fun plus(other: PersistentInternalNode): PersistentInternalNode = merge(this, other)

    /**
     * Returns a new [node][InternalNode] with the specified [child] inserted at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is greater than or equal to the maximum size of children.
     * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
     */
    fun add(index: Int, child: BTreeNode): InternalNode {
        checkElementIndex(index)
        require(children.size + 1 <= MAX_CHILDREN) { "node cannot hold more than:$MAX_CHILDREN children" }
        val newChildren = children.addWithCopyOnWrite(child, index)
        return unsafeCreateParent(newChildren)
    }

    fun addLast(child: BTreeNode): InternalNode = add(children.size - 1, child)

    fun addFirst(child: BTreeNode): InternalNode = add(0, child)

    fun addAll(index: Int, children: List<BTreeNode>): InternalNode {
        checkElementIndex(index)
        require(this.children.size + children.size <= MAX_CHILDREN) {
            "node cannot hold more than:$MAX_CHILDREN children"
        }
        val newChildren = this.children.addWithCopyOnWrite(children, index)
        return unsafeCreateParent(newChildren)
    }

    operator fun set(index: Int, child: BTreeNode): InternalNode {
        checkElementIndex(index)
        val newChildren = buildList {
            for (i in children.indices) {
                if (i == index) add(child) else add(children[i])
            }
        }
        return unsafeCreateParent(newChildren)
    }

    operator fun set(index: Int, children: List<BTreeNode>): InternalNode {
        checkElementIndex(index)
        require(this.children.size - 1 + children.size <= MAX_CHILDREN) {
            "node cannot hold more than:$MAX_CHILDREN children"
        }
        val childNodes = this.children
        val newChildren = buildList {
            for (i in childNodes.indices) {
                if (index == i) addAll(children) else add(childNodes[i])
            }
        }
        return unsafeCreateParent(newChildren)
    }

    fun replace(old: BTreeNode, new: BTreeNode): InternalNode {
        val newChildren = buildList {
            for (child in children) {
                if (child === old) add(new) else add(child)
            }
        }
        return unsafeCreateParent(newChildren)
    }

    fun deleteAt(index: Int): InternalNode? {
        checkElementIndex(index)
        val newChildren = buildList {
            for (i in children.indices) {
                if (i == index) continue
                add(children[i])
            }
        }
        if (newChildren.isEmpty()) return null
        return unsafeCreateParent(newChildren)
    }
}

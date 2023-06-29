package keb.ropes

sealed interface PersistentBTreeNode : BTreeNode

class PersistentLeafNode(override val value: String) : LeafNode(), PersistentBTreeNode {
    override val weight: Int = value.length
    override val isEmpty: Boolean = value.isEmpty()
    override val isLegalNode: Boolean = weight < MAX_SIZE_LEAF
}

class PersistentInternalNode(
    override val weight: Int,
    override val height: Int,
    override val children: List<BTreeNode>
) : InternalNode(), PersistentBTreeNode {
    override val isLegalNode: Boolean
        get() = TODO("Not yet implemented")
}

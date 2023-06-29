package keb.ropes

sealed interface MutableBTreeNode : BTreeNode

class MutableLeafNode(value: String) : LeafNode(), MutableBTreeNode {
    private val sb = StringBuilder(value)

    override val value: String get() = sb.toString()
    override val weight: Int get() = sb.length
}

class MutableInternalNode(
    override val weight: Int,
    override val height: Int,
    children: List<BTreeNode>
) : InternalNode(), MutableBTreeNode {
    private val _children = children.toMutableList()
    override val children: List<BTreeNode> get() = _children

    override val isLegalNode: Boolean
        get() = TODO("Not yet implemented")
}
package keb.ropes

sealed interface MutableBTreeNode : BTreeNode {
    //operator fun plus(other: MutableBTreeNode)
}

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
}
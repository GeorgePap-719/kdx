package keb.ropes

class MutableLeafNode(initialValue: String) : LeafNode(initialValue) {
    private var sb = StringBuilder(initialValue)

    override val value: String get() = sb.toString()
    override val weight: Int get() = sb.length

    fun add(index: Int, element: String) {
        val newLen = weight + element.length
        require(newLen <= MAX_SIZE_LEAF) { "max size of a leaf is:$MAX_SIZE_LEAF, but got:$newLen" }
        sb.insert(index, value)
    }

    // Delete operations might leave leaf empty

    fun deleteAt(index: Int) {
        sb.deleteAt(index)
    }

    fun removeRange(range: IntRange) {
        sb.removeRange(range.first, range.last + 1)
    }

    fun removeRange(startIndex: Int, endIndex: Int) {
        sb.removeRange(startIndex, endIndex)
    }
}

fun LeafNode.makeMutable(): MutableLeafNode = if (this is MutableLeafNode) this else MutableLeafNode(value)

class MutableInternalNode(
    weight: Int,
    height: Int,
    children: List<BTreeNode>
) : InternalNode(weight, height, children) {
    private val mutableChildren = children.toMutableList()

    override val children: List<BTreeNode> get() = mutableChildren

    fun addChild(index: Int, child: BTreeNode) {
        checkElementIndex(index)
        require(children.size + 1 <= MAX_CHILDREN) { "node cannot hold more than:$MAX_CHILDREN children" }
        mutableChildren.add(index, child)
    }

    fun removeAt(index: Int) {
        checkElementIndex(index)
        mutableChildren.removeAt(index)
    }
}

fun InternalNode.makeMutable(): MutableInternalNode =
    if (this is MutableInternalNode) this else MutableInternalNode(weight, height, children)
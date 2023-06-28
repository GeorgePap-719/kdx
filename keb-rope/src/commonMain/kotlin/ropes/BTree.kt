package keb.ropes

interface BTreeNode {
    val weight: Int
    val height: Int

    //
    val isLegalNode: Boolean
    val isEmpty: Boolean //-> can be calculated

    fun isBalanced(): Boolean {
        if (!this.isLegalNode || isEmpty) return false
        if (this is AbstractInternalNode) for (node in this.children) if (!node.isBalanced()) return false
        return true
    }
}

abstract class AbstractLeafNode : BTreeNode {
    abstract val value: String
    final override val height: Int = 0
    override val isEmpty: Boolean get() = value.isEmpty()
    override val isLegalNode: Boolean get() = weight < MAX_SIZE_LEAF
}

abstract class AbstractInternalNode : BTreeNode {
    init {
        @Suppress("LeakingThis")
        require(children.isNotEmpty()) { "internal node cannot be empty" }
    }

    abstract val children: List<BTreeNode>
    override val isEmpty: Boolean get() = children.isEmpty()
}

const val MIN_CHILDREN = 4
const val MAX_CHILDREN = 8
const val MAX_SIZE_LEAF = 2048

package keb.ropes

import keb.classSimpleName
import keb.hexAddress

interface BTreeNode : Iterable<LeafNode> {
    val weight: Int
    val height: Int

    //
    val isLegalNode: Boolean
    val isEmpty: Boolean //-> can be calculated

    fun isBalanced(): Boolean {
        if (!this.isLegalNode || isEmpty) return false
        if (this is InternalNode) for (node in this.children) if (!node.isBalanced()) return false
        return true
    }
}

abstract class LeafNode : BTreeNode {
    abstract val value: String
    final override val height: Int = 0
    override val isEmpty: Boolean get() = value.isEmpty()
    override val isLegalNode: Boolean get() = weight < MAX_SIZE_LEAF

    override fun iterator(): Iterator<LeafNode> {
        return SingleBTreeNodeIterator(this)
    }

    internal fun toStringDebug(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName@$hexAddress(")
        sb.append("weight=$weight,")
        sb.append("isLeafNode=true,")
        sb.append("value=$value,")
        sb.append("height=$height,")
        sb.append("isLegal=$isLegalNode")
        sb.append(")")
        return sb.toString()
    }
}

abstract class InternalNode : BTreeNode {
    init {
        @Suppress("LeakingThis")
        require(children.isNotEmpty()) { "internal node cannot be empty" }
    }

    abstract val children: List<BTreeNode>
    override val isEmpty: Boolean get() = children.isEmpty()

    override val isLegalNode: Boolean
        get() {
            //TODO:
            // children.size > MAX_CHILDREN || children.size < MIN_CHILDREN
            // with the above condition, we have to change isBalanced() API, since it is a condition
            // where we cannot always meet.
            // Maybe we also need to distinct between legal and balanced nodes.
            if (children.size > MAX_CHILDREN) return false
            val rootHeight = height
            for (node in children) if (node.height >= rootHeight) return false
            return true
        }

    override fun iterator(): Iterator<LeafNode> {
        return BTreeNodeIterator(this)
    }

    internal fun toStringDebug(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName@$hexAddress(")
        sb.append("weight=$weight,")
        sb.append("isInternalNode=true,")
        sb.append("childrenSize=${children.size},")
        sb.append("children=[")
        for (node in children) {
            when (node) {
                is InternalNode -> sb.append("${node.toStringDebug()},")
                is LeafNode -> sb.append("${node.toStringDebug()},")
            }
        }
        sb.append("],")
        sb.append("height=$height,")
        sb.append("isLegal=$isLegalNode")
        sb.append(")")
        return sb.toString()
    }
}

const val MIN_CHILDREN = 4
const val MAX_CHILDREN = 8
const val MAX_SIZE_LEAF = 2048

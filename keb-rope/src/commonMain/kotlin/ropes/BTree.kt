package keb.ropes

import keb.classSimpleName
import keb.hexAddress

interface BTreeNode : Iterable<LeafNode> {
    val weight: Int
    val height: Int
    val isLegal: Boolean
    val isEmpty: Boolean
}

/**
 * Adds the [other] tree to the right side of this tree, and creates a new balanced btree.
 */
operator fun BTreeNode.plus(other: BTreeNode): InternalNode = merge(this, other)

fun BTreeNode.isBalanced(): Boolean {
    if (!this.isLegal || isEmpty) return false
    if (this is InternalNode) for (node in this.children) if (!node.isBalanced()) return false
    return true
}

abstract class LeafNode : BTreeNode {
    abstract val value: String

    final override val height: Int = 0
    override val isEmpty: Boolean get() = value.isEmpty()
    override val isLegal: Boolean get() = weight <= MAX_SIZE_LEAF

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
        sb.append("isLegal=$isLegal")
        sb.append(")")
        return sb.toString()
    }
}

abstract class InternalNode : BTreeNode {
    abstract val children: List<BTreeNode>

    init {
        @Suppress("LeakingThis")
        require(children.isNotEmpty()) { "internal node cannot be empty" }
    }

    override val isEmpty: Boolean get() = children.isEmpty()
    override val isLegal: Boolean
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

    fun indexOf(child: BTreeNode): Int = children.indexOf(child)

    protected fun checkElementIndex(index: Int) {
        if (index < 0 || index >= MAX_CHILDREN) throw IndexOutOfBoundsException("index:$index")
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
        sb.append("isLegal=$isLegal")
        sb.append(")")
        return sb.toString()
    }
}

const val MIN_CHILDREN = 4
const val MAX_CHILDREN = 8
const val MAX_SIZE_LEAF = 2048

// --- XXXcow ---

fun List<BTreeNode>.replaceWithCopyOnWrite(oldNode: BTreeNode, newNode: BTreeNode): List<BTreeNode> {
    return buildList {
        for (node in this@replaceWithCopyOnWrite) {
            if (node === oldNode) {
                add(newNode)
            } else {
                add(node)
            }
        }
    }
}

fun List<BTreeNode>.addWithCopyOnWrite(newNode: BTreeNode, index: Int): List<BTreeNode> {
    return buildList {
        var added = false // flag to check if the new element is in the bounds of the current list.
        for ((i, node) in this@addWithCopyOnWrite.withIndex()) {
            if (i == index) {
                add(newNode)
                added = true
            }
            add(node)
        }
        if (!added) add(newNode)
    }
}

fun List<BTreeNode>.addWithCopyOnWrite(newNode: List<BTreeNode>, index: Int): List<BTreeNode> {
    return buildList {
        var added = false // flag to check if the new element is in the bounds of the current list.
        for ((i, node) in this@addWithCopyOnWrite.withIndex()) {
            if (i == index) {
                addAll(newNode)
                added = true
            }
            add(node)
        }
        if (!added) addAll(newNode)
    }
}

// --- builders ---

/**
 * Merges [left] and [right] nodes into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
fun merge(left: BTreeNode, right: BTreeNode): PersistentInternalNode = merge(listOf(left, right))

/**
 * Merges [nodes] into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
fun merge(nodes: List<BTreeNode>): PersistentInternalNode {
    nodes.forEach {
        require(it.isLegal) { "node:$it does not meet the requirements" }
    }
    return unsafeMerge(nodes)
}

/**
 * An analogue of the [merge] builder that does not check for invariants.
 * Used internally in operators where we trust the validity of nodes.
 */
private fun unsafeMerge(nodes: List<BTreeNode>): PersistentInternalNode {
    if (nodes.size <= MAX_CHILDREN) return unsafeCreateParent(nodes)
    val leftList = nodes.subList(0, MAX_CHILDREN)
    val rightList = nodes.subList(MAX_CHILDREN, nodes.size)
    val leftParent = unsafeCreateParent(leftList)
    if (rightList.size <= MAX_CHILDREN) {
        val rightParent = unsafeCreateParent(rightList)
        return unsafeCreateParent(leftParent, rightParent)
    }
    val rightParent = unsafeMerge(rightList)
    return unsafeCreateParent(leftParent, rightParent)
}

/**
 * Creates a legal parent for [left] and [right] nodes.
 * The weight of the parent is set to that of the [left] node.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
 */
fun createParent(left: BTreeNode, right: BTreeNode): PersistentInternalNode {
    return createParent(listOf(left, right))
}

/**
 * Creates a legal parent for [nodes].
 * The weight of the parent is set to that of the first node.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
 */
fun createParent(nodes: List<BTreeNode>): PersistentInternalNode {
    require(nodes.size <= MAX_CHILDREN) { "a node cannot hold more than:$MAX_CHILDREN children" }
    nodes.forEach {
        require(it.isLegal) { "node:$it does not meet the requirements" }
    }
    return unsafeCreateParent(nodes)
}

internal fun unsafeCreateParent(left: BTreeNode, right: BTreeNode): PersistentInternalNode {
    return unsafeCreateParent(listOf(left, right))
}

/**
 * Creates a parent for [nodes], without checking if satisfies the requirements for a legal btree.
 */
internal fun unsafeCreateParent(nodes: List<BTreeNode>): PersistentInternalNode {
    val weight = computeWeightInLeftSubtreeForParent(nodes)
    val height = nodes.maxOf { it.height } + 1
    return PersistentInternalNode(weight, height, nodes)
}

/**
 * Computes weight in left subtree for a new parent.
 */
private fun computeWeightInLeftSubtreeForParent(children: List<BTreeNode>): Int {
    return when (val leftmostNode = children.first()) {
        is LeafNode -> leftmostNode.weight
        //TODO: check if we can compute this with faster path
        is InternalNode -> leftmostNode.sumOf { it.weight }
        else -> error("unexpected")
    }
}
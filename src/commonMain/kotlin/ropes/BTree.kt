package keb.ropes

import keb.classSimpleName
import keb.hexAddress

open class NodeInfo //TODO: impl later, but it seems it is not needed for btree impl

/**
 * Persistent [btree](https://en.wikipedia.org/wiki/B-tree) node. Modification operations return new
 * instances of the tree with the modifications applied.
 */
sealed class BTreeNode(
    /**
     * If this node is a leaf, then this value represents the length of the string. Else holds the sum of the lengths of
     * all the leaves in its left subtree.
     */
    val weight: Int,
    /**
     * The `height` is measured as the number of edges in the longest path from the root node to a leaf node. If this
     * node is the `root`, then it represents the `height` of the entire tree. By extension, if this is a leaf node
     * then, it should be `0`.
     */
    val height: Int,
) : Iterable<LeafNode> {
    abstract val isInternalNode: Boolean
    abstract val isLeafNode: Boolean

    abstract val isLegalNode: Boolean

    val isEmpty: Boolean = weight == 0

    /**
     * Checks if tree needs rebalancing and rebuilds it from the bottom-up. In case it is balanced, then it returns
     * the same tree.
     */
    fun rebalance(): BTreeNode {
        if (isBalanced()) return this
        //TODO: we do not check for invariant leaves here, since we do not have yet a proper impl yet.
        val leaves = buildList { for (node in this@BTreeNode) add(node) }
        return unsafeMerge(leaves)
    }

    //TODO: check if this should be public API
    fun isBalanced(): Boolean {
        if (!this.isLegalNode) return false
        if (this is InternalNode) for (node in this.children) if (!node.isBalanced()) return false
        return true
    }

    /**
     * Returns the parent node of [child], or `null` if [child] is the root node.
     * Root is considered as "this" node.
     */
    private fun getParentOrNull(child: BTreeNode): InternalNode? {
        if (child === this) return null
        val root = this as InternalNode
        return tailrecGetParentOrNull(child, root)
    }

    private tailrec fun tailrecGetParentOrNull(child: BTreeNode, curParent: InternalNode): InternalNode? {
        for (node in curParent.children) {
            when (node) {
                is InternalNode -> {
                    if (node === child) return curParent
                    return tailrecGetParentOrNull(child, node)
                }

                is LeafNode -> if (node === child) return curParent
            }
        }
        return null
    }

    /**
     * Returns the [LeafNode] at the given [index] or `null` if the [index] is out of bounds of this tree.
     */
    //TODO: impl should be revisited
    @Deprecated("care with this API")
    open operator fun get(index: Int): LeafNode? {
        if (index < 0) return null
        return LazyPathFinder(this, index).getOrNull()
    }

    private class LazyPathFinder(var curNode: BTreeNode, val index: Int) {
        var curIndex = 0

        fun getOrNull(): LeafNode? {
            if (curIndex > index) return null
            when (curNode) {
                is InternalNode -> {
                    for (node in (curNode as InternalNode).children) {
                        curNode = node
                        return getOrNull() ?: continue
                    }
                }

                is LeafNode -> {
                    if (curIndex == index) return curNode as LeafNode
                    curIndex++
                }
            }
            return null // out of bounds
        }
    }

    fun find(value: String): LeafNode? {
        val len = value.length
        if (len > weight) return null
        return when (this) {
            is InternalNode -> children.find(value, len)
            is LeafNode -> {
                if (this.value.contains(value)) {
                    this
                } else {
                    null
                }
            }
        }
    }

    private tailrec fun List<BTreeNode>.find(value: String, len: Int): LeafNode? {
        for (node in this) {
            if (len > weight) continue
            when (node) {
                is InternalNode -> return node.children.find(value, len)
                is LeafNode -> if (node.value.contains(value)) return node
            }
        }
        return null
    }

    override fun iterator(): Iterator<LeafNode> {
        return when (this) {
            is InternalNode -> BTreeNodeIterator(this)
            is LeafNode -> SingleBTreeNodeIterator(this)
        }
    }

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName(")
        sb.append("weight=$weight,")
        sb.append("isBalanced=${isBalanced()},")
        if (isInternalNode) {
            sb.append("isInternalNode=true")
        } else {
            sb.append("isLeafNode=true")
        }
        sb.append(")")
        return sb.toString()
    }

    internal fun toStringDebug(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName@$hexAddress(")
        sb.append("weight=$weight,")
        when (this) {
            is InternalNode -> {
                sb.append("isInternalNode=true,")
                sb.append("childrenSize=${children.size},")
                sb.append("children=[")
                for (node in children) sb.append("${node.toStringDebug()},")
                sb.append("],")
            }

            is LeafNode -> {
                sb.append("isLeafNode=true,")
                sb.append("value=$value,")
            }
        }
        sb.append("height=$height,")
        sb.append("isLegal=$isLegalNode")
        sb.append(")")
        return sb.toString()
    }
}

/**
 * Represents a leaf-node in Btree.
 */
class LeafNode(val value: String) : BTreeNode(value.length, 0) {
    constructor(value: Char) : this(value.toString())

    override val isInternalNode: Boolean = false
    override val isLeafNode: Boolean = true

    override val isLegalNode: Boolean = value.length <= MAX_SIZE_LEAF
}

const val MIN_CHILDREN = 4
const val MAX_CHILDREN = 8
const val MAX_SIZE_LEAF = 2048

/**
 * Represents an internal-node in Btree with a maximum of 8 children.
 */
open class InternalNode(
    weight: Int,
    height: Int,
    val children: List<BTreeNode>,
) : BTreeNode(weight, height) {

    init {
        require(children.isNotEmpty()) { "internal node cannot be empty" }
    }

    override val isInternalNode: Boolean = true
    override val isLeafNode: Boolean = false
    override val isLegalNode: Boolean = isLegalNodeImpl()


    val areChildrenLegal: Boolean = areChildrenLegalImpl() //TODO: check if this pulls his weight

    private fun isLegalNodeImpl(): Boolean {
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

    private fun areChildrenLegalImpl(): Boolean {
        var isLegal = false
        for (node in children) {
            isLegal = when (node) {
                is InternalNode -> node.isLegalNode && node.areChildrenLegalImpl()
                is LeafNode -> node.isLegalNode
            }
            if (!isLegal) return false
        }
        return isLegal
    }

    /**
     * Returns a new expanded [node][InternalNode] by a factor of 2. This operation splits
     * the tree in half and creates a new parent node for them.
     *
     * @throws IllegalArgumentException if a child node is not a legal ([BTreeNode.isLegalNode]).
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
     * Merges the [other] tree to the right side of this tree, and creates a new balanced btree.
     *
     * @throws IllegalArgumentException if a child node is not a legal ([BTreeNode.isLegalNode]).
     */
    fun merge(other: InternalNode): InternalNode = merge(this, other)

    /**
     * Returns a new [node][InternalNode] with the specified [child] inserted at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is greater than or equal to the maximum size of children.
     * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
     */
    fun add(index: Int, child: BTreeNode): InternalNode {
        if (index >= MAX_CHILDREN) throw IndexOutOfBoundsException()
        require(children.size + 1 <= MAX_CHILDREN) { "node cannot hold more than:$MAX_CHILDREN children" }
        val newChildren = children.addWithCopyOnWrite(child, index)
        return unsafeCreateParent(newChildren)
    }

    fun addLast(child: BTreeNode): InternalNode = add(children.size - 1, child)

    fun addFirst(child: BTreeNode): InternalNode = add(0, child)

    fun addAll(index: Int, children: List<BTreeNode>): InternalNode {
        if (index >= MAX_CHILDREN) throw IndexOutOfBoundsException()
        require(this.children.size + children.size <= MAX_CHILDREN) {
            "node cannot hold more than:$MAX_CHILDREN children"
        }
        val newChildren = this.children.addWithCopyOnWrite(children, index)
        return unsafeCreateParent(newChildren)
    }

    fun indexOf(child: BTreeNode): Int = children.indexOf(child)
}

/**
 * Adds the [other] tree to the right side of this tree, and creates a new balanced btree
 * (see [InternalNode.merge]).
 */
operator fun InternalNode.plus(other: InternalNode): InternalNode = merge(other)

// --- XXXcow

fun InternalNode.replaceChildWithCopyOnWrite(oldNode: BTreeNode, newNode: BTreeNode): InternalNode {
    return unsafeCreateParent(this.children.replaceWithCopyOnWrite(oldNode, newNode))
}

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
        for ((_index, node) in this@addWithCopyOnWrite.withIndex()) {
            if (_index == index) {
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
        for ((_index, node) in this@addWithCopyOnWrite.withIndex()) {
            if (_index == index) {
                addAll(newNode)
                added = true
            }
            add(node)
        }
        if (!added) addAll(newNode)
    }
}

// --- extensions ---

/**
 * Tries to add a list of [children] on this node, with copy-on-write semantics, in the specified [index].
 * In case [index] is `null` the nodes are appended to the end of list. If receiver node cannot hold more children,
 * it returns `null`.
 */
fun InternalNode.tryAddAll(children: List<BTreeNode>, index: Int? = null): InternalNode? {
    if (this.children.size + children.size > MAX_CHILDREN) return null
    return addAll(index ?: (children.size - 1), children)
}

/**
 * Returns a new [node][InternalNode] with the specified [child] inserted at the specified [index].
 * In case [index] is `null` the nodes are appended to the end of list. If receiver node cannot
 * hold more children, it returns `null`.
 */
fun InternalNode.tryAdd(child: BTreeNode, index: Int? = null): InternalNode? {
    if (this.children.size + 1 > MAX_CHILDREN) return null
    return add(index ?: (children.size - 1), child)
}

/**
 * Returns a new [node][InternalNode] with the specified [child] inserted first. If receiver node
 * cannot hold more children, it returns `null`.
 */
fun InternalNode.tryAddFirst(child: BTreeNode): InternalNode? {
    if (this.children.size + 1 > MAX_CHILDREN) return null
    return addFirst(child)
}

// --- builders ---

/**
 * Merges [left] and [right] nodes into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not a legal ([BTreeNode.isLegalNode]).
 */
fun merge(left: BTreeNode, right: BTreeNode): InternalNode = merge(listOf(left, right))

/**
 * Merges [nodes] into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not a legal ([BTreeNode.isLegalNode]).
 */
fun merge(nodes: List<BTreeNode>): InternalNode {
    nodes.forEach {
        require(it.isLegalNode) { "node:$it does not meet the requirements" }
    }
    return unsafeMerge(nodes)
}

/**
 * An analogue of the [merge] builder that does not check for invariants. Used internally in operators
 * where we trust the validity of nodes.
 */
private fun unsafeMerge(nodes: List<BTreeNode>): InternalNode {
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
 * Creates a legal parent for [nodes].
 *
 * @throws IllegalArgumentException if a child node is not a legal ([BTreeNode.isLegalNode]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
 */
fun createParent(nodes: List<BTreeNode>): InternalNode {
    require(nodes.size <= MAX_CHILDREN) { "a node cannot hold more than:$MAX_CHILDREN children" }
    nodes.forEach {
        require(it.isLegalNode) { "node:$it does not meet the requirements" }
    }
    return unsafeCreateParent(nodes)
}

private fun unsafeCreateParent(left: BTreeNode, right: BTreeNode): InternalNode {
    return unsafeCreateParent(listOf(left, right))
}

/**
 * Creates a parent for [nodes], without checking if satisfies the requirements for a legal btree.
 */
private fun unsafeCreateParent(nodes: List<BTreeNode>): InternalNode {
    val weight = computeWeightInLeftSubtreeForParent(nodes)
    val height = nodes.maxOf { it.height } + 1
    return InternalNode(weight, height, nodes)
}

/**
 * Computes weight in left subtree for a new parent.
 */
private fun computeWeightInLeftSubtreeForParent(children: List<BTreeNode>): Int {
    return when (val leftmostNode = children.first()) {
        is LeafNode -> leftmostNode.weight
        //TODO: check if we can compute this with faster path
        is InternalNode -> leftmostNode.sumOf { it.weight }
    }
}
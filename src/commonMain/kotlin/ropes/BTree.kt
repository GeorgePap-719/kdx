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

    private fun List<BTreeNode>.find(value: String, len: Int): LeafNode? {
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

@Deprecated("bad api", ReplaceWith("listOf(this, other)"))
operator fun BTreeNode.plus(other: BTreeNode): List<BTreeNode> = listOf(this, other)

/**
 * Represents a leaf-node in Btree.
 */
class LeafNode(val value: String) : BTreeNode(value.length, 0) {
    override val isInternalNode: Boolean = false
    override val isLeafNode: Boolean = true

    override val isLegalNode: Boolean = value.length <= MAX_SIZE_LEAF
}

const val MIN_CHILDREN = 4
const val MAX_CHILDREN = 8
const val MAX_SIZE_LEAF = 2048

/**
 * Represents an internal-node in Btree.
 */
open class InternalNode(
    weight: Int,
    height: Int,
    val children: List<BTreeNode> = listOf(),
) : BTreeNode(weight, height) {
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
        }
        return isLegal
    }
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

/**
 * Tries to add a list of [children] on this node, with copy-on-write semantics, in the specified [index].
 * In case [index] is `null` the nodes are appended to the end of list. If receiver node cannot hold more children, it returns `null`.
 */
fun InternalNode.tryAddChildren(children: List<BTreeNode>, index: Int? = null): InternalNode? {
    if (this.children.size + children.size > MAX_CHILDREN) return null
    val newChildren = this.children.addWithCopyOnWrite(children, index ?: children.size)
    return InternalNode(this.weight, this.height, newChildren)
}

/**
 * Tries to add a child on this node, with copy-on-write semantics, in the specified [index]. In case [index]
 * is `null` the nodes are appended to the end of list. If receiver node cannot hold more children, it returns `null`.
 */
fun InternalNode.tryAddChild(child: BTreeNode, index: Int? = null): InternalNode? {
    if (this.children.size + 1 > MAX_CHILDREN) return null
    val newChildren = this.children.addWithCopyOnWrite(child, index ?: children.size)
    return InternalNode(this.weight, this.height, newChildren)
}

// --- builders ---

/**
 * Merges [nodes] into one balanced btree.
 *
 * @throws IllegalArgumentException if a node [is-not-legal][BTreeNode.isLegalNode].
 */
fun merge(nodes: List<BTreeNode>): BTreeNode {
    nodes.forEach {
        require(it.isLegalNode) { "node:$it does not meet the requirements" }
    }
    return unsafeMerge(nodes)
}

/**
 * An analogue of the [merge] builder that does not check for invariants. Used internally in operators
 * where we trust the validity of nodes.
 */
private fun unsafeMerge(nodes: List<BTreeNode>): BTreeNode {
    if (nodes.size <= MAX_CHILDREN) return createParent(nodes)
    val leftList = nodes.subList(0, MAX_CHILDREN)
    val rightList = nodes.subList(MAX_CHILDREN, nodes.size)
    val leftParent = createParent(leftList)
    if (rightList.size <= MAX_CHILDREN) {
        val rightParent = createParent(rightList)
        return createParent(leftParent, rightParent)
    }
    val rightParent = unsafeMerge(rightList)
    return createParent(leftParent, rightParent)
}

private fun createParent(left: BTreeNode, right: BTreeNode): InternalNode {
    return createParent(listOf(left, right))
}

/**
 * Creates a parent for [nodes], without checking if satisfies the requirements for a legal btree.
 */
private fun createParent(nodes: List<BTreeNode>): InternalNode {
    val weight = nodes.first().computeWeightInLeftSubtree()
    val height = nodes.maxOf { it.height } + 1
    return InternalNode(weight, height, nodes)
}

//TODO: this can probably be improved, by not searching all leaves
private fun BTreeNode.computeWeightInLeftSubtree(): Int {
    return when (this) {
        is InternalNode -> {
            //TODO: is this redundant action since the weight is already computed?
            val leftmost = this.children.first()
            return leftmost.sumOf { it.weight } // sumOf weight in leaves
        }

        is LeafNode -> this.weight
    }
}
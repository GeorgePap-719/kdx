package keb.ropes

import keb.classSimpleName
import keb.hexAddress

interface Leaf {
    val weight: Int
}

val Leaf.isEmpty get() = weight == 0

sealed class BTreeNode<T : Leaf> : Iterable<LeafNode<T>> {
    abstract val weight: Int
    abstract val height: Int
    abstract val isLegal: Boolean
    abstract val isEmpty: Boolean
}

/**
 * Adds the [other] tree to the right side of this tree, and creates a new balanced btree.
 */
operator fun <T : Leaf> BTreeNode<T>.plus(other: BTreeNode<T>): InternalNode<T> = merge(this, other)

fun <T : Leaf> BTreeNode<T>.isBalanced(): Boolean {
    if (!this.isLegal || isEmpty) return false
    if (this is InternalNode) for (node in this.children) if (!node.isBalanced()) return false
    return true
}

/**
 * Checks if tree needs rebalancing and rebuilds it from the bottom-up.
 * In case it is balanced, then it returns the same tree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
fun <T : Leaf> BTreeNode<T>.rebalance(): BTreeNode<T> {
    if (isBalanced()) return this
    val leaves = this.mapNotNull { if (it.isEmpty) null else it }
    return merge(leaves)
}

class LeafNode<T : Leaf>(val value: T) : BTreeNode<T>() {
    override val weight: Int = value.weight
    override val height: Int = 0
    override val isEmpty: Boolean = value.isEmpty
    override val isLegal: Boolean = weight <= MAX_SIZE_LEAF

    override fun iterator(): Iterator<LeafNode<T>> {
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

class InternalNode<T : Leaf>(
    override val weight: Int,
    override val height: Int,
    val children: List<BTreeNode<T>>
) : BTreeNode<T>() {
    init {
        require(children.isNotEmpty()) { "internal node cannot be empty" }
    }

    override val isEmpty: Boolean get() = children.isEmpty()
    override val isLegal: Boolean
        get() {
            //TODO:
            // 1. compute it once
            // 2. children.size > MAX_CHILDREN || children.size < MIN_CHILDREN
            // with the above condition, we have to change isBalanced() API, since it is a condition
            // where we cannot always meet.
            // Maybe we also need to distinct between legal and balanced nodes.
            if (children.size > MAX_CHILDREN) return false
            val rootHeight = height
            for (node in children) if (node.height >= rootHeight) return false
            return true
        }

    override fun iterator(): Iterator<LeafNode<T>> {
        return BTreeNodeIterator(this)
    }

    fun indexOf(child: BTreeNode<T>): Int = children.indexOf(child)

    /**
     * Returns a new expanded [node][InternalNode] by a factor of 2.
     * This operation splits the tree in half and creates a new parent node for them.
     *
     * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
     */
    fun expand(): InternalNode<T> {
        if (children.size == 1) return this
        val half = children.size / 2
        val left = children.subList(0, half)
        val right = children.subList(half, children.size)
        val leftParent = merge(left)
        val rightParent = merge(right)
        return merge(leftParent, rightParent) //TODO: check if we better use merge or unsafe merge
    }

    /**
     * Returns a new [node][InternalNode] with the specified [child] inserted at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is greater than or equal to the maximum size of children.
     * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
     */
    fun add(index: Int, child: BTreeNode<T>): InternalNode<T> {
        checkElementIndex(index)
        require(children.size + 1 <= MAX_CHILDREN) { "node cannot hold more than:$MAX_CHILDREN children" }
        val newChildren = children.addWithCopyOnWrite(child, index)
        return unsafeCreateParent(newChildren)
    }

    fun addLast(child: BTreeNode<T>): InternalNode<T> = add(children.size - 1, child)

    fun addFirst(child: BTreeNode<T>): InternalNode<T> = add(0, child)

    fun addAll(index: Int, children: List<BTreeNode<T>>): InternalNode<T> {
        checkElementIndex(index)
        require(this.children.size + children.size <= MAX_CHILDREN) {
            "node cannot hold more than:$MAX_CHILDREN children"
        }
        val newChildren = this.children.addWithCopyOnWrite(children, index)
        return unsafeCreateParent(newChildren)
    }

    operator fun set(index: Int, child: BTreeNode<T>): InternalNode<T> {
        checkElementIndex(index)
        val newChildren = buildList {
            for (i in children.indices) {
                if (i == index) add(child) else add(children[i])
            }
        }
        return unsafeCreateParent(newChildren)
    }

    operator fun set(index: Int, children: List<BTreeNode<T>>): InternalNode<T> {
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

    fun replace(old: BTreeNode<T>, new: BTreeNode<T>): InternalNode<T> {
        val newChildren = buildList {
            for (child in children) {
                if (child === old) add(new) else add(child)
            }
        }
        return unsafeCreateParent(newChildren)
    }

    fun deleteAt(index: Int): InternalNode<T>? {
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

    private fun checkElementIndex(index: Int) {
        if (index < 0 || index >= MAX_CHILDREN) throw IndexOutOfBoundsException("index:$index")
    }

    // --- DEBUG FUNCTIONS ---

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

fun <T : Leaf> List<BTreeNode<T>>.replaceWithCopyOnWrite(
    oldNode: BTreeNode<T>,
    newNode: BTreeNode<T>
): List<BTreeNode<T>> {
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

fun <T : Leaf> List<BTreeNode<T>>.addWithCopyOnWrite(newNode: BTreeNode<T>, index: Int): List<BTreeNode<T>> {
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

fun <T : Leaf> List<BTreeNode<T>>.addWithCopyOnWrite(newNode: List<BTreeNode<T>>, index: Int): List<BTreeNode<T>> {
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
fun <T : Leaf> merge(left: BTreeNode<T>, right: BTreeNode<T>): InternalNode<T> = merge(listOf(left, right))

/**
 * Merges [nodes] into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
fun <T : Leaf> merge(nodes: List<BTreeNode<T>>): InternalNode<T> {
    nodes.forEach {
        require(it.isLegal) { "node:$it does not meet the requirements" }
    }
    return unsafeMerge(nodes)
}

/**
 * An analogue of the [merge] builder that does not check for invariants.
 * Used internally in operators where we trust the validity of nodes.
 */
private fun <T : Leaf> unsafeMerge(nodes: List<BTreeNode<T>>): InternalNode<T> {
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
fun <T : Leaf> createParent(left: BTreeNode<T>, right: BTreeNode<T>): InternalNode<T> {
    return createParent(listOf(left, right))
}

/**
 * Creates a legal parent for [nodes].
 * The weight of the parent is set to that of the first node.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
 */
fun <T : Leaf> createParent(nodes: List<BTreeNode<T>>): InternalNode<T> {
    require(nodes.size <= MAX_CHILDREN) { "a node cannot hold more than:$MAX_CHILDREN children" }
    nodes.forEach {
        require(it.isLegal) { "node:$it does not meet the requirements" }
    }
    return unsafeCreateParent(nodes)
}

internal fun <T : Leaf> unsafeCreateParent(left: BTreeNode<T>, right: BTreeNode<T>): InternalNode<T> {
    return unsafeCreateParent(listOf(left, right))
}

/**
 * Creates a parent for [nodes], without checking if satisfies the requirements for a legal btree.
 */
internal fun <T : Leaf> unsafeCreateParent(nodes: List<BTreeNode<T>>): InternalNode<T> {
    val weight = computeWeightInLeftSubtreeForParent(nodes)
    val height = nodes.maxOf { it.height } + 1
    return InternalNode(weight, height, nodes)
}

/**
 * Computes weight in left subtree for a new parent.
 */
private fun <T : Leaf> computeWeightInLeftSubtreeForParent(children: List<BTreeNode<T>>): Int {
    return when (val leftmostNode = children.first()) {
        is LeafNode -> leftmostNode.weight
        //TODO: check if we can compute this with faster path
        is InternalNode -> leftmostNode.sumOf { it.weight }
        else -> error("unexpected")
    }
}
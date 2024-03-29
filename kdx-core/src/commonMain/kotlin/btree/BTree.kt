package kdx.btree

import kdx.classSimpleName
import kdx.hexAddress

/**
 * The properties of a [LeafNode].
 */
interface LeafInfo {
    /**
     * Returns the measurement of this leaf in units.
     * The type of unit is specified by the implementation.
     */
    val length: Int

    /**
     * Returns whether this leaf is legal or not.
     * Generally, this mostly depends on the leaf's implementation,
     * but can be used to decide if the tree needs rebalancing or not.
     */
    val isLegal: Boolean

    /**
     * Returns a subSequence of this leaf specified by the given [range] of indices.
     */
    fun subsequnce(range: IntRange): LeafInfo
}

val LeafInfo.isEmpty: Boolean get() = length == 0

/**
 * A persistent [btree](https://en.wikipedia.org/wiki/B-tree#Algorithms) node.
 */
//TODO: this can probably be an interface
sealed class BTreeNode<out T : LeafInfo> : Iterable<LeafNode<T>> {
    abstract val weight: Int
    abstract val height: Int
    abstract val isLegal: Boolean
    abstract val isEmpty: Boolean

    @Deprecated("Use the corresponding operator on `Rope`, wherever applicable")
    abstract fun subSequence(range: IntRange): BTreeNode<T>
}

val <T : LeafInfo> BTreeNode<T>.isNotEmpty: Boolean get() = !isEmpty

/**
 * Adds the [other] tree to the right side of this tree, and creates a new balanced btree.
 */
operator fun <T : LeafInfo> BTreeNode<T>.plus(other: BTreeNode<T>): InternalNode<T> = merge(this, other)

fun <T : LeafInfo> BTreeNode<T>.isBalanced(): Boolean {
    if (!this.isLegal || isEmpty) return false
    if (this is InternalNode) for (node in this.children) if (!node.isBalanced()) return false
    return true
}

/**
 * Checks if the tree needs rebalancing and rebuilds it from the bottom-up.
 * In case it is balanced, then it returns the same tree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
//TODO: rebalance should not throw in case a node is not legal,
// but split it properly.
fun <T : LeafInfo> BTreeNode<T>.rebalance(): BTreeNode<T> {
    if (isBalanced()) return this
    val leaves = this.mapNotNull { if (it.isEmpty) null else it }
    return merge(leaves)
}

// for more readable API
fun <T : LeafInfo> BTreeNode<T>.collectLeaves(): List<LeafNode<T>> = toList()

/**
 * Computes this btree's length.
 */
fun <T : LeafInfo> BTreeNode<T>.treeLength(): Int = when (this) {
    is LeafNode -> this.weight
    is InternalNode -> {
        val children = this.children
        var curLen = 0
        curLen += this.weight
        for (index in children.indices) {
            if (index == 0) continue
            curLen += children[index].treeLength()
        }
        curLen
    }
}

class LeafNode<out T : LeafInfo>(val value: T) : BTreeNode<T>() {
    override val weight: Int = value.length
    override val height: Int = 0
    override val isEmpty: Boolean = value.isEmpty

    @Deprecated("Use the corresponding operator on `Rope`, wherever applicable")
    override fun subSequence(range: IntRange): LeafNode<T> {
        val newSequence = value.subsequnce(range)
        @Suppress("UNCHECKED_CAST")
        return LeafNode(newSequence) as LeafNode<T>
    }

    override val isLegal: Boolean = value.isLegal

    override fun iterator(): Iterator<LeafNode<T>> {
        return SingleBTreeNodeIterator(this)
    }

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName(")
        sb.append("weight=$weight,")
        sb.append("value=$value,")
        sb.append("height=$height,")
        sb.append("isLegal=$isLegal")
        sb.append(")")
        return sb.toString()
    }

    internal fun toStringDebug(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName@$hexAddress(")
        sb.append("weight=$weight,")
        sb.append("isLeafNode=true,")
        sb.append("value=$value,")
        sb.append("height=$height,")
        sb.append("isLegal=$isLegal")
        sb.append("isBalanced=${isBalanced()}")
        sb.append(")")
        return sb.toString()
    }
}

open class InternalNode<out T : LeafInfo>(
    override val weight: Int,
    override val height: Int,
    // Future design notes:
    // This should re-implemented with a linkedList implementation,
    // which has pointers to both prev and next nodes.
    // This way,
    // we can expose a simpler API
    // to manipulate the `children`.
    // This could have 2 great impacts:
    // - improve performance (this perf might actually be space perf rather than speed) when searching for children.
    // - reduce a lot of boilerplate code to handle children with `COW` semantics.
    val children: List<BTreeNode<T>>
) : BTreeNode<T>() {
    override val isEmpty: Boolean = children.isEmpty()

    override val isLegal: Boolean
        get() {
            //TODO:
            // 1. compute it once
            // 2. children.size > MAX_CHILDREN || children.size < MIN_CHILDREN
            // with the above condition, we have to change isBalanced() API, since it is a condition
            // where we cannot always meet.
            // Maybe we also need to distinct between legal and balanced nodes.
            if (children.size > MAX_CHILDREN || children.isEmpty()) return false
            val rootHeight = height
            for (node in children) if (node.height >= rootHeight) return false
            return true
        }

    //TODO: This op, can be improved,
    // similarly how Rope.subRope() is implemented.
    // Refine if there is time.
    // The algorithm in this function in not working correct.
    // though it is debatable if it is worth it to fix it,
    // as we have an equivalent operation on `Rope`.
    @Deprecated("Use the corresponding operator on `Rope`, where applicable")
    override fun subSequence(range: IntRange): BTreeNode<T> {
        return subSequence(range.first, range.last + 1)
    }

    private fun subSequence(startIndex: Int, endIndex: Int): BTreeNode<T> {
        checkRangeIndexes(startIndex, endIndex)
        val leaves = collectLeaves()
        var leftLeaf: LeafNode<T>? = null
        var leftIndexInLeftLeaf: Int? = null
        var rightLeaf: LeafNode<T>? = null
        var rightIndexInRightLeaf: Int? = null
        var len = 0
        for (leaf in leaves) {
            len += leaf.weight
            if (leftLeaf == null && startIndex <= len) {
                leftLeaf = leaf
                leftIndexInLeftLeaf = len - startIndex
            }
            if (rightLeaf == null && endIndex - 1 <= len) {
                rightLeaf = leaf
                rightIndexInRightLeaf = len - endIndex - 1
            }
            if (leftLeaf != null && rightLeaf != null) break
        }
        if (leftLeaf == null || leftIndexInLeftLeaf == null || rightIndexInRightLeaf == null || rightLeaf == null) {
            throw IndexOutOfBoundsException("startIndex:$startIndex, endIndex:$endIndex, length:${treeLength()}")
        }
        val leftBound = indexOf(leftLeaf)
        val rightBound = indexOf(rightLeaf)
        val inBetween = leaves.subList(leftBound + 1, rightBound - 1)
        val newLeftNode = leftLeaf.subSequence(leftIndexInLeftLeaf..leftLeaf.weight)
        val newRightNode = rightLeaf.subSequence(rightIndexInRightLeaf..rightLeaf.weight)
        val listWithNewLeftLeaf = inBetween.addWithCopyOnWrite(newLeftNode, 0)
        val newLeaves = listWithNewLeftLeaf.addWithCopyOnWrite(newRightNode, listWithNewLeftLeaf.size)
        return merge(newLeaves)
    }

    private fun throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex: Int, endIndex: Int): Nothing {
        throw IndexOutOfBoundsException("startIndex:$startIndex, endIndex:$endIndex, length:${treeLength()}")
    }

    override fun iterator(): Iterator<LeafNode<T>> {
        return BTreeNodeIterator(this)
    }

    open fun indexOf(child: BTreeNode<@UnsafeVariance T>): Int = children.indexOf(child)

    /**
     * Returns a new expanded [node][InternalNode] by a factor of 2,
     * or if the node is empty it returns an empty node.
     * This operation splits the tree in half and creates a new parent node for them.
     *
     * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
     */
    open fun expand(): InternalNode<T> {
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
    fun add(index: Int, child: BTreeNode<@UnsafeVariance T>): InternalNode<T> {
        checkElementIndex(index)
        require(children.size + 1 <= MAX_CHILDREN) { "node cannot hold more than:$MAX_CHILDREN children" }
        val newChildren = children.addWithCopyOnWrite(child, index)
        return unsafeCreateParent(newChildren)
    }

    fun addLast(child: BTreeNode<@UnsafeVariance T>): InternalNode<T> = add(children.size - 1, child)

    fun addFirst(child: BTreeNode<@UnsafeVariance T>): InternalNode<T> = add(0, child)

    fun addAll(index: Int, children: List<BTreeNode<@UnsafeVariance T>>): InternalNode<T> {
        checkElementIndex(index)
        require(this.children.size + children.size <= MAX_CHILDREN) {
            "node cannot hold more than:$MAX_CHILDREN children"
        }
        val newChildren = this.children.addWithCopyOnWrite(children, index)
        return unsafeCreateParent(newChildren)
    }

    operator fun set(index: Int, child: BTreeNode<@UnsafeVariance T>): InternalNode<T> {
        checkElementIndex(index)
        val newChildren = buildList {
            for (i in children.indices) {
                if (i == index) add(child) else add(children[i])
            }
        }
        return unsafeCreateParent(newChildren)
    }

    operator fun set(index: Int, children: List<BTreeNode<@UnsafeVariance T>>): InternalNode<T> {
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

    fun replace(old: BTreeNode<@UnsafeVariance T>, new: BTreeNode<@UnsafeVariance T>): InternalNode<T> {
        val newChildren = buildList {
            for (child in children) {
                if (child === old) add(new) else add(child)
            }
        }
        return unsafeCreateParent(newChildren)
    }

    fun deleteAt(index: Int): InternalNode<T> {
        checkElementIndex(index)
        val newChildren = buildList {
            for (i in children.indices) {
                if (i == index) continue
                add(children[i])
            }
        }
        if (newChildren.isEmpty()) return emptyInternalNode()
        return unsafeCreateParent(newChildren)
    }

    private fun checkElementIndex(index: Int) {
        if (index < 0 || index >= children.size || index >= MAX_CHILDREN) throw IndexOutOfBoundsException("index:$index")
    }

    private fun checkRangeIndexes(startIndex: Int, endIndex: Int) {
        if (startIndex < 0) throw IndexOutOfBoundsException("startIndex:$startIndex")
        if (endIndex < startIndex) {
            throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
        }
    }

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName(")
        sb.append("weight=$weight,")
        sb.append("height=$height,")
        sb.append("childrenSize=${children.size},")
        sb.append("isLegal=$isLegal")
        sb.append(")")
        return sb.toString()
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

internal object EmptyInternalNode : InternalNode<Nothing>(0, 0, emptyList()) {
    override val isEmpty: Boolean = true
    override val isLegal: Boolean = false

    override fun indexOf(child: BTreeNode<Nothing>): Int = -1
    override fun expand(): InternalNode<Nothing> = this
    override fun equals(other: Any?): Boolean = other is InternalNode<*> && other.isEmpty
}

//TODO: check if this pulls its weight
fun <T : LeafInfo> emptyBTreeNode(): BTreeNode<T> = EmptyInternalNode
fun <T : LeafInfo> emptyInternalNode(): InternalNode<T> = EmptyInternalNode

internal fun <T : LeafInfo> BTreeNode<T>.toStringDebug(): String = when (this) {
    is LeafNode -> this.toStringDebug()
    is InternalNode -> this.toStringDebug()
}

const val MIN_CHILDREN = 4
const val MAX_CHILDREN = 8

// --- XXXCopyOnWrite ---

fun <T : LeafInfo> List<BTreeNode<T>>.replaceWithCopyOnWrite(
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

fun <T : LeafInfo> List<BTreeNode<T>>.addWithCopyOnWrite(newNode: BTreeNode<T>, index: Int): List<BTreeNode<T>> {
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

fun <T : LeafInfo> List<BTreeNode<T>>.addWithCopyOnWrite(newNode: List<BTreeNode<T>>, index: Int): List<BTreeNode<T>> {
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
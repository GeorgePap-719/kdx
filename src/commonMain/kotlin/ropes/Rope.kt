package keb.ropes


fun Rope(value: String): Rope {
    val root = btreeOf(value)
    return Rope(root)
}

/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(private val root: BTreeNode) {

    /**
     * Returns the [Char] at the given [index] or `null` if the [index] is out of bounds of this rope.
     */
    operator fun get(index: Int): Char? {
        if (index < 0) return null
        return getImpl(index, root)
    }

    // variant of binary search.
    // * is able to skip effectively left subtrees if `index` is not
    //  -- in that part of the tree.
    // * uses a stack to keep reference to parent nodes, in case it
    //  -- needs to traverse the tree backwards.
    @Suppress("DuplicatedCode") //TODO: avoid dup
    private fun getImpl(index: Int, root: BTreeNode): Char? {
        var curIndex = index
        var curNode = root
        val stack = ArrayStack<IndexedInternalNode>(root.height)

        while (true) {
            when (curNode) {
                is LeafNode -> {
                    if (curIndex < curNode.weight) return curNode.value[curIndex] // fast-path
                    if (curNode === root) return null // single-node btree.
                    curIndex -= curNode.weight
                    val parent = stack.popOrNull()
                        ?: error("leaf:$curNode does not have a parent in stack")
                    // Iterate the next child and keep `self` reference in stack, since we
                    // need to allow a child to find its parent in stack in the case of "failure".
                    curNode = parent.nextChildAndKeepRefOrElse(stack) {
                        // If neither `parent` nor stack has a node to give back, then there are no more
                        // nodes to traverse. Technically, returning `null` here means we are in rightmost subtree.
                        stack.popOrNull() ?: return null
                    }
                }

                is InternalNode -> {
                    val node = if (curNode is IndexedInternalNode) curNode else curNode.indexed()
                    // push the current node, so we can always return as a fallback.
                    stack.push(node)
                    // If `index` is less than node's weight, then we know
                    // for use that `index` is in this subtree.
                    if (curIndex < node.weight) {
                        curNode = node.nextChildOrElse {
                            // At this point, `index` is out of bounds because we tried to traverse
                            // a non-existent "next" node, in an internal node where we are certain that
                            // `index` should be within this subtree. Technically, this happens because
                            // when we are in the rightmost leafNode, we cannot be sure there is not a
                            // "next" leaf. We have to traverse the tree backwards and check explicitly.
                            return null
                        }
                        continue
                    }
                    if (node.index == 0) { // leftmost child
                        //TODO: we do not calculate here proper subtraction
                        // Bug is in computing of weight.
                        curIndex -= node.weight
                        // No need to check leaves on leftmost child,
                        // since we are sure `index` is not here.
                        if (!node.tryIncIndex()) { // skip first-child
                            // No more children to traverse in this node, go to the parent node.
                            // If either node is the root or there is no parent, then it means there
                            // are no more nodes to traverse, and `index` is out of bounds.
                            curNode = node.findParentInStack(stack) ?: return null
                            continue
                        }
                    }
                    curNode = node.nextChildOrElse {
                        // If stack returns `null`, there are no more nodes to traverse.
                        // In that case, we can safely assume we are out of bounds.
                        node.findParentInStack(stack) ?: return null
                    }
                }
            }
        }
    }

    private fun BTreeNode.findParentInStack(stack: ArrayStack<IndexedInternalNode>): IndexedInternalNode? {
        var stackNode = stack.popOrNull() ?: return null
        while (stackNode === this) {
            stackNode = stack.popOrNull() ?: return null
        }
        return stackNode
    }

    private inline fun IndexedInternalNode.nextChildAndKeepRefOrElse(
        stack: ArrayStack<IndexedInternalNode>,
        action: () -> BTreeNode
    ): BTreeNode = nextChildOrNull.let {
        if (it == null) {
            action()
        } else {
            stack.push(this)
            return it
        }
    }

    // if index > length() -> will append char
    fun insert(index: Int, char: Char): Rope {
        if (index == 0) {
            TODO("addFirst()")
        }
        TODO()
    }

    fun addFirst(input: Char): Rope {
        // - find the first leafNode and check if it has any more space left
        val leftmostChild = root.findLeftmostChild()
        if (leftmostChild.weight + 1 <= MAX_SIZE_LEAF) {
            if (leftmostChild === root) return Rope(leftmostChild.value + input) // fast-path
            val newChild = LeafNode(leftmostChild.value + input)

        }
        // - if yes then inserted there and rebuild where necessary.
        // - if not, check if parent (internal node) has any space left for one more child
        // - if yes, then insert child in start and rebuild where necessary
        // - if not, traverse the tree backwards until we find an empty spot.
        // - rebuilding may even create a new root above old one.
        TODO()
    }

    // note: this may even return root itself
    private fun BTreeNode.findLeftmostChild(): LeafNode {
        var curNode = this
        while (true) {
            when (curNode) {
                is LeafNode -> return curNode
                is InternalNode -> curNode = curNode.children.first()
            }
        }
    }

    private inline fun <R> getBaseImpl(
        index: Int,
        stack: ArrayStack<IndexedInternalNode>,
        onOutOfBounds: () -> R,

        ) {

    }


    inner class RopeIterator(root: BTreeNode) {
        private val links = mutableMapOf<BTreeNode, BTreeNode>() // child || parent
        private var next: BTreeNode? = null

        private val stack = ArrayStack<IndexedInternalNode>(root.height)
        var curIndex = 0
        var curNode = root


        @Suppress("DuplicatedCode")
        fun hasNext(): Boolean {
            var curNode = curNode
            while (true) {
                when (curNode) {
                    is LeafNode -> {

                    }

                    is InternalNode -> {
                        val indexedNode = if (curNode is IndexedInternalNode) curNode else curNode.indexed()
                        stack.push(indexedNode)
                        while (indexedNode.index < indexedNode.children.size) {

                        }
                    }
                }
            }
            TODO("do we need here so bad a lazy iteration?")
        }

        private fun link(child: BTreeNode, parent: BTreeNode) {
            links[child] = parent
        }

        fun next(): BTreeNode {
            return this.next ?: throw NoSuchElementException()
        }
    }

    fun length(): Int {
        var curNode = root
        var length = 0
        while (true) {
            when (curNode) {
                is LeafNode -> return length + curNode.weight
                is InternalNode -> {
                    if (curNode.children.size == 1) return length + curNode.weight // only left-child
                    val rightMostNode = curNode.children.last()
                    // accumulate lef-subtree weight and move on
                    length += curNode.weight
                    curNode = rightMostNode
                    continue
                }
            }
        }
    }


    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String = root.toStringDebug()

}

// btree utils

internal fun InternalNode.indexed(): IndexedInternalNode {
    return IndexedInternalNode(weight, height, children)
}

internal inline fun IndexedInternalNode.nextChildOrElse(action: () -> BTreeNode): BTreeNode {
    return nextChildOrNull ?: action()
}

/**
 * A helper class to iterate through an internal node's children, similarly to an iterator.
 */
internal class IndexedInternalNode(
    weight: Int,
    height: Int,
    children: List<BTreeNode>,
) : InternalNode(weight, height, children) {
    var index = 0
        private set

    val nextChildOrNull: BTreeNode? get() = if (hasNextChild()) nextChild() else null

    fun nextChild(): BTreeNode {
        if (index >= children.size) throw NoSuchElementException()
        return children[index++]
    }

    fun hasNextChild(): Boolean {
        return index < children.size
    }

    fun tryIncIndex(): Boolean {
        if (index == children.lastIndex) return false
        index++
        return true
    }
}

// string-btree utils

fun btreeOf(input: String): BTreeNode {
    return splitIntoNodes(input)
}

private fun splitIntoNodes(input: String): BTreeNode {
    if (input.length <= MAX_SIZE_LEAF) return LeafNode(input)
    val leaves = buildList {
        var index = 0
        while (index < input.length) {
            val leafValue = input.substring(index, minOf(index + MAX_SIZE_LEAF, input.length))
            add(LeafNode(leafValue))
            index += MAX_SIZE_LEAF
        }
    }
    return merge(leaves)
}
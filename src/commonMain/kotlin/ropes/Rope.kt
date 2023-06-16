package keb.ropes

import keb.assert


fun Rope(value: String): Rope {
    val root = btreeOf(value)
    return Rope(root)
}

/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(private val root: BTreeNode) {

    init {
        assert { root.isBalanced() }
    }

    /**
     * Returns the [Char] at the given [index] or `null` if the [index] is out of bounds of this rope.
     */
    operator fun get(index: Int): Char? =
        getImpl(
            index = index,
            root = root,
            onOutOfBounds = { return null },
            onElementRetrieved = { _, _, element -> return element }
        )

    // if index > length() -> will append char
    fun insert(index: Int, char: Char): Rope {
        if (index == 0) {
            return addFirst(char)
        }
        TODO()
    }

    // - find the first leafNode and check if it has any more space left
    // - if yes then inserted there and rebuild where necessary.
    // - if not, check if parent (internal node) has any space left for one more child
    // - if yes, then insert child in start and rebuild where necessary
    // - if not, split and merge.
    // - rebuilding may even create a new root above old one.
    fun addFirst(input: Char): Rope {
        val iterator = SingleIndexRopeIteratorWithHistory(root, 0)
        if (!iterator.hasNext()) error("unexpected")
        val leftmostChild = iterator.currentLeaf
        if (leftmostChild.weight + 1 <= MAX_SIZE_LEAF) {
            if (leftmostChild === root) return Rope(input + leftmostChild.value) // fast-path
            val newChild = LeafNode(input + leftmostChild.value)
            val newTree = rebuildTree(leftmostChild, newChild, iterator)
            return Rope(newTree)
        }
        return addFirstSlow(leftmostChild, iterator, input)
    }

    // split and merge
    private fun addFirstSlow(
        leftmostChild: LeafNode,
        iterator: SingleIndexRopeIteratorWithHistory,
        input: Char
    ): Rope {
        val parent = iterator.findParent(leftmostChild) ?: error("unexpected")
        val newChild = LeafNode(input.toString())
        val newParent = addChildFirstOrExpand(newChild, parent)
        val newTree = rebuildTree(parent, newParent, iterator)
        return Rope(newTree)
    }

    private fun addChildFirstOrExpand(child: LeafNode, parent: InternalNode): InternalNode {
        val newNode = parent.tryAddChild(child, 0)
        if (newNode != null) return newNode
        val newParent = expandInternalNode(parent)
        return newParent.tryAddChild(child, 0) ?: error("unexpected")
    }

    private fun expand(node: BTreeNode): BTreeNode {
        return when (node) {
            is LeafNode -> expandLeaf(node)
            is InternalNode -> expandInternalNode(node)
        }
    }

    private fun expandLeaf(leaf: LeafNode): InternalNode {
        val half = leaf.weight / 2
        val left = LeafNode(leaf.value.substring(0, half))
        val right = LeafNode(leaf.value.substring(half))
        return merge(left, right)
    }

    private fun expandInternalNode(node: InternalNode): InternalNode {
        val half = node.children.size / 2
        val left = node.children.subList(0, half)
        val right = node.children.subList(half, node.children.size)
        val leftParent = merge(left)
        val rightParent = merge(right)
        return merge(leftParent, rightParent)
    }

    private fun rebuildTree(
        oldNode: BTreeNode,
        newNode: BTreeNode,
        iterator: SingleIndexRopeIteratorWithHistory
    ): BTreeNode {
        var old = oldNode
        var new = newNode
        if (old === root) return newNode
        while (true) {
            // for non-root nodes, findParent() should always return a parent.
            val parent = iterator.findParent(old) ?: error("unexpected:TODO") //TODO: should we just return new here?
            val newParent = parent.replaceChildWithCopyOnWrite(old, new)
            old = parent
            new = newParent
            if (old === root) return new
        }
    }

    //TODO: check if we really need this.
    private inline fun <R> getWithSafeIndex(
        /* The target index to retrieve. */
        index: Int,
        /* The tree which we iterate. */
        root: BTreeNode,
        /* The stack which keeps references to parent nodes. */
        stack: ArrayStack<IndexedInternalNode> = defaultStack(),
        /* This lambda is invoked when the target element has
        been retrieved successfully. */
        onElementRetrieved: (
            leaf: LeafNode,
            i: Int,
            element: Char
        ) -> R,
        /* This lambda is invoked when we retrieve the next
        child-node by a preceding nextChild() call. */
        onNextChild: (next: BTreeNode) -> Unit = {}
    ): R = getImpl(
        index = index,
        root = root,
        stack = stack,
        // This fun should be invoked only for safe `index`.
        onOutOfBounds = { error("unexpected out-of-bounds error for index:$index") },
        onElementRetrieved = onElementRetrieved,
        onNextChild = onNextChild
    )

    // abstract get implementation.
    // variant of binary search.
    // * is able to skip effectively left subtrees if `index` is not
    //  -- in that part of the tree.
    // * uses a stack to keep reference to parent nodes, in case it
    //  -- needs to traverse the tree backwards.
    private inline fun <R> getImpl(
        /* The target index to retrieve. */
        index: Int,
        /* The tree which we iterate. */
        root: BTreeNode,
        /* The stack which keeps references to parent nodes. */
        stack: ArrayStack<IndexedInternalNode> = defaultStack(),
        /* This lambda is invoked when the target index is
        out of bounds for the current in tree. */
        onOutOfBounds: () -> R,
        /* This lambda is invoked when the target element has
        been retrieved successfully. */
        onElementRetrieved: (
            leaf: LeafNode,
            i: Int,
            element: Char
        ) -> R,
        /* This lambda is invoked when we retrieve the next
        child-node by a preceding nextChild() call. */
        onNextChild: (next: BTreeNode) -> Unit = {}
    ): R {
        if (index < 0) return onOutOfBounds() // rope does not support negative `index`
        var curIndex = index
        var curNode = root
        while (true) {
            when (curNode) {
                is LeafNode -> {
                    if (curIndex < curNode.weight) {
                        return onElementRetrieved(curNode, curIndex, curNode.value[curIndex]) // fast-path
                    }
                    if (curNode === root) return onOutOfBounds() // single-node btree.
                    curIndex -= curNode.weight
                    val parent = stack.popOrNull()
                        ?: error("leaf:$curNode does not have a parent in stack")
                    // Iterate the next child and keep `self` reference in stack, since we
                    // need to allow a child to find its parent in stack in the case of "failure".
                    curNode = parent.nextChildAndKeepRefOrElse(stack) {
                        // If neither `parent` nor stack has a node to give back, then there are no more
                        // nodes to traverse. Technically, returning `null` here means we are in rightmost subtree.
                        stack.popOrNull() ?: return onOutOfBounds()
                    }
                    onNextChild(curNode)
                }

                is InternalNode -> {
                    val node = if (curNode is IndexedInternalNode) curNode else curNode.indexed()
                    // push the current node, so we can always return as a fallback.
                    stack.push(node)
                    // If `index` is less than node's weight, then we know
                    // for use that `index` is in this subtree.
                    if (curIndex < node.weight) {
                        curNode = node.nextChildOrElse {
                            // At this point, `index` is out of bounds because we tried to iterate
                            // a non-existent "next" node, in an internal node where we are certain that
                            // `index` should be within this subtree. Technically, this happens because
                            // when we are in the rightmost leafNode, we cannot be sure there is not a
                            // "next" leaf. We have to iterate the tree backwards and check explicitly.
                            return onOutOfBounds()
                        }
                        onNextChild(curNode)
                        continue
                    }
                    if (node.index == 0) { // leftmost child
                        curIndex -= node.weight
                        // No need to check leaves on leftmost child,
                        // since we are sure `index` is not here.
                        if (!node.tryIncIndex()) { // skip first-child
                            // No more children to traverse in this node, go to the parent node.
                            // If either node is the root or there is no parent, then it means there
                            // are no more nodes to traverse, and `index` is out of bounds.
                            curNode = node.findParentInStack(stack) ?: return onOutOfBounds()
                            onNextChild(curNode)
                            continue
                        }
                    }
                    curNode = node.nextChildOrElse {
                        // If stack returns `null`, there are no more nodes to iterate.
                        // In that case, we can safely assume we are out of bounds.
                        node.findParentInStack(stack) ?: return onOutOfBounds()
                    }
                    onNextChild(curNode)
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

    private fun defaultStack(): ArrayStack<IndexedInternalNode> = ArrayStack(root.height)

    inner class SingleIndexRopeIteratorWithHistory(private val root: BTreeNode, index: Int) {
        init {
            require(index > -1) { "index cannot be negative, but got:$index" }
        }

        private val links = mutableMapOf<BTreeNode, InternalNode>() // child || parent
        private val stack = PeekableArrayStack<BTreeNode>(root.height)

        init {
            //TODO: add explanation
            pushInStack(root)
        }

        private var curIndex = index
        private var curNode = root

        // - char -> value is retrieved successfully.
        // - null -> element is retrieved.
        // - CLOSED -> we are out of bounds and further next() calls are not allowed.
        private var nextOrClosed: Any? = null // Char || null || CLOSED

        private inline fun nextOrIfClosed(onClosedAction: () -> Nothing): Char? = nextOrClosed.let {
            if (it == CLOSED) {
                onClosedAction()
            } else {
                it as Char?
            }
        }

        val currentLeaf: LeafNode
            get() = curNode as? LeafNode ?: error("should be invoked after the first iteration")

        fun findParent(child: BTreeNode): InternalNode? = links[child]

        fun hasNext(): Boolean {
            val next = nextOrIfClosed { return false }
            return if (next == null) {
                tryGetNext() != null
            } else {
                true
            }
        }

        fun next(): Char {
            val next = nextOrIfClosed { throw NoSuchElementException() }
                ?: return tryGetNext()
                    ?: throw NoSuchElementException()
            cleanNext()
            return next
        }

        /**
         * Tries to get the next `Char` if this iterator is not marked as closed. If this iteration
         * returns null, then it marks the iterator as closed.
         */
        private fun tryGetNext(): Char? {
            if (nextOrClosed === CLOSED) return null
            nextImpl()
            if (nextOrClosed == null) {
                markAsClosed()
                return null
            }
            return nextOrClosed as Char
        }

        /**
         * Marks the iterator as closed and forbids any other subsequent [next] calls.
         */
        private fun markAsClosed() {
            nextOrClosed = CLOSED
        }

        /**
         * Cleans the `next` variable.
         */
        private fun cleanNext() {
            nextOrClosed = null
        }

        private fun nextImpl() {
            check(nextOrClosed == null) {
                "either this iterator is closed or the `nextOrClosed` has not been cleaned after previous retrieval"
            }
            getImpl(
                index = curIndex, // curIndex++
                root = curNode,
                onOutOfBounds = { nextOrClosed = null },
                onElementRetrieved = { leaf, i, element ->
                    pushInStack(leaf)
                    curIndex = i
                    curNode = leaf
                    nextOrClosed = element
                },
                //TODO: add comments
                onNextChild = {
                    pushInStack(it)
                    it.findParentInStackAndLink()
                }
            )
        }

        private fun pushInStack(child: BTreeNode) {
            stack.push(child)
        }

        private fun BTreeNode.findParentInStackAndLink() {
            if (this === root) return
            stack.forEach {
                if (it === this) return@forEach
                val parent = it as? InternalNode ?: return@forEach
                if (!parent.children.contains(this)) return@forEach
                links[this] = parent // link
            }
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

private val CLOSED = keb.Symbol("CLOSED")
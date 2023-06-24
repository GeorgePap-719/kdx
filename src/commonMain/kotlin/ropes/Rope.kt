package keb.ropes

import keb.assert

fun Rope(value: String): Rope {
    val root = btreeOf(value)
    return Rope(root)
}

fun emptyRope(): Rope {
    return Rope(btreeOf(""))
}

/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(private val root: BTreeNode) {

    init {
        assert { root.isBalanced() }
    }

    //TODO: we can also improve this too keep the tree wide.
    fun concat(other: Rope): Rope {
        val left = root
        val right = other.root
        val newRope = createParent(left, right)
        return Rope(newRope)
    }

    fun split(index: Int): Pair<Rope, Rope> {
        TODO()
    }

    @Deprecated("use len", ReplaceWith("length"))
    fun length0(): Int {
        var len = 0
        for (leaf in root) len += leaf.weight
        return len
    }

    val length: Int by lazy { if (root is LeafNode) root.weight else lenImpl(root) }

    //TODO: research if we can avoid big tail-rec
    private fun lenImpl(curNode: BTreeNode): Int {
        return when (curNode) {
            is LeafNode -> curNode.weight
            is InternalNode -> {
                val children = curNode.children
                var curLen = 0
                curLen += curNode.weight
                for (index in children.indices) {
                    if (index == 0) continue
                    curLen += lenImpl(children[index])
                }
                curLen
            }
        }
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

    fun indexOf(element: Char): Int {
        var index = 0
        for (leaf in root) {
            for (c in leaf.value) {
                if (c == element) return index
                index++
            }
        }
        return -1
    }

    fun subRope(startIndex: Int) {
        TODO()
    }

    fun subRope(startIndex: Int, endIndex: Int): Rope {
        checkPositionIndex(startIndex)
        TODO()
    }

    // endIndex exclusive
    fun deleteAt(startIndex: Int, endIndex: Int): Rope {
        TODO()
    }

    fun deleteAt(index: Int): Rope {
        checkPositionIndex(index)
        val iterator = SingleIndexRopeIteratorWithHistory(root, index)
        if (!iterator.hasNext()) throw IndexOutOfBoundsException("index:$index, length:$length")
        val leaf = iterator.currentLeaf // leaf where index is found
        val i = iterator.currentIndex // index in leaf
        val newLeaf = leaf.deleteAt(i)
        val newTree = rebuildTreeCleaningEmptyNodes(leaf, newLeaf, iterator)
        return Rope(newTree)
    }

    private fun rebuildTreeCleaningEmptyNodes(
        oldNode: BTreeNode,
        newNode: BTreeNode,
        iterator: SingleIndexRopeIteratorWithHistory
    ): BTreeNode {
        if (oldNode === root && newNode.isEmpty) return emptyBtree()
        var old = oldNode
        var new: BTreeNode? = newNode
        while (true) {
            if (new?.isEmpty == true) new = null // mark empty nodes as null to clean them out
            if (new != null) return rebuildTree(old, new, iterator)
            // for non-root nodes, findParent() should always return a parent.
            val parent = iterator.findParent(old) ?: error("unexpected")
            val pos = parent.indexOf(old)
            new = parent.deleteAt(pos)
            old = parent
            if (old === root) return new ?: old
        }
    }

    // throws for index -1 && out-of-bounds
    //
    // - find target leafNode and check if it has any more space left
    // - if yes then inserted there and rebuild where necessary.
    // - if not, check if parent (internal node) has any space left for one more child
    // - if yes, then insert child in start and rebuild where necessary
    // - if not, split and merge.
    // - rebuilding creates a new root and replaces old one.
    //TODO: One improvement would be to check for more parents up ahead if we can split them,
    // but at this point it is non-trivial and not worth it time-wise.
    fun insert(index: Int, element: String): Rope {
        checkPositionIndex(index)
        val iterator = SingleIndexRopeIteratorWithHistory(root, index)
        // Try to find the target `index`, since we need to locate
        // it and start adding after that `index`.
        if (!iterator.hasNext()) {
            // we allow for inserting on + 1 after last-index, since these are
            // the append() operations.
            if (index != length) throw IndexOutOfBoundsException("index:$index, length:$length")
        }
        val leaf = iterator.currentLeaf // leaf where index is found
        val i = iterator.currentIndex // index in leaf
        // If the leaf which contains the index has enough space for adding
        // the element, create new leaf and rebuild tree.
        if (leaf.weight + element.length <= MAX_SIZE_LEAF) { // fast-path
            val newChild = leaf.add(i, element)
            if (leaf === root) return Rope(newChild)
            val newTree = rebuildTree(leaf, newChild, iterator)
            return Rope(newTree)
        }
        // Add the element into leaf and expand (split and merge) as necessary.
        val newChildren = leaf.expandableAdd(i, element)
        if (leaf === root) {
            val newParent = createParent(newChildren)
            return Rope(newParent)
        }
        // At this point, we should always find a parent, since we are in a leaf
        // and hasNext() returned `true`.
        val parent = iterator.findParent(leaf) ?: error("unexpected")
        val pos = parent.indexOf(leaf)
        // If there is space in the parent, add new leaf/s to keep the tree wide
        // as much as possible.
        if (newChildren.size + parent.children.size - 1 <= MAX_CHILDREN) {
            val newParent = parent.set(pos, newChildren)
            val newTree = rebuildTree(parent, newParent, iterator)
            return Rope(newTree)
        }
        // Replace leaf with new node.
        // Note, at this point, we deepen the tree rather than keep it wide.
        val newChild = merge(newChildren)
        val newParent = parent.set(pos, newChild)
        val newTree = rebuildTree(parent, newParent, iterator)
        return Rope(newTree)
    }

    private fun rebuildTree(
        oldNode: BTreeNode,
        newNode: BTreeNode,
        iterator: SingleIndexRopeIteratorWithHistory
    ): BTreeNode {
        if (oldNode === root) return newNode
        var old = oldNode
        var new = newNode
        while (true) {
            // for non-root nodes, findParent() should always return a parent.
            val parent = iterator.findParent(old) ?: error("unexpected")
            new = parent.replace(old, new)
            old = parent
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
                        return onElementRetrieved(curNode, curIndex, curNode.value[curIndex])
                    }
                    if (curNode === root) return onOutOfBounds() // single-node btree.
                    curIndex -= curNode.weight //TODO: explain this
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

    private fun defaultStack(): ArrayStack<IndexedInternalNode> = ArrayStack(root.height)

    open inner class RopeIterator(private val root: BTreeNode, startIndex: Int) {
        init {
            checkPositionIndex(startIndex)
            // This implementation has second `init`.
        }

        private val links = mutableMapOf<BTreeNode, InternalNode>() // child || parent
        private val stack = PeekableArrayStack<BTreeNode>(root.height)

        init {
            //TODO: add explanation
            pushInStack(root)
        }

        private var curIndex = startIndex
        private var nextIndex = curIndex
        private var curNode = root

        val currentIndex get() = curIndex

        // - char -> value is found successfully.
        // - null ->  indicates the absence of pre-received result.
        // - CLOSED -> we are out of bounds and further next() calls are not allowed.
        private var nextOrClosed: Any? = null // Char || null || CLOSED

        val currentLeaf: LeafNode
            get() = curNode as? LeafNode ?: error("should be invoked after the first iteration")

        fun findParent(child: BTreeNode): InternalNode? = links[child]

        // `hasNext()` is a special get() opearation.
        open operator fun hasNext(): Boolean {
            if (nextOrClosed === CLOSED) throw NoSuchElementException(DEFAULT_CLOSED_MESSAGE)
            return getImpl(
                index = nextIndex,
                root = curNode,
                onOutOfBounds = { onOutOfBoundsHasNext() },
                onElementRetrieved = { leaf, i, element ->
                    pushInStack(leaf)
                    curIndex = i
                    curNode = leaf
                    nextOrClosed = element
                    nextIndex = ++curIndex
                    true
                },
                //TODO: add comments
                onNextChild = {
                    pushInStack(it)
                    it.findParentInStackAndLink()
                }
            )
        }

        private fun onOutOfBoundsHasNext(): Boolean {
            nextOrClosed = CLOSED
            return false
        }

        open operator fun next(): Char {
            // Read the already received result or `null` if [hasNext] has not been invoked yet.
            val result = nextOrClosed
            check(result != null) { "`hasNext()` has not been invoked" }
            nextOrClosed = null
            // Is this iterator closed?
            if (nextOrClosed === CLOSED) throw NoSuchElementException(DEFAULT_CLOSED_MESSAGE)
            return result as Char
        }

        /**
         * Tries to get the next `Char` if this iterator is not marked as closed. If this iteration
         * returns null, then it marks the iterator as closed.
         */
//        private fun tryGetNext(): Char? {
//            if (nextOrClosed === CLOSED) return null
//            nextImpl()
//            if (nextOrClosed == null) {
//                markClosed()
//                return null
//            }
//            return nextOrClosed as Char
//        }

        /**
         * Marks the iterator as closed and forbids any other subsequent [next] calls.
         */
        protected fun markClosed() {
            nextOrClosed = CLOSED
        }

        /**
         * Cleans the `next` variable.
         */
        protected fun cleanNext() {
            nextOrClosed = null
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

    inner class SingleIndexRopeIteratorWithHistory(private val root: BTreeNode, index: Int) {
        init {
            checkPositionIndex(index)
            // This implementation has second `init`.
        }

        private val links = mutableMapOf<BTreeNode, InternalNode>() // child || parent
        private val stack = PeekableArrayStack<BTreeNode>(root.height)

        init {
            //TODO: add explanation
            pushInStack(root)
        }

        private var curIndex = index
        private var curNode = root

        val currentIndex get() = curIndex

        // - char -> value is found successfully.
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

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String = root.toStringDebug()

    private fun checkPositionIndex(index: Int) {
        if (index < 0) throw IndexOutOfBoundsException("index:$index")
    }
}

fun Rope.insert(index: Int, element: Char): Rope = insert(index, element.toString())

// btree utils

internal fun InternalNode.indexed(): IndexedInternalNode {
    return IndexedInternalNode(weight, height, children)
}

internal inline fun IndexedInternalNode.nextChildOrElse(action: () -> BTreeNode): BTreeNode {
    return nextChildOrNull ?: action()
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

fun emptyBtree(): BTreeNode {
    return LeafNode("")
}

private fun splitIntoNodes(input: String): BTreeNode {
    if (input.length <= MAX_SIZE_LEAF) return LeafNode(input)
    val leaves = splitIntoLeaves(input)
    return merge(leaves)
}

private fun splitIntoLeaves(input: String): List<LeafNode> {
    return buildList {
        var index = 0
        while (index < input.length) {
            val leafValue = input.substring(index, minOf(index + MAX_SIZE_LEAF, input.length))
            add(LeafNode(leafValue))
            index += MAX_SIZE_LEAF
        }
    }
}

// ------ string-leaf utils ------

private fun expandLeaf(leaf: LeafNode): InternalNode {
    val half = leaf.weight / 2
    val left = LeafNode(leaf.value.substring(0, half))
    val right = LeafNode(leaf.value.substring(half))
    return merge(left, right)
}

// ------ addXXX ------

// - Adds the element into the leaf and expands as necessary. Returns the resulting leaves.
//
// This operation creates one resulting string from the element and this leaf. This can
// be expensive in most cases. One solution is to avoid creating a big string by creating
// leaf nodes on the spot. Though, this is easier than done.
//TODO: research this if there is time.
private fun LeafNode.expandableAdd(index: Int, element: String): List<LeafNode> {
    checkValueIndex(index, this)
    val newLen = value.length + element.length
    if (newLen <= MAX_SIZE_LEAF) return listOf(add(index, element))
    val newValue = value.add(index, element)
    return splitIntoLeaves(newValue)
}

private fun LeafNode.add(index: Int, element: Char): LeafNode = add(index, element.toString())

/**
 * Returns a new leaf with the specified [element] inserted at the specified [index].
 *
 * @throws IndexOutOfBoundsException if [index] is greater than or equals to the length of this child.
 * @throws IllegalArgumentException if the resulting length exceeds the maximum size of a leaf.
 */
private fun LeafNode.add(index: Int, element: String): LeafNode {
    checkValueIndex(index, this)
    val newLen = value.length + element.length
    require(newLen <= MAX_SIZE_LEAF) { "max size of a leaf is:$MAX_SIZE_LEAF, but got:$newLen" }
    if (index == 0) return LeafNode(element + value)
    if (index == value.lastIndex + 1) return LeafNode(value + element)
    val newValue = value.add(index, element)
    return LeafNode(newValue)
}

private fun String.add(index: Int, element: String): String = buildString {
    val str = this@add
    for (i in str.indices) {
        if (i == index) append(element)
        append(str[i])
    }
    if (index == str.length) append(element)
}

private fun String.add(index: Int, element: Char): String = buildString {
    val str = this@add
    for (i in str.indices) {
        if (i == index) append(element)
        append(str[i])
    }
    if (index == str.length) append(element)
}

// ------ deleteXXX ------

private inline fun LeafNode.deleteAtAndIfEmpty(index: Int, onEmpty: () -> LeafNode): LeafNode {
    checkElementIndex(index, this)
    val newValue = value.deleteAt(index)
    if (newValue.isEmpty()) return onEmpty()
    return LeafNode(newValue)
}

private fun LeafNode.deleteAt(index: Int): LeafNode {
    checkElementIndex(index, this)
    return LeafNode(value.deleteAt(index))
}

private fun String.deleteAt(index: Int): String = buildString {
    val str = this@deleteAt
    for (i in str.indices) {
        if (i == index) continue
        append(str[i])
    }
}

// Internal result for [SingleIndexRopeIteratorWithHistory.nextOrClosed]
private val CLOSED = keb.Symbol("CLOSED")

//

private const val DEFAULT_CLOSED_MESSAGE = "iterator was closed"

private fun checkValueIndex(index: Int, leaf: LeafNode) {
    if (index < 0 || index > leaf.value.lastIndex + 1) { // it is acceptable for an index to be right after the last-index
        throw IndexOutOfBoundsException("index:$index, leaf-length:${leaf.value.length}")
    }
}

private fun checkElementIndex(index: Int, leaf: LeafNode) {
    if (index < 0 || index > leaf.value.lastIndex) {
        throw IndexOutOfBoundsException("index:$index, leaf-length:${leaf.weight}")
    }
}
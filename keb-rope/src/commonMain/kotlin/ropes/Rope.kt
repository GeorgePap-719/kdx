package keb.ropes

import keb.assert
import keb.internal.ArrayStack
import keb.internal.PeekableArrayStack

fun Rope(value: String): Rope {
    val root = ropeNodeOf(value)
    return Rope(root)
}

fun emptyRope(): Rope = Rope(emptyRopeNode())

class Rope(private val root: RopeNode) {
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

    val length: Int by lazy { if (root is LeafNode) root.weight else lenImpl(root) }

    //TODO: research if we can avoid big tail-rec
    private fun lenImpl(curNode: RopeNode): Int {
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

    fun subRope(index: Int): Rope {
        TODO()
    }

    // xxxIndex variables, need rename for sure.
    // `endIndex` is exclusive
    @Suppress("DuplicatedCode")
    fun subRope(startIndex: Int, endIndex: Int): Rope {
        checkRangeIndexes(startIndex, endIndex)
        // 1. get left and right positions
        val leftIterator = SingleElementRopeIterator(root, startIndex)
        if (!leftIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val leftLeaf = leftIterator.currentLeaf // leaf where index is found
        val leftIndex = leftIterator.currentIndex // index in leaf
        val rightIterator = SingleElementRopeIterator(root, endIndex - 1)
        if (!rightIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val rightLeaf = rightIterator.currentLeaf
        val rightIndex = rightIterator.currentIndex
        // --
        if (leftLeaf === rightLeaf) {
            val newLeaf = leftLeaf.value.subStringLeaf(leftIndex, rightIndex + 1)
            return Rope(RopeLeafNode(newLeaf))
        }
        val parent = leftIterator.findParent(leftLeaf) ?: error("unexpected")
        // find common parent to both leaves
        val commonParent = if (parent.contains(rightLeaf)) {
            parent
        } else {
            findCommonParent(leftIterator, leftLeaf, rightIterator, rightLeaf)
        }
        val newTree = buildTreeFromStartAndEndIndex(leftIndex, leftLeaf, rightIndex, rightLeaf, commonParent)
        return Rope(newTree)
    }

    private fun buildTreeFromStartAndEndIndex(
        leftIndex: Int,
        leftLeaf: RopeLeafNode,
        rightIndex: Int,
        rightLeaf: RopeLeafNode,
        parent: RopeNode
    ): RopeNode {
        val leaves = parent.collectLeaves()
        val leftLeafIndex = leaves.indexOf(leftLeaf)
        val rightLeafIndex = leaves.indexOf(rightLeaf)
        val newTree = buildBTree {
            for (i in leftLeafIndex..rightLeafIndex) {
                val child = leaves[i]
                when (i) {
                    leftLeafIndex -> {
                        val newLeaf = child.value.subStringLeaf(leftIndex)
                        add(RopeLeafNode(newLeaf))
                    }

                    rightLeafIndex -> {
                        val newLeaf = child.value.subStringLeaf(0, rightIndex)
                        add(RopeLeafNode(newLeaf))
                    }

                    else -> {
                        // In-between leaves can be added as they are,
                        // since they in range: startIndex < leaf < endIndex.
                        addAll(child.collectLeaves())
                    }
                }
            }
        }
        return newTree
    }

    private fun findCommonParent(
        leftIterator: RopeIteratorWithHistory,
        leftLeafNode: RopeLeafNode,
        rightIterator: RopeIteratorWithHistory,
        rightLeafNode: RopeLeafNode
    ): RopeNode {
        var leftParent: RopeNode = leftLeafNode
        var rightParent: RopeNode = rightLeafNode
        while (true) {
            leftParent = leftIterator.findParent(leftParent) ?: error("unexpected")
            rightParent = rightIterator.findParent(rightParent) ?: error("unexpected")
            if (leftParent === rightParent) return leftParent
        }
    }

    // Wiki has a better idea on how deleteAt should work.
    // Probably will follow that, but first we need subRope operation.
    // endIndex exclusive
    fun deleteAt(startIndex: Int, endIndex: Int): Rope {
        checkPositionIndex(startIndex)
        return when (root) {
            is RopeLeafNode -> {
                val leaf = root.value
                if (endIndex - 1 > leaf.lastIndex) {
                    throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
                }
                val newLeaf = leaf.removeRange(startIndex, endIndex)
                Rope(RopeLeafNode(newLeaf))
            }

            is RopeInternalNode -> deleteAtWithRootAsInternalNode(root, startIndex, endIndex)
        }
    }

    private fun deleteAtWithRootAsInternalNode(root: RopeInternalNode, startIndex: Int, endIndex: Int): Rope {
        // 1. get left and right positions
        val leftIterator = RopeIterator(root, startIndex)
        if (!leftIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val leftLeaf = leftIterator.currentLeaf // leaf where index is found
        val leftI = leftIterator.currentIndex // index in leaf
        val rightIterator = SingleElementRopeIterator(root, endIndex - 1)
        if (!rightIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val rightLeaf = rightIterator.currentLeaf
        val rightI = rightIterator.currentIndex
        // 2. check if they are in the same leaf
        if (leftLeaf === rightLeaf) {
            val newValue = leftLeaf.value.removeRange(leftI, rightI) // `endIndex` is exclusive here
            val newLeaf = RopeLeafNode(newValue)
            val newTree = rebuildTreeCleaningEmptyNodes(leftLeaf, newLeaf, leftIterator)
            return Rope(newTree)
        }
        // 3. if not, then gl hf
        // -- First, we need to find how much of "index" each leaf contains, then subtract it.
//        val newRoot = root.mutate {
        var index = startIndex
        var curLeaf = leftLeaf
        while (index < endIndex) {
            val range = if (index + curLeaf.value.lastIndex < endIndex) {
                index..curLeaf.value.lastIndex
            } else {
                index..<endIndex
            }
            val newLeaf = curLeaf.value.removeRange(range)
            val newTree = rebuildTreeCleaningEmptyNodes(curLeaf, RopeLeafNode(newLeaf), leftIterator)
            //TODO: replaceRoot(newTree) // TODO: ref builder
            //TODO: go-to next leaf through lefIterator
            index += range.last // leaf boundary
            // ---> sadly this will not work
        }
        // Let's begin again,
        // 1. if we compute all new leaves,
        // 2. how can we replace them safely?

//        }
//        return Rope(newRoot)
        TODO()
    }

    // at this point, it is easier to recreate the tree from scratch (not sure if it's optimal tho).
    private fun rebuildTreeCleaningEmptyNodes(
        nodesToBeReplaced: Map<RopeLeafNode, RopeLeafNode> // old | new
    ): RopeNode {
        var nodesReplaced = 0
        val leaves = root.toMutableList()
        for ((index, node /*key*/) in leaves.withIndex()) {
            // Avoid traversing all leaves, since they can potentially be many.
            if (nodesReplaced == nodesToBeReplaced.size) break // no more nodes to replace
            val newNode = nodesToBeReplaced[node] ?: continue
            if (newNode.isEmpty) leaves.removeAt(index)
            leaves[index] = newNode
            nodesReplaced++
        }
        return merge(leaves)
    }

    private fun throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex: Int, endIndex: Int): Nothing {
        throw IndexOutOfBoundsException("startIndex:$startIndex, endIndex:$endIndex, length:$length")
    }

    fun deleteAt(index: Int): Rope {
        checkPositionIndex(index)
        val iterator = SingleElementRopeIterator(root, index)
        if (!iterator.hasNext()) throw IndexOutOfBoundsException("index:$index, length:$length")
        val leaf = iterator.currentLeaf // leaf where index is found
        val i = iterator.currentIndex // index in leaf
        val newLeaf = leaf.deleteAt(i)
        val newTree = rebuildTreeCleaningEmptyNodes(leaf, newLeaf, iterator)
        return Rope(newTree)
    }

    private fun rebuildTreeCleaningEmptyNodes(
        oldNode: RopeNode,
        newNode: RopeNode,
        iterator: RopeIteratorWithHistory
    ): RopeNode {
        if (oldNode === root && newNode.isEmpty) return emptyRopeNode()
        var old = oldNode
        var new: RopeNode? = newNode
        while (true) {
            if (new?.isEmpty == true) new = null // mark empty nodes as null to clean them out
            if (new != null) return rebuildTree(old, new, iterator)
            // for non-root nodes, findParent() should always return a parent.
            val parent = iterator.findParent(old) ?: error("unexpected")
            val pos = parent.indexOf(old)
            assert { pos >= 0 } // position should always be positive.
            new = parent.deleteAt(pos)
            old = parent
            if (old === root) {
                return if (new.isEmpty) old else new
            }
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
        val iterator = SingleElementRopeIterator(root, index)
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
        oldNode: RopeNode,
        newNode: RopeNode,
        iterator: RopeIteratorWithHistory
    ): RopeNode {
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
        root: RopeNode,
        /* The stack which keeps references to parent nodes. */
        stack: ArrayStack<IndexedRopeInternalNode> = defaultStack(),
        /* This lambda is invoked when the target index is
        out of bounds for the current in tree. */
        onOutOfBounds: () -> R,
        /* This lambda is invoked when the target element has
        been retrieved successfully. */
        onElementRetrieved: (
            leaf: RopeLeafNode,
            i: Int,
            element: Char
        ) -> R,
        /* This lambda is invoked when we retrieve the next
        child-node by a preceding nextChild() call. */
        onNextChild: (next: RopeNode) -> Unit = {}
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
                    val node = if (curNode is IndexedRopeInternalNode) curNode else curNode.indexed()
                    // push the current node, so we can always return as a fallback.
                    stack.push(node)
                    // if `index` is less than node's weight, then `index` is in this subtree.
                    if (curIndex < node.weight) {
                        curNode = node.nextChildOrElse {
                            // At this point, `index` is out of bounds because we tried to iterate
                            // a non-existent "next" node, in an internal node when we are certain that
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

    private fun RopeNode.findParentInStack(stack: ArrayStack<IndexedRopeInternalNode>): IndexedRopeInternalNode? {
        var stackNode = stack.popOrNull() ?: return null
        while (stackNode === this) {
            stackNode = stack.popOrNull() ?: return null
        }
        return stackNode
    }

    private fun defaultStack(): ArrayStack<IndexedRopeInternalNode> = ArrayStack(root.height)

    internal interface RopeIteratorWithHistory {
        fun findParent(child: RopeNode): RopeInternalNode?
    }

    open inner class RopeIterator(private val root: RopeNode, startIndex: Int) : RopeIteratorWithHistory {
        init {
            checkPositionIndex(startIndex)
            // This implementation has second `init`.
        }

        private val links = mutableMapOf<RopeNode, RopeInternalNode>() // child || parent
        private val onNextStack = PeekableArrayStack<RopeNode>(root.height)
        private val parentNodesRef = defaultStack()

        init {
            //TODO: add explanation
            onNextStack.push(root)
        }

        private var curIndex = startIndex
        private var nextIndex = curIndex
        private var curNode = root

        val currentIndex get() = curIndex

        // - char -> value is found successfully.
        // - null ->  indicates the absence of pre-received result.
        // - CLOSED -> we are out of bounds and further `next()` calls are not allowed.
        private var nextOrClosed: Any? = null // Char || null || CLOSED

        /**
         * Stores the leaf retrieved by [hasNext] call.
         * If [hasNext] has not been invoked yet,
         * or [hasNext] has not retrieved successfully an element, throws [IllegalStateException].
         */
        val currentLeaf: RopeLeafNode
            get() {
                val leaf = curNode as? RopeLeafNode
                check(nextOrClosed != null) { "`hasNext()` has not been invoked" }
                check(leaf != null) { "`hasNext()` has not retrieved a leaf" }
                return leaf
            }

        // internal API
        override fun findParent(child: RopeNode): RopeInternalNode? = links[child]

        // `hasNext()` is a special get() operation.
        open operator fun hasNext(): Boolean {
            if (nextOrClosed === CLOSED) return false
            return getImpl(
                index = nextIndex,
                root = curNode,
                stack = parentNodesRef,
                onOutOfBounds = { onOutOfBoundsHasNext() },
                onElementRetrieved = { leaf, i, element ->
                    onNextStack.push(leaf)
                    curIndex = i
                    curNode = leaf
                    nextOrClosed = element
                    nextIndex = curIndex + 1
                    true
                },
                //TODO: add comments
                onNextChild = {
                    onNextStack.push(it)
                    findParentInStackAndLink(it)
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
         * Marks the iterator as closed and forbids any other subsequent [next] calls.
         */
        protected fun markClosed() {
            nextOrClosed = CLOSED
        }

        private fun findParentInStackAndLink(child: RopeNode) {
            if (child === root) return
            onNextStack.onEach {
                if (it === child) return@onEach
                val parent = it as? RopeInternalNode ?: return@onEach
                if (!parent.children.contains(child)) return@onEach
                links[child] = parent // link
            }
        }
    }

    inner class SingleElementRopeIterator(root: RopeNode, index: Int) : RopeIterator(root, index) {
        private var invoked = false

        override fun hasNext(): Boolean {
            if (invoked) {
                markClosed()
                return false
            }
            invoked = true
            return super.hasNext()
        }
    }

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String = root.toString()

    private fun checkPositionIndex(index: Int) {
        if (index < 0) throw IndexOutOfBoundsException("index:$index")
    }

    private fun checkRangeIndexes(startIndex: Int, endIndex: Int) {
        if (startIndex < 0) throw IndexOutOfBoundsException("startIndex:$startIndex")
        if (endIndex < startIndex) {
            throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
        }
    }
}

fun Rope.insert(index: Int, element: Char): Rope = insert(index, element.toString())


// btree utils

internal fun RopeInternalNode.indexed(): IndexedRopeInternalNode {
    return IndexedRopeInternalNode(weight, height, children)
}

internal inline fun IndexedRopeInternalNode.nextChildOrElse(action: () -> RopeNode): RopeNode {
    return nextChildOrNull ?: action()
}

private inline fun IndexedRopeInternalNode.nextChildAndKeepRefOrElse(
    stack: ArrayStack<IndexedRopeInternalNode>,
    action: () -> RopeNode
): RopeNode = nextChildOrNull.let {
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
internal class IndexedRopeInternalNode(
    weight: Int,
    height: Int,
    children: List<RopeNode>,
) : RopeInternalNode(weight, height, children) {
    var index = 0
        private set

    val nextChildOrNull: RopeNode? get() = if (hasNextChild()) nextChild() else null

    fun nextChild(): RopeNode {
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


// Internal result for [SingleIndexRopeIteratorWithHistory.nextOrClosed]
private val CLOSED = keb.Symbol("CLOSED")

private const val DEFAULT_CLOSED_MESSAGE = "iterator was closed"
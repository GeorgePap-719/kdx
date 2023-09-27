package kdx

import kdx.btree.*
import kdx.internal.ArrayStack
import kdx.internal.EmptyIterator
import kdx.internal.PeekableArrayStack
import kdx.internal.Symbol

/**
 * A [persistent](https://en.wikipedia.org/wiki/Persistent_data_structure) [rope](https://en.wikipedia.org/wiki/Rope_(data_structure)#See_also)
 * data structure, backed by a [btree][BTreeNode].
 *
 * Most operations have path-copying semantics.
 */
//TODO: research if we need to check if a rope is balanced.
open class Rope(
    // Due to bad initial design,
    // we are forced to expose this as internal.
    // To expose/enable operations on BTreeNode<T>.
    // This leads to a somewhat leaky abstraction,
    // since it is supposed to be an implementation detail.
    internal val root: RopeNode
) {
    //TODO: we can also improve this too keep the tree wide.
    open operator fun plus(other: Rope): Rope {
        // Avoid checking for length == 0, since it might have a cost.
        if (other === EmptyRope) return this
        val left = root
        val right = other.root
        // Use unsafeCreateParent(), because one of the two nodes might be empty.
        val newRope = unsafeCreateParent(left, right)
        // Though, in the end, we still need to rebalance.
        // Not sure if it's optimal, but we avoid upfront cost for the most common case,
        // and pay a bigger cost in the worst case.
        return Rope(newRope.rebalance())
    }

    open val length: Int by lazy { root.length() }

    /**
     * Returns the [Char] at the given [index] or `null` if the [index] is out of bounds of this rope.
     */
    open operator fun get(index: Int): Char? =
        getImpl(
            index = index,
            root = root,
            onOutOfBounds = { return null },
            onElementRetrieved = { _, _, element -> return element }
        )

    open fun indexOf(element: Char): Int {
        var index = 0
        for (leaf in root) {
            for (c in leaf.value) {
                if (c == element) return index
                index++
            }
        }
        return -1
    }

    //TODO: maybe this function better return a sequence?
    open fun collectLeaves(): List<RopeLeaf> = root.map { it.value }

    // `endIndex` is exclusive
    open fun subRope(startIndex: Int, endIndex: Int): Rope {
        checkRangeIndexes(startIndex, endIndex)
        // Fast-path, root is a leaf,
        // call directly subStringLeaf() to retrieve subRope.
        if (root is RopeLeafNode) {
            val newLeaf = root.value.subStringLeaf(startIndex, endIndex)
            return Rope(RopeLeafNode(newLeaf))
        }
        // First, we retrieve left and right bounds (indexes).
        // Then, we subtract all leaves between left and right (exclusive) bounds.
        val leftIterator = SingleElementRopeIteratorWithHistory(root, startIndex)
        if (!leftIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val leftLeaf = leftIterator.currentLeaf // leaf where leftIndex is found
        val leftIndex = leftIterator.currentIndex // index in leaf
        // Since, we create the iterator with `endIndex` exclusive,
        // all other operations can safely include `rightIndex`.
        val rightIterator = SingleElementRopeIteratorWithHistory(root, endIndex - 1)
        if (!rightIterator.hasNext()) throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex, endIndex)
        val rightLeaf = rightIterator.currentLeaf // leaf where rightIndex is found
        val rightIndex = rightIterator.currentIndex // index in leaf
        if (leftLeaf === rightLeaf) {
            // We use `rightIndex + 1`, because subStringLeaf() is an `endIndex` exclusive operation.
            val newLeaf = leftLeaf.value.subStringLeaf(leftIndex, rightIndex + 1)
            return Rope(RopeLeafNode(newLeaf))
        }
        // Since we need only leaves between startIndex <= leaf <= endIndex,
        // we only need the first common parent to both leaves to retrieve them all.
        val commonParent = findCommonParent(leftIterator, leftLeaf, rightIterator, rightLeaf)
        val newTree = buildTreeFromStartAndEndIndex(leftIndex, leftLeaf, rightIndex, rightLeaf, commonParent)
        return if (newTree.isEmpty) emptyRope() else Rope(newTree)
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
        var leftNode: RopeNode = leftLeafNode
        var rightNode: RopeNode = rightLeafNode
        while (true) {
            leftNode = leftIterator.getParent(leftNode)
            rightNode = rightIterator.getParent(rightNode)
            if (leftNode === rightNode) return leftNode
        }
    }

    // endIndex exclusive
    open fun removeRange(startIndex: Int, endIndex: Int): Rope {
        if (startIndex == 0) return subRope(endIndex)
        val leftTree = subRope(0, startIndex)
        val rightTree = subRope(endIndex)
        return leftTree + rightTree
    }

    private fun throwIndexOutOfBoundsExceptionForStartAndEndIndex(startIndex: Int, endIndex: Int): Nothing {
        throw IndexOutOfBoundsException("startIndex:$startIndex, endIndex:$endIndex, length:$length")
    }

    open fun deleteAt(index: Int): Rope {
        checkPositionIndex(index)
        val iterator = SingleElementRopeIteratorWithHistory(root, index)
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
            // for non-root nodes, getParent() should always return a parent.
            val parent = iterator.getParent(old)
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
    open fun insert(index: Int, element: String): Rope {
        checkPositionIndex(index)
        val iterator = SingleElementRopeIteratorWithHistory(root, index)
        // Try to find the target `index`, since we need to locate
        // it and start adding after that `index`.
        if (!iterator.hasNext()) {
            // We allow for inserting on + 1 after last-index, since these are
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
        val parent = iterator.getParent(leaf)
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
            val parent = iterator.getParent(old)
            new = parent.replace(old, new)
            old = parent
            if (old === root) return new
        }
    }

    /**
     * Abstract get implementation.
     *
     * It is a variant of binary search whereas we move down the tree,
     * if `index` is less than the weight of the node, we go to the left.
     * If on the other hand `index` is higher we go to the right, subtracting the value of weight from `index`.
     * This way we are able to skip left subtrees if `index` is not in that part of the tree.
     *
     * Note: It uses a stack to keep references to parent nodes, in case it needs to traverse the tree backwards.
     */
    private inline fun <R> getImpl(
        /* The target index to retrieve. */
        index: Int,
        /* The tree which we iterate. */
        root: RopeNode,
        /* The stack which keeps references to parent nodes. */
        stack: ArrayStack<RopeInternalNodeChildrenIterator> = defaultStack(),
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
                    if (curNode === root) return onOutOfBounds() // Single-node btree.
                    // If "curIndex" is higher than the node's weight,
                    // then we subtract from the node's weight.
                    // This way as we move down the tree,
                    // the "curIndex"  decreases,
                    // and once we reach a leafNode
                    // the character at position "curIndex"
                    // is the target.
                    curIndex -= curNode.weight
                    val parent = stack.popOrNull()
                        ?: error("leaf:$curNode does not have a parent in stack")
                    // Iterate the next child and keep `self` reference in stack, since we
                    // need to allow a child to find its parent in stack in the case of "failure".
                    curNode = parent.nextChildAndKeepRefOrElse(stack) {
                        // If neither `parent` nor stack has a node to give back,
                        // then there are no more nodes to traverse.
                        // Technically, returning onOutOfBounds() here means we are in rightmost subtree.
                        stack.popOrNull() ?: return onOutOfBounds()
                    }
                    onNextChild(curNode)
                }

                is InternalNode -> {
                    val node = if (curNode is RopeInternalNodeChildrenIterator) {
                        curNode
                    } else {
                        curNode.childrenIterator()
                    }
                    // Push the current node, so we can always return as a fallback.
                    stack.push(node)
                    // If `index` is less than node's weight, then `index` is in this subtree.
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
                    if (node.index == 0) { // Leftmost child.
                        // No need to check leaves on leftmost child,
                        // since "curIndex" is higher than node's weight,
                        // and each node holds the sum of the lengths of all the leaves in its left subtree.
                        curIndex -= node.weight
                        if (!node.tryIncIndex()) { // Skip leftmost-child
                            // No more children to traverse in this node, go to the parent node.
                            // If either node is the root or there is no parent, then it means there
                            // are no more nodes to traverse, and `index` is out of bounds.
                            curNode = node.moveStackForward(stack) ?: return onOutOfBounds()
                            onNextChild(curNode)
                            continue
                        }
                    }
                    // Move to the next child node.
                    curNode = node.nextChildOrElse {
                        // If stack returns `null`, there are no more nodes to iterate.
                        // In that case, we can safely assume we are out of bounds.
                        node.moveStackForward(stack) ?: return onOutOfBounds()
                    }
                    onNextChild(curNode)
                }
            }
        }
    }

    /**
     * Moves forward in the specified [stack] until we find the first node that is not [this] node,
     * or returns `null` if [stack] holds no more elements.
     *
     * We check if next node is the same one, because [stack] might hold up a reference to [this] node.
     */
    private fun RopeNode.moveStackForward(
        stack: ArrayStack<RopeInternalNodeChildrenIterator>
    ): RopeInternalNodeChildrenIterator? {
        var stackNode = stack.popOrNull() ?: return null
        while (stackNode === this) {
            stackNode = stack.popOrNull() ?: return null
        }
        return stackNode
    }

    /**
     * Returns an optimal sized-stack, where the size is equal to root's height.
     * In most use-cases, stack will not need to resize.
     */
    private fun defaultStack(): ArrayStack<RopeInternalNodeChildrenIterator> = ArrayStack(root.height)

    fun iteratorWithIndex(startIndex: Int): RopeIterator = RopeIteratorWithHistory(root, startIndex)

    operator fun iterator(): RopeIterator = RopeIteratorWithHistory(root, 0)

    /**
     * The key idea is that the iterator is a special get type,
     * which can be invoked continuously to move to next indexes.
     *
     * Roughly, [hasNext] is a [get] sibling, while [next] simply returns the already retrieved element.
     * From the implementation side, [nextResult] stores the element retrieved by [hasNext]
     * (or a special [ITERATOR_CLOSED] token if there are no more elements to retrieve).
     *
     * Additionally, the iterator keeps a history with all traversed nodes and links them with their parent nodes.
     * This is used as by other operations where they need to move to node's parent.
     *
     * The iteration is performed lazily, similarly how a sequence works.
     */
    private open inner class RopeIteratorWithHistory(private val root: RopeNode, startIndex: Int) : RopeIterator {
        init {
            checkPositionIndex(startIndex)
            // This implementation has second `init`.
        }

        /**
         * Stores all traversed nodes, linked with their parents.
         * Links are exposed through [getParent] API.
         */
        private val links = mutableMapOf<RopeNode, RopeInternalNode>() // child || parent

        /**
         * Stores traversed nodes, so we can link them later with their parents.
         */
        private val onNextStack = PeekableArrayStack<RopeNode>(root.height)

        /**
         * The stack to feed [Rope.getImpl] in [hasNext] on each iteration.
         * By feeding [Rope.getImpl] the same stack allows it to climb the tree backwards,
         * from the previous "checkpoint".
         */
        private val parentNodesRef = defaultStack()

        init {
            // We push root in stack, to mark it as "checkpoint",
            // in case we need to move backwards in the tree.
            onNextStack.push(root)
        }

        private var curIndex = startIndex

        // Tracks on each iteration the `nextIndex`.
        // Basically, this is "curIndex + 1", after `curIndex` retrieves the first value.
        private var nextIndex = curIndex

        // Feeds [getImpl] the node we iterate.
        // This node at first is root, but then after the first successful iteration
        // becomes the leaf we found.
        // This means, unless `nextIndex` > curNode.weight is true,
        // we apply a direct get() call on current leaf.
        private var curNode = root

        /**
         * Stores current leaf's `index`, where the target element was found.
         * If this is invoked before the first invocation of [hasNext], it will return the "original" `startIndex`.
         */
        val currentIndex: Int get() = curIndex

        /**
         * Stores the element retrieved by [hasNext] or a special [ITERATOR_CLOSED] token if this iterator is closed.
         * If [hasNext] has not been invoked yet, `null` is stored.
         */
        private var nextResult: Any? = null // Char || null || CLOSED

        override val isClosed: Boolean get() = nextResult === ITERATOR_CLOSED

        /**
         * Stores the leaf retrieved by [hasNext] call.
         * If [hasNext] has not been invoked yet,
         * or [hasNext] has not retrieved successfully an element, throws [IllegalStateException].
         */
        val currentLeaf: RopeLeafNode
            get() {
                val leaf = curNode as? RopeLeafNode
                check(nextResult != null) { "`hasNext()` has not been invoked" }
                check(leaf != null) { "`hasNext()` has not retrieved a leaf" }
                return leaf
            }

        /**
         * A nullable variant of [getParent] API.
         *
         * **This is an internal API**.
         */
        fun getParentOrNull(child: RopeNode): RopeInternalNode? = links[child]

        /**
         * Returns the [child's][child] parent node.
         * It is the responsibility of the caller to make sure there is a linked parent in stack.
         * Throws [IllegalStateException] when there is no parent is stack,
         * because semantically it should always be a parent in stack.
         *
         * **This is an internal API**.
         */
        fun getParent(child: RopeNode): RopeInternalNode =
            getParentOrNull(child) ?: error("there is no linked parent for child:$child in map")


        // `hasNext()` is a special get() operation.
        override operator fun hasNext(): Boolean {
            if (nextResult === ITERATOR_CLOSED) return false
            return getImpl(
                index = nextIndex,
                root = curNode,
                stack = parentNodesRef,
                onOutOfBounds = { onOutOfBoundsHasNext() },
                onElementRetrieved = { leaf, i, element ->
                    onNextStack.push(leaf) //TODO: i think this can be removed.
                    // Leafs are not used directly to find their parents.
                    curIndex = i
                    curNode = leaf
                    nextResult = element
                    nextIndex = curIndex + 1
                    true
                },
                // Save each node, and try to link it with its parent.
                onNextChild = {
                    onNextStack.push(it)
                    findParentInStackAndLink(it)
                }
            )
        }

        private fun onOutOfBoundsHasNext(): Boolean {
            nextResult = ITERATOR_CLOSED
            return false
        }

        override operator fun next(): Char {
            // Read the already received result or `null` if [hasNext] has not been invoked yet.
            val result = nextResult
            check(result != null) { "`hasNext()` has not been invoked" }
            nextResult = null
            // Is this iterator closed?
            if (result === ITERATOR_CLOSED) throw NoSuchElementException(DEFAULT_CLOSED_MESSAGE)
            return result as Char
        }

        /**
         * Marks the iterator as closed and forbids any other subsequent [next] calls.
         */
        protected fun markClosed() {
            nextResult = ITERATOR_CLOSED
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

    private inner class SingleElementRopeIteratorWithHistory(
        root: RopeNode,
        index: Int
    ) : RopeIteratorWithHistory(root, index) {
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

    private fun checkPositionIndex(index: Int) {
        if (index < 0) throw IndexOutOfBoundsException("index:$index")
    }

    private fun checkRangeIndexes(startIndex: Int, endIndex: Int) {
        if (startIndex < 0) throw IndexOutOfBoundsException("startIndex:$startIndex")
        if (endIndex < startIndex) {
            throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
        }
    }

    /**
     * Returns the [String] representing this rope.
     *
     * Note that for big ropes, this might lead to `OutOfMemoryError`.
     * It is recommended to iterate over the leaves and call `toString()` individually on them.
     */
    override fun toString(): String {
        val sb = StringBuilder()
        val leaves = collectLeaves()
        for (leaf in leaves) sb.append(leaf.chars)
        return sb.toString()
    }

    // ###################
    // # Debug Functions #
    // ###################

    internal fun toStringDebug(): String = root.toStringDebug()
}

fun Rope(value: String): Rope {
    val root = ropeNodeOf(value)
    return Rope(root)
}


/**
 * Iterator for [Rope]. Each iteration is performed lazily, similarly how a sequence works.
 */
// Note:Instances of this interface are *thread-safe* and can be used from coroutines.
//TODO: actually needs research if this is thread-safe.
// The underlying implementation is persistent though.
// All reads should return the same.
// On the other hand probably we are not thread-safe.
// Since, we track non thread-safe states outside [getImpl].
interface RopeIterator {
    /**
     * Returns `true` if iterator has more elements, or returns `false` if the iterator has no more elements.
     *
     * This function retrieves an element from this rope for the subsequent invocation of [next].
     */
    operator fun hasNext(): Boolean

    /**
     * Retrieves the element retrieved by a preceding call to [hasNext],
     * or throws an [IllegalStateException] if [hasNext] was not invoked.
     * This method should only be used in pair with [hasNext]:
     * ```
     * while (iterator.hasNext()) {
     *     val char = iterator.next()
     *     // ... handle element ..
     * }
     * ```
     *
     * This method throws [NoSuchElementException] if iterator [is closed][isClosed].
     */
    operator fun next(): Char

    /**
     * Indicates if the iterator is closed.
     * The iterator closes after the first time [hasNext] returns false,
     * in other words when there are no more elements to retrieve.
     */
    val isClosed: Boolean
}

fun emptyRope(): Rope = EmptyRope

internal object EmptyRope : Rope(emptyRopeNode()) {
    override fun toString(): String = "Rope()"
    override fun equals(other: Any?): Boolean = other is Rope && other.isEmpty()

    override val length: Int = 0
    override fun collectLeaves(): List<RopeLeaf> = emptyList()
    override fun deleteAt(index: Int): Rope = throw IndexOutOfBoundsException("Rope is empty")
    override fun indexOf(element: Char): Int = -1
    override fun get(index: Int): Char? = null
    override fun insert(index: Int, element: String): Rope {
        if (index != 0) throw IndexOutOfBoundsException("index:$index, length:$length")
        val ropeNode = ropeNodeOf(element)
        return Rope(ropeNode)
    }

    override fun removeRange(startIndex: Int, endIndex: Int): Rope {
        throw IndexOutOfBoundsException("Rope is empty")
    }

    override fun plus(other: Rope): Rope {
        if (other.isEmpty()) return this
        return other
    }

    override fun subRope(startIndex: Int, endIndex: Int): Rope {
        if (startIndex == 0 && endIndex == 0) return this
        throw IndexOutOfBoundsException("startIndex: $startIndex, endIndex:$endIndex")
    }
}

fun Rope.insert(index: Int, element: Char): Rope = insert(index, element.toString())
fun Rope.append(element: String): Rope = insert(length, element)
fun Rope.prepend(element: String): Rope = insert(0, element)

fun Rope.subRope(startIndex: Int): Rope = subRope(startIndex, length)
fun Rope.subRope(range: IntRange): Rope = subRope(range.first, range.last + 1)

fun Rope.isEmpty(): Boolean = length == 0

private fun RopeInternalNode.childrenIterator(): RopeInternalNodeChildrenIterator {
    return RopeInternalNodeChildrenIterator(weight, height, children)
}

private inline fun RopeInternalNodeChildrenIterator.nextChildOrElse(action: () -> RopeNode): RopeNode {
    return nextChildOrNull ?: action()
}

private inline fun RopeInternalNodeChildrenIterator.nextChildAndKeepRefOrElse(
    stack: ArrayStack<RopeInternalNodeChildrenIterator>,
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
 * A helper class to iterate through an internal node's children.
 */
internal class RopeInternalNodeChildrenIterator(
    weight: Int,
    height: Int,
    children: List<RopeNode>,
) : RopeInternalNode(weight, height, children) {
    var index = 0
        private set

    val nextChildOrNull: RopeNode? get() = if (hasNext()) next() else null

    operator fun next(): RopeNode {
        if (index >= children.size) throw NoSuchElementException()
        return children[index++]
    }

    operator fun hasNext(): Boolean {
        return index < children.size
    }

    fun tryIncIndex(): Boolean {
        if (index == children.lastIndex) return false
        index++
        return true
    }
}

//TODO: lineCount
open class RopeLeaf(val chars: String, val lineCount: Int) : LeafInfo, Iterable<Char> {
    override val length: Int = chars.length
    override val isLegal: Boolean = chars.length <= MAX_SIZE_LEAF && chars.isNotEmpty()

    override fun subsequnce(range: IntRange): RopeLeaf {
        val newValue = substring(range.first, range.last)
        return RopeLeaf(newValue)
    }

    open operator fun get(index: Int): Char = chars[index]
    open fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
//        checkRangeIndexes(startIndex, endIndex)
        return chars.subSequence(startIndex, endIndex)
    }

    val isEmpty: Boolean = chars.isEmpty()

    override fun iterator(): Iterator<Char> = chars.iterator()

    fun add(index: Int, element: String): RopeLeaf {
        val newValue = buildString {
            val str = this@RopeLeaf
            for (i in str.indices) {
                if (i == index) append(element)
                append(str[i])
            }
            if (index == str.length) append(element)
        }
        return RopeLeaf(newValue)
    }

    fun deleteAt(index: Int): RopeLeaf {
        val newValue = chars.deleteAt(index)
        return if (newValue.isEmpty()) EmptyRopeLeaf else RopeLeaf(newValue)
    }

    private fun String.deleteAt(index: Int): String = buildString {
        val str = this@deleteAt
        for (i in str.indices) {
            if (i == index) continue
            append(str[i])
        }
    }

    override fun toString(): String = "RopeLeaf($chars,$lineCount)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RopeLeaf) return false
        if (chars != other.chars) return false
        return lineCount == other.lineCount
    }

    override fun hashCode(): Int {
        var result = chars.hashCode()
        result = 31 * result + lineCount
        return result
    }
}

fun RopeLeaf.substring(startIndex: Int, endIndex: Int = length): String = subSequence(startIndex, endIndex).toString()
fun RopeLeaf.substring(range: IntRange): String = substring(range.first, range.last + 1)

/**
 * Returns the range of valid character indices for this [RopeLeaf].
 */
val RopeLeaf.indices: IntRange get() = 0..<length

/**
 * Returns the last index for this [RopeLeaf].
 */
val RopeLeaf.lastIndex: Int get() = length - 1

fun RopeLeaf.removeRange(startIndex: Int, endIndex: Int): RopeLeaf {
    val newValue = (this as CharSequence).removeRange(startIndex, endIndex).toString()
    return RopeLeaf(newValue)
}

fun RopeLeaf.removeRange(range: IntRange): RopeLeaf {
    val newValue = (this as CharSequence).removeRange(range).toString()
    return RopeLeaf(newValue)
}

fun RopeLeaf.subStringLeaf(startIndex: Int, endIndex: Int = length): RopeLeaf {
    val newValue = substring(startIndex, endIndex)
    return RopeLeaf(newValue)
}

fun RopeLeaf.subStringLeaf(range: IntRange): RopeLeaf {
    val newValue = substring(range)
    return RopeLeaf(newValue)
}

internal object EmptyRopeLeaf : RopeLeaf("", 0) {
    override val length: Int = 0
    override val isLegal: Boolean = false

    override operator fun get(index: Int): Char =
        throw IndexOutOfBoundsException("Empty leaf doesn't contain element at index:$index")

    override fun toString(): String = "RopeLeaf(\"\", 0)"
    override fun equals(other: Any?): Boolean = other is RopeLeaf && other.isEmpty
    override fun hashCode(): Int = 1

    override fun iterator(): Iterator<Char> = EmptyIterator
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex == 0 && endIndex == 0) return this.chars
        throw IndexOutOfBoundsException("Empty leaf doesn't contain element at startIndex:$startIndex, and endIndex:$endIndex")
    }
}


fun RopeLeaf(charCount: String): RopeLeaf = RopeLeaf(charCount, 0)

fun RopeLeaf.add(index: Int, element: Char): RopeLeaf = add(index, element.toString())

typealias RopeInternalNode = InternalNode<RopeLeaf>
typealias RopeLeafNode = LeafNode<RopeLeaf>

private val emptyRopeLeafNode = RopeLeafNode(EmptyRopeLeaf)

internal fun RopeLeafNode(input: String): RopeLeafNode =
    if (input.isEmpty()) emptyRopeLeafNode else RopeLeafNode(RopeLeaf(input))

typealias RopeNode = BTreeNode<RopeLeaf>

//TODO: research if we can avoid big rec
fun RopeNode.length(): Int {
    return when (this) {
        is LeafNode -> this.weight
        is InternalNode -> {
            val children = this.children
            var curLen = 0
            curLen += this.weight
            for (index in children.indices) {
                if (index == 0) continue
                curLen += children[index].length()
            }
            curLen
        }
    }
}

internal fun createParent(nodes: List<RopeNode>): RopeInternalNode = createParent(nodes)

internal fun ropeNodeOf(input: String): RopeNode {
    return splitIntoNodes(input)
}

fun emptyRopeNode(): RopeNode {
    return emptyBTreeNode()
}

private fun splitIntoNodes(input: String): RopeNode {
    if (input.length <= MAX_SIZE_LEAF) return RopeLeafNode(input)
    val leaves = splitIntoLeaves(input)
    return merge(leaves)
}

private fun splitIntoLeaves(input: String): List<RopeLeafNode> {
    return buildList {
        var index = 0
        while (index < input.length) {
            val leafValue = input.substring(index, minOf(index + MAX_SIZE_LEAF, input.length))
            add(RopeLeafNode(leafValue))
            index += MAX_SIZE_LEAF
        }
    }
}

// ------ string-leaf utils ------

internal fun expandLeaf(leaf: RopeLeafNode): RopeInternalNode {
    val half = leaf.weight / 2
    val left = RopeLeafNode(leaf.value.substring(0, half))
    val right = RopeLeafNode(leaf.value.substring(half))
    return merge(left, right)
}

// ------ addXXX ------

// - Adds the element into the leaf and expands as necessary. Returns the resulting leaves.
//
// This operation creates one resulting string from the element and this leaf. This can
// be expensive in most cases. One solution is to avoid creating a big string by creating
// leaf nodes on the spot. Though, this is easier than done.
//TODO: research this if there is time.
internal fun RopeLeafNode.expandableAdd(index: Int, element: String): List<RopeLeafNode> {
    checkValueIndex(index, this.value)
    val newLen = value.length + element.length
    if (newLen <= MAX_SIZE_LEAF) return listOf(add(index, element))
    val newLeaf = value.add(index, element)
    return splitIntoLeaves(newLeaf.chars)
}

internal fun RopeLeafNode.add(index: Int, element: Char): RopeLeafNode = add(index, element.toString())

/**
 * Returns a new leaf with the specified [element] inserted at the specified [index].
 *
 * @throws IndexOutOfBoundsException if [index] is greater than or equals to the length of this child.
 * @throws IllegalArgumentException if the resulting length exceeds the maximum size of a leaf.
 */
internal fun RopeLeafNode.add(index: Int, element: String): RopeLeafNode {
    checkValueIndex(index, this.value)
    val newLen = value.length + element.length
    require(newLen <= MAX_SIZE_LEAF) { "max size of a leaf is:$MAX_SIZE_LEAF, but got:$newLen" }
    if (index == 0) return RopeLeafNode(element + value.chars)
    if (index == value.chars.lastIndex + 1) return RopeLeafNode(value.chars + element)
    val newValue = value.add(index, element)
    return RopeLeafNode(newValue)
}

// ------ deleteXXX ------

internal inline fun RopeLeafNode.deleteAtAndIfEmpty(index: Int, onEmpty: () -> RopeLeafNode): RopeLeafNode {
    checkElementIndex(index, this.value)
    val newValue = value.deleteAt(index)
    if (newValue.isEmpty) return onEmpty()
    return RopeLeafNode(newValue)
}

internal fun RopeLeafNode.deleteAt(index: Int): RopeLeafNode {
    checkElementIndex(index, this.value)
    val newLeaf = value.deleteAt(index)
    return if (newLeaf.isEmpty) emptyRopeLeafNode else RopeLeafNode(newLeaf)
}

private fun checkValueIndex(index: Int, leafNode: RopeLeaf) {
    if (index < 0 || index > leafNode.lastIndex + 1) { // it is acceptable for an index to be right after the last-index
        throw IndexOutOfBoundsException("index:$index, leaf-length:${leafNode.length}")
    }
}

private fun checkElementIndex(index: Int, leafNode: RopeLeaf) {
    if (index < 0 || index > leafNode.lastIndex) {
        throw IndexOutOfBoundsException("index:$index, leaf-length:${leafNode.length}")
    }
}

private fun RopeLeaf.checkRangeIndexes(startIndex: Int, endIndex: Int) {
    if (startIndex < 0 || endIndex > lastIndex) {
        throw IndexOutOfBoundsException("startIndex:$startIndex, endIndex:$endIndex, leaf-length:${length}")
    }
    if (endIndex < startIndex) {
        throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
    }
}

internal const val MAX_SIZE_LEAF = 2048

// Internal result for [RopeIteratorWithHistory.nextResult]
// Typically means we are out of bounds for this iterator.
private val ITERATOR_CLOSED = Symbol("CLOSED")

// Default error message when the iterator is closed,
// and invoking [RopeIteratorWithHistory.next].
private const val DEFAULT_CLOSED_MESSAGE = "iterator was closed"
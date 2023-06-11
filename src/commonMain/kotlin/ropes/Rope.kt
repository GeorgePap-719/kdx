package keb.ropes


/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(value: String) {
    val root = btreeOf(value)

    operator fun get(index: Int): Char? {
        if (index < 0) return null
        return getImpl(index, root)
    }

    // variant of binary search
    private fun getImpl(index: Int, root: BTreeNode): Char? {
        var curIndex = index
        var curNode = root
        val stack = ArrayStack<IndexedInternalNode>(root.height)

        while (true) {
            when (curNode) {
                is LeafNode -> {
                    if (curIndex < curNode.length) curNode.value[curIndex] // fast-path
                    if (curNode === root) return null // single-node btree.
                    // Two major cases:
                    // 1. out-of-bounds for this leaf, but we still have the next child to check.
                    // 2. subtract weight properly
                    curIndex -= curNode.weight
                    //TODO: state here all procedure
                    //TODO: this scenario prob does not cover up all subcases.
                    val parent = stack.popOrNull()
                        ?: error("leaf:$curNode does not have a parent in stack")
                    // If neither parent nor stack has a node to give back, then there are no more
                    // nodes to traverse. Technically, returning `null` here means we are in right subtree.
                    curNode = parent.nextChildOrMoveForward(stack) ?: return null
                }

                is InternalNode -> {
                    val node = if (curNode is IndexedInternalNode) curNode else curNode.indexed()
                    // push the current node, so we can always return as a fallback.
                    stack.push(node)
                    // Start by checking conditions on the first child
                    // traverse each child 1-by-1
                    if (curIndex < node.weight) {
                        // index is in this subtree
                        curNode = node.nextChildOrElse {
                            // Current state:
                            // - "curIndex < node.weight" is true.
                            // - we are an internal node.
                            // Since meet all preconditions, then at this point, there is a node to traverse.
                            error("unexpected result for node:$node")
                        }
                        continue
                    }
                    if (node.index == 0) { // leftmost child
                        curIndex -= node.weight
                        // No need to check leaves on leftmost child,
                        // since we are sure `index` is not here.
                        if (!node.tryIncIndex()) { // skip first-child
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

    /**
     * Returns next-child in [this] indexed-node or moves up in stack. In case stack is empty, it returns null.
     */
    private fun IndexedInternalNode.nextChildOrMoveForward(stack: ArrayStack<IndexedInternalNode>): BTreeNode? {
        return nextChildOrElse { stack.popOrNull() ?: return null }
    }

    fun length(): Int {
        var curNode = root
        while (true) {
            var length = 0
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

private fun InternalNode.indexed(): IndexedInternalNode {
    return IndexedInternalNode(weight, height, children)
}

private inline fun IndexedInternalNode.nextChildOrElse(action: () -> BTreeNode): BTreeNode {
    return nextChildOrNull ?: action()
}

/**
 * A helper class to iterate through an internal node's children, similarly to an iterator.
 */
private class IndexedInternalNode(
    weight: Int,
    height: Int,
    children: List<BTreeNode>,
) : InternalNode(weight, height, children) {
    var index = 0
        private set

    val nextChildOrNull: BTreeNode? get() = if (hasNextChild()) null else nextChild()

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
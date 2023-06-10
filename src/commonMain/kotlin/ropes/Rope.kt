package keb.ropes


/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(value: String) {
    val root = btreeOf(value)

    operator fun get(index: Int): Char? {
        if (index < 0) return null
        return tailrecget(index, root)
    }

    // variant of binary search
    // notes: maybe we should subtract an index for every right child, otherwise probably we will always get out of
    // bounds.
    // note: wrong impl
    private tailrec fun tailrecget(index: Int, curNode: BTreeNode): Char? {
        if (curNode is LeafNode) {
            val value = curNode.value
            // We cannot avoid checking for out-of-bounds index,
            // since it is not known until we reach the targeted leaf.
            return if (index < value.length) curNode.value[index] else null
        }
        var curIndex = index
        for (node in (curNode as InternalNode).children) {
            if (curIndex < node.weight) return tailrecget(curIndex, node)
            // for every right step we subtract the node's weight,
            // this is needed to "compute" the leaf's proper index.
            // TODO: check if this section needs documenting.
            curIndex -= node.weight
        }
        return null // out of bounds
    }

    private fun getImpl(index: Int, root: BTreeNode): Char? {
        var curIndex = index
        var curNode = root
        var indexedRoot = if (root is InternalNode) root.indexed() else null
        val stack = ArrayStack<IndexedInternalNode>(root.height)
        var nodePtr = 0 // TODO

        outerLp@ while (true) {
            when (curNode) {
                is LeafNode -> {
                    // store this only for ref
                    // return if (curIndex < curNode.value.length) curNode.value[curIndex] else null
                    if (curIndex < curNode.length) curNode.value[curIndex] // fast-path
                    // Two major cases:
                    // 1. Is out of bounds for sure because we are in rightmost child
                    // 2. out-of-bounds for this leaf, but we still have the next child to check.
                    // 3. subtract weight properly
                    curIndex -= curNode.weight
                    //TODO: not sure if this is ok scenario or some sort of IllegalState
                    // Though, for simple scenario where root is leaf, it is needed.
                    if (curIndex < 0) return null
                    while (true) {
                        // 1.
                        //TODO: this scenario prob does not cover up all subcases.
                        val parent = stack.popOrNull() ?: return null
                        // 2.
                        curNode = parent.getNextChildAndIncIndexOrNull()
                            ?: continue // no more children to check, try again.
                        stack.push(parent)
                        continue@outerLp // is this enough?
                    }
                }

                is InternalNode -> {
                    val _curNode = curNode // otherwise smartcast is impossible
                    //TODO: add info about this action
                    val curIndexedNode = _curNode.indexed()
                    stack.push(curIndexedNode)

                    for ((i, node) in _curNode.children.withIndex()) {
                        // separate first child vs others
                        if (i == 0 && _curNode.children.size == 1 && curIndex >= _curNode.weight) {
                            return null // out of bounds
                        }
                        if (i == _curNode.children.lastIndex && node is LeafNode) { // rightmost child
                            // Only at the rightmost leafNode we can check
                            // if target `index` is out of bounds or not.
                            return if (curIndex < node.value.length) node.value[curIndex] else null
                        }
                        ///
                        ///
                        // traverse each child 1-by-1
                        if (curIndex < node.weight) {
                            curNode = node // index is in this subtree
                            continue@outerLp
                        }
                        // ---- Time to check next child ----
                        // cases: we need to add fallback, so we can check the next child
                        // - where should we check of curIndex is negative
                        // handle first case, since will be leftmost child
                        if (index == 0) {
                            curIndex -= node.weight
                            // No need to check leaves on leftmost child, since we can be sure `index`
                            // is not here.
                            continue
                        }
                        // need to visit child here
                        // cases to check:
                        // - we need to add fallback, so we can check next child
                        curNode = node // something like this here
                    }
                }
            }
        }
    }

    private fun traverseChild(index: Int, child: BTreeNode): TraverseChildOp {
        var curIndex = index
        when (child) {
            is LeafNode -> {
                return if (curIndex < child.weight) {
                    FoundChar(child.value[curIndex])
                } else {
                    TraversedWeight(child.weight)
                }
            }

            is InternalNode -> {
                for ((i, node) in child.children.withIndex()) {
                    if (i == 0 && curIndex >= node.weight) {
                        curIndex -= node.weight
                        continue
                    }
                    //traverseChild()
                    TODO()
                }
            }
        }
        TODO()
    }

    private sealed class TraverseChildOp
    private class FoundChar(val value: Char) : TraverseChildOp()
    private class TraversedWeight(val value: Int) : TraverseChildOp()

    fun length(): Int {
        while (true) {
            var length = 0
            var curNode = root
            when (curNode) {
                is LeafNode -> return length + curNode.weight
                is InternalNode -> {
                    if (curNode.children.size == 1) return length + curNode.weight // only left-child
                    val rightMostNode = curNode.children.last()
                    // accumulate lef-subtree weight and move on
                    length += curNode.weight
                    @Suppress("UNUSED_VALUE")
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

private fun IndexedInternalNode.getNextChildAndIncIndexOrNull(): BTreeNode? {
    val child = getNextChildOrNull() ?: return null
    tryIncIndex()
    return child
}

private class IndexedInternalNode(
    weight: Int,
    height: Int,
    children: List<BTreeNode> = listOf(),
) : InternalNode(weight, height, children) {
    private var index = 0

    fun tryIncIndex(): Boolean {
        if (index == children.size) return false
        index++
        return true
    }

    fun getNextChildOrNull(): BTreeNode? {
        return if (index < children.size) null else children[index]
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
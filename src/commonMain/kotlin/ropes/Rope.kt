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
        val stack = ArrayStack<IndexedInternalNode>(root.height)

        loop@ while (true) {
            when (curNode) {
                is LeafNode -> {
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
                        //TODO: state here all procedure
                        // 1.
                        //TODO: this scenario prob does not cover up all subcases.
                        val parent = stack.popOrNull() ?: return null
                        // If child is rightmost, then index is out of bounds.
                        // hmm i think this cond does not stand.. let me think a bit about it.
                        if (parent.index == parent.children.lastIndex) return null
                        // 2.
                        curNode = parent.getNextChildAndIncIndexOrNull()
                            ?: continue // no more children to check, try again.
                        stack.push(parent)
                        continue@loop // is this enough?
                    }
                }

                is InternalNode -> {
                    //TODO: add info about this action
                    val node = if (curNode is IndexedInternalNode) curNode else curNode.indexed()
                    stack.push(node)
                    val i = node.index
                    // Start by checking conditions on the first child
                    // out of bounds
                    if (i == 0 && node.children.size == 1 && curIndex >= node.weight) return null
                    // traverse each child 1-by-1
                    if (curIndex < node.weight) {
                        curNode = node.getNextChildAndIncIndexOrNull() // index is in this subtree
                            ?: error("unexpected result for node:$node")
                        continue
                    }
                    // ---- Time to check next child ----
                    // - where should we check of curIndex is negative
                    // handle first case, since will be leftmost child
                    if (i == 0) {
                        curIndex -= node.weight
                        // No need to check leaves on leftmost child, since we can be sure `index`
                        // is not here.
                        if (!node.tryIncIndex()) {
                            curNode = stack.popOrNull() ?: return null
                            continue
                        }
                        // If stack returns `null`, there are no more nodes to traverse.
                        // In that case, we can safely assume we are out of bounds.
                        curNode = node.getNextChildAndIncIndexOrNull() ?: stack.popOrNull() ?: return null
                        continue
                    }
                    // need to visit child here
                    curNode = node.getNextChildAndIncIndexOrNull() ?: stack.popOrNull() ?: return null
                }
            }
        }
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
    var index = 0
        private set

    fun tryIncIndex(): Boolean {
        if (index == children.lastIndex) return false
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
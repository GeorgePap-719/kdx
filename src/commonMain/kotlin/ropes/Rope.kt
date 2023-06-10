package keb.ropes

import keb.Symbol


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
        var parent = curNode

        outerLp@ while (true) {
            when (curNode) {
                is LeafNode -> {
                    //TODO: here is not semantically right to return `null`,
                    // since we cannot be sure if its out of bounds or just not the right leaf
                    // We cannot avoid checking for out-of-bounds index,
                    // since it is not known until we reach the targeted leaf.
                    //

//                    if (curNode !== parent) {
//                        return if (curIndex < curNode.value.length) {
//                            curNode.value[curIndex]
//                        } else { // fallback strat
//                            getImpl(
//                                curIndex - curNode.weight,
//                                parent
//                            )
//                        }
//                    }
//                    return if (curIndex < curNode.value.length) curNode.value[curIndex] else null

                    // is LeafNode

                }

                is InternalNode -> {
                    val _curNode = curNode // otherwise smartcast is impossible
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
                        // prob this operations will be rethinked
                        curIndex -= node.weight //TODO: add doc
                        if (curIndex < 0) return null //TODO: check this op

                        // curNode =

                        // cases to check:
                        // - we need to add fallback, so we can check next child
                        // - handle last/rightmost child in loop (done!)
                        // - where should we check of curIndex is negative
                    }
                }
            }
        }
    }

    private class BTreeNodeWithParent(val node: BTreeNode, val parent: BTreeNode) {
        // ref to child index
        private var index: Int = when (node) {
            is InternalNode -> 0
            is LeafNode -> -1
        }

        fun tryIncIndex(): Boolean {
            if (index == -1) return false
            index++
            return true
        }

        fun getNextChildOrParent(): BTreeNode {
            if (index == -1) return parent
            return (node as InternalNode).children[index]
        }

        fun getChildren(): List<BTreeNode> {
            if (index == -1) return emptyList()
            return (node as InternalNode).children
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

private val OUT_OF_BOUNDS = Symbol("OUT_OF_BOUNDS")


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
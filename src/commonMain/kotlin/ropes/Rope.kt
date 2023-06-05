package keb.ropes


/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(value: String)

fun btreeOf(input: String): BTreeNode {
    return splitIntoNodesF(input)
}

// saner btreeOf

fun splitIntoNodesF(input: String): BTreeNode {
    if (input.length < MAX_SIZE_LEAF) return LeafNode(input)
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

//TODO: how should we check invariants?
//TODO: document it
fun merge(nodes: List<BTreeNode>): BTreeNode {
    if (nodes.size < MAX_CHILDREN) return unbalancedMerge(nodes)
    val leftList = nodes.subList(0, MAX_CHILDREN)
    val rightList = nodes.subList(MAX_CHILDREN, nodes.size)

    val leftParent = unbalancedMerge(leftList)

    if (rightList.size < MAX_CHILDREN) {
        val rightParent = unbalancedMerge(rightList)
        return unbalancedMerge(leftParent, rightParent)
    }

    val rightParent = merge(rightList)
    return unbalancedMerge(leftParent, rightParent)
}

// btree utils

private fun unbalancedMerge(left: BTreeNode, right: BTreeNode): InternalNode {
    return unbalancedMerge(listOf(left, right))
}

// May return an unbalanced tree
//TODO: document it
private fun unbalancedMerge(nodes: List<BTreeNode>): InternalNode {
    val weight = nodes.first().computeWeightInLeftSubtree()
    val height = nodes.maxOf { it.height } + 1
    return InternalNode(weight, height, nodes)
}

//TODO: this can probably be improved, by not searching all leaves
private fun BTreeNode.computeWeightInLeftSubtree(): Int {
    return when (this) {
        is InternalNode -> {
            val leftmost = this.children.first()
            return leftmost.sumOf { it.weight } // sumOf weight in leaves
        }

        is LeafNode -> this.weight
    }
}

/**
 * Tries to add a list of [children] on this node, in the specified [index]. In case [index] is `null`
 * the nodes are appended to the end of list. If receiver node cannot hold more children, it returns `null`.
 */
// copy-on-write semantics
private fun InternalNode.tryAddChildren(children: List<BTreeNode>, index: Int? = null): InternalNode? {
    if (this.children.size + children.size > MAX_CHILDREN) return null
    val newChildren = this.children.addWithCopyOnWrite(children, index ?: children.size)
    return InternalNode(this.weight, this.height, newChildren)
}

/**
 * Tries to add a child on this node, in the specified [index]. In case [index] is `null` the nodes are appended
 * to the end of list. If receiver node cannot hold more children, it returns `null`.
 */
// copy-on-write semantics
private fun InternalNode.tryAddChild(child: BTreeNode, index: Int? = null): InternalNode? {
    if (this.children.size + 1 > MAX_CHILDREN) return null
    val newChildren = this.children.addWithCopyOnWrite(child, index ?: children.size)
    return InternalNode(this.weight, this.height, newChildren)
}

package keb.ropes

import kotlin.math.min


/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(value: String)

fun btreeOf(input: String): BTreeNode {
    return if (input.length < MAX_SIZE_LEAF) {
        LeafNode(input)
    } else {
        splitIntoNodes(input)
    }
}

// btree utils

private fun splitIntoNodes(input: String): BTreeNode {
    val splitPoint = min(MAX_SIZE_LEAF, input.length)
    val left = input.substring(0 until splitPoint)
    val right = input.substring(splitPoint)
    val leftLeaf = LeafNode(left)
    val rightLeaf = LeafNode(right)
    val parent = InternalNode(leftLeaf.weight, 1, listOf(leftLeaf, rightLeaf))
    if (input.length - MAX_SIZE_LEAF > 0) return tailrecsplitIntoNodes(input.substring(MAX_SIZE_LEAF), parent)
    return parent
}

@Suppress("DuplicatedCode")
private fun tailrecsplitIntoNodes(input: String, curRoot: InternalNode): BTreeNode {
    if (input.length < MAX_SIZE_LEAF) {
        return curRoot.tryAddChild(LeafNode(input)) ?: TODO("hande split case")
    }
    val splitPoint = min(MAX_SIZE_LEAF, input.length)
    val left = input.substring(0 until splitPoint)
    val right = input.substring(splitPoint)
    val leftLeaf = LeafNode(left)
    val rightLeaf = LeafNode(right)
    val newRoot = curRoot.tryAddChildren(listOf(leftLeaf, rightLeaf)) ?: TODO("hande split case")
    if (input.length - MAX_SIZE_LEAF > 0) return tailrecsplitIntoNodes(input.substring(MAX_SIZE_LEAF), newRoot)
    return newRoot
}

private fun InternalNode.addChildOrSplitAndAdd(child: BTreeNode, index: Int? = null): InternalNode {
    return tryAddChild(child) ?: splitAndAdd(child, index)
}

private fun InternalNode.splitAndAdd(child: BTreeNode, index: Int? = null): InternalNode {
    if (index == null) TODO("append")
    val newChildren = this.children.addWithCopyOnWrite(child, index)
    val leftNodeChildren = newChildren.subList(0, MAX_CHILDREN)
    val rightNodeChildren = newChildren.subList(MAX_CHILDREN, newChildren.size)
    val leftmostChildrenInLeftNodeChildren = leftNodeChildren.first()
    val leftParent = InternalNode(
        leftmostChildrenInLeftNodeChildren.weight,
        leftmostChildrenInLeftNodeChildren.height + 1,
        leftNodeChildren
    )
    val leftmostChildrenInRightNodeChildren = rightNodeChildren.first()
    val rightParent = InternalNode(
        leftmostChildrenInRightNodeChildren.weight,
        leftmostChildrenInLeftNodeChildren.height + 1,
        rightNodeChildren
    )
//    val root = InternalNode(leftParent.children.computeWeight(), leftParent.height + 1, leftParent + rightParent)
//    if (rightParent.children.size > MAX_CHILDREN) return root.splitAndAdd() //TODO
//    return root
    TODO()
}

private fun InternalNode.ifNeedSplitAndMerge(): InternalNode {
    for (node in this.children) {
        when (node) {
            is InternalNode -> {

                for (_node in node.children) if (_node is InternalNode) {
                    _node.ifNeedSplitAndMerge()
                }
                if (node.children.size < MAX_CHILDREN) continue
                return legalAndUnbalancedMerge(node.children)
            }

            is LeafNode -> continue
        }
    }
    TODO()
}

//TODO: document it
// not suitable for big number of nodes
private fun legalAndUnbalancedMerge(nodes: List<BTreeNode>): InternalNode {
    if (nodes.size < MAX_CHILDREN) return unbalancedMerge(nodes)
    val leftList = nodes.subList(0, MAX_CHILDREN)
    val rightList = nodes.subList(MAX_CHILDREN, nodes.size)
    val leftParent = unbalancedMerge(leftList)
    if (rightList.size < MAX_CHILDREN) {
        val rightParent = unbalancedMerge(rightList)
        return unbalancedMerge(leftParent + rightParent)
    }
    val rightParent = legalAndUnbalancedMerge(rightList)
    return unbalancedMerge(leftParent + rightParent)
}

// May return an unbalanced tree
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

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
    val root = InternalNode(leftParent.children.computeWeight(), leftParent.height + 1, leftParent + rightParent)
    if (rightParent.children.size > MAX_CHILDREN) return root.splitAndAdd()
    return root
}

private fun InternalNode.ifNeedSplitAndMerge(): InternalNode {
    for (node in this.children) {
        when (node) {
            is InternalNode -> {
                //for (_node in node.children) if (_node is InternalNode) return _node.ifNeedSplitAndMerge()
                if (node.children.size < MAX_CHILDREN) continue

            }

            is LeafNode -> continue
        }
    }
}

// does not check for any balancing requirements.
//TODO: not right impl
private fun unsafeMergeNodes(nodes: List<BTreeNode>): InternalNode {
    if (nodes.size > MAX_CHILDREN) {
        val leftList = nodes.subList(0, MAX_CHILDREN)
        val rightList = nodes.subList(MAX_CHILDREN, nodes.size)
        val rightParent = unsafeMergeNodes(rightList)
        val leftParent = InternalNode(leftList.computeWeight(), leftList.first().height, leftList)
        val newRoot = InternalNode(leftParent.children.computeWeight(), leftParent.height + 1, leftParent + rightParent)
        return newRoot
    }

    return InternalNode(nodes.computeWeight(), nodes.first().height + 1, nodes)
}

private fun InternalNode.isLegalChild(): Boolean {
    return children.size <= MAX_CHILDREN
}


//TODO: compute proper weight here, so we wont come back here again.
private fun List<BTreeNode>.computeWeight(): Int {
    var weight = 0
    for (node in this) weight += node.weight
    return weight
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

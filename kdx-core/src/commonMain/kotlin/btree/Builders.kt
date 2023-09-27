package kdx.btree

/**
 * Merges [left] and [right] nodes into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
fun <T : LeafInfo> merge(left: BTreeNode<T>, right: BTreeNode<T>): InternalNode<T> = merge(listOf(left, right))

/**
 * Merges [nodes] into one balanced btree.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 */
//Note: this is changed.
fun <T : LeafInfo> merge(nodes: List<BTreeNode<T>>): InternalNode<T> {
    val nonEmptyNodes = nodes.filter { !it.isEmpty }
    if (nonEmptyNodes.isEmpty()) return emptyInternalNode()
    nonEmptyNodes.forEach {
        require(it.isLegal) { "node:$it does not meet the requirements" }
    }
    return unsafeMerge(nonEmptyNodes)
}

/**
 * An analogue of the [merge] builder that does not check for invariants.
 * Used internally in operators where we trust the validity of nodes.
 */
private fun <T : LeafInfo> unsafeMerge(nodes: List<BTreeNode<T>>): InternalNode<T> {
    if (nodes.size <= MAX_CHILDREN) return unsafeCreateParent(nodes)
    val leftList = nodes.subList(0, MAX_CHILDREN)
    val rightList = nodes.subList(MAX_CHILDREN, nodes.size)
    val leftParent = unsafeCreateParent(leftList)
    if (rightList.size <= MAX_CHILDREN) {
        val rightParent = unsafeCreateParent(rightList)
        return unsafeCreateParent(leftParent, rightParent)
    }
    val rightParent = unsafeMerge(rightList)
    return unsafeCreateParent(leftParent, rightParent)
}

/**
 * Creates a legal parent for [node].
 * The weight of the parent is set to that of the [node].
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
 */
fun <T : LeafInfo> createParent(node: BTreeNode<T>): InternalNode<T> {
    return createParent(listOf(node))
}

/**
 * Creates a legal parent for [left] and [right] nodes.
 * The weight of the parent is set to that of the [left] node.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children.
 */
fun <T : LeafInfo> createParent(left: BTreeNode<T>, right: BTreeNode<T>): InternalNode<T> {
    return createParent(listOf(left, right))
}

/**
 * Creates a legal parent for [nodes].
 * The weight of the parent is set to that of the first node.
 *
 * @throws IllegalArgumentException if a child node is not legal ([BTreeNode.isLegal]).
 * @throws IllegalArgumentException if the resulting node has more than the maximum size of children, or
 * if the [input][nodes] is empty.
 */
fun <T : LeafInfo> createParent(nodes: List<BTreeNode<T>>): InternalNode<T> {
    require(nodes.size <= MAX_CHILDREN) { "a node cannot hold more than:$MAX_CHILDREN children" }
    require(nodes.isNotEmpty()) { "cannot create a parent for zero nodes" }
    nodes.forEach {
        require(it.isLegal) { "node:$it does not meet the requirements" }
    }
    return unsafeCreateParent(nodes)
}

internal fun <T : LeafInfo> unsafeCreateParent(left: BTreeNode<T>, right: BTreeNode<T>): InternalNode<T> {
    return unsafeCreateParent(listOf(left, right))
}

/**
 * Creates a parent for [nodes], without checking if satisfies the requirements for a legal btree.
 */
internal fun <T : LeafInfo> unsafeCreateParent(nodes: List<BTreeNode<T>>): InternalNode<T> {
    val weight = computeWeightInLeftSubtreeForParent(nodes)
    val height = nodes.maxOf { it.height } + 1
    return InternalNode(weight, height, nodes)
}

/**
 * Computes weight in left subtree for a new parent.
 */
private fun <T : LeafInfo> computeWeightInLeftSubtreeForParent(children: List<BTreeNode<T>>): Int {
    return when (val leftmostNode = children.first()) {
        is LeafNode -> leftmostNode.weight
        //TODO: check if we can compute this with faster path
        is InternalNode -> leftmostNode.sumOf { it.weight }
    }
}
package keb.ropes

fun btreeOf(input: String): BTreeNode {
    return splitIntoNodes(input)
}

fun splitIntoNodes(input: String): BTreeNode {
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
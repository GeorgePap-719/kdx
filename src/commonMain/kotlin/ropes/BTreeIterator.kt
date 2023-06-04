package keb.ropes

class BTreeNodeIterator(root: BTreeNode) : Iterator<LeafNode> {
    private var index = 0
    private var currentNode = root
    private val size: Int

    private val path: ResizeableArray<LeafNode> = ResizeableArray(1)

    init {
        fillPath()
        size = index + 1
        index = 0
    }

    private fun fillPath() {
        when (currentNode) {
            is InternalNode -> {
                val cur = currentNode as InternalNode
                traverseInOrder(cur.children)
            }

            is LeafNode -> path[index++] = currentNode as LeafNode
        }
    }

    private fun traverseInOrder(nodes: List<BTreeNode>) {
        for (node in nodes) {
            when (node) {
                is InternalNode -> {
                    val cur = currentNode as InternalNode
                    traverseInOrder(cur.children)
                }

                is LeafNode -> path[index++] = currentNode as LeafNode
            }
        }
    }

    override fun hasNext(): Boolean = index < size

    override fun next(): LeafNode {
        if (!hasNext()) throw NoSuchElementException()
        return path[index++]!!
    }
}

class SingleBTreeNodeIterator(private val root: LeafNode) : Iterator<LeafNode> {
    private var index = 0
    private val size = 1

    override fun hasNext(): Boolean = index < size

    override fun next(): LeafNode {
        if (!hasNext()) throw NoSuchElementException()
        index++
        return root
    }
}
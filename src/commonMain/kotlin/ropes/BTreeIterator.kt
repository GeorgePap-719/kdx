package keb.ropes

class BTreeNodeIterator(root: BTreeNode) : Iterator<LeafNode> {
    private var index = 0
    private var currentNode = root
    private val size: Int

    private val path: ResizeableArray<LeafNode> = ResizeableArray(1)

    init {
        fillPath()
        size = index // cannot + 1 index, since in last insertion we always inc the index ([index++])
        index = 0
    }

    private fun fillPath() {
        when (val curNode = currentNode) {
            is InternalNode -> traverseInOrder(curNode.children)
            is LeafNode -> path[index++] = curNode
        }
    }

    private fun traverseInOrder(nodes: List<BTreeNode>) {
        for (node in nodes) {
            when (node) {
                is InternalNode -> traverseInOrder(node.children)
                is LeafNode -> path[index++] = node
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
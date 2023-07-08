package keb.ropes

internal class BTreeNodeBuilder<T : LeafInfo>(private var root: InternalNode<T>) {
    val weight: Int get() = root.weight
    val height: Int get() = root.height
    val isLegal: Boolean get() = root.isLegal
    val isEmpty: Boolean get() = root.isEmpty

    fun build(): BTreeNode<T> = root.rebalance()

    operator fun get(index: Int): BTreeNode<T> = root.children[index]

    fun add(child: BTreeNode<T>) {
        root = root.addLast(child)
    }

    fun deleteAt(index: Int) {
        this.root = root.deleteAt(index) ?: emptyInternalNode()
    }

    fun replaceRoot(newRoot: InternalNode<T>) {
        root = newRoot
    }

    fun subTree(fromIndex: Int, toIndex: Int): List<BTreeNode<T>> =
        root.children.subList(fromIndex, toIndex).ifEmpty { emptyList() }
}
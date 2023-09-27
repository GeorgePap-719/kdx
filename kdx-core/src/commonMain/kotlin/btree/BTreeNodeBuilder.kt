package kdx.btree

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)
internal fun <T : LeafInfo> buildBTree(@BuilderInference builderAction: BTreeNodeBuilder<T>.() -> Unit): BTreeNode<T> {
    contract { callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE) }
    val builder = BTreeNodeBuilder<T>()
    builder.builderAction()
    val tree = builder.build()
    return if (tree.isEmpty) emptyBTreeNode() else tree
}

internal class BTreeNodeBuilder<T : LeafInfo> {
    private val leaves = mutableListOf<BTreeNode<T>>()

    fun build(): BTreeNode<T> = merge(leaves)

    operator fun get(index: Int): BTreeNode<T> = leaves[index]

    fun add(element: BTreeNode<T>) {
        leaves.add(element)
    }

    fun add(element: BTreeNode<T>, range: IntRange) {
        //TODO: should check if its working
        // as intended.
        leaves.add(element.subSequence(range))
    }

    fun addAll(elements: List<BTreeNode<T>>) {
        leaves.addAll(elements)
    }

    fun remove(element: BTreeNode<T>) {
        leaves.remove(element)
    }
}
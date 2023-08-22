package keb.ropes.ot

import keb.ropes.BTreeNode
import keb.ropes.Delta
import keb.ropes.NodeInfo
import keb.ropes.buildDelta

fun <T : NodeInfo> simpleEdit(
    range: IntRange,
    node: BTreeNode<T>,
    baseLen: Int
): Delta<T> = buildDelta(baseLen) {
    if (node.isEmpty) {
        delete(range)
    } else {
        replace(range, node)
    }
}
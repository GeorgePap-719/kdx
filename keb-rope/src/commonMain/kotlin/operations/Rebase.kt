package keb.ropes.operations

import keb.ropes.FullPriority
import keb.ropes.Revision
import keb.ropes.Rope
import keb.ropes.Subset

/// Rebase `b_new` on top of `expand_by` and return revision contents that can be appended as new
/// revisions on top of the revisions represented by `expand_by`.
fun rebase(
    expandBy: SeriesOfPropsAndTrans,
    ops: List<DeltaOp>,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset,
    maxUndoSoFar: Int
): Rebaseable {
    val revisions: MutableList<Revision> = ArrayList(ops.size)
    val nextExpandBy: MutableList<Pair<FullPriority, Subset>> = ArrayList(expandBy.size)
    for (op in ops) {
        val fullPriority = FullPriority(op.priority, op.id.sessionId)
        for ((transformPriority, transformInsert) in expandBy) {
            fullPriority
            val after = fullPriority >= transformPriority
        }
    }

}

class Rebaseable(
    val revisions: List<Revision>,
    val text: Rope,
    val tombstones: Rope,
    val deletesFromUnion: Subset
)
package keb.ropes.ot

import keb.ropes.*

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
        for ((transformPriority, transformInserts) in expandBy) {
            // Should never be ==
            assert { fullPriority.compareTo(transformPriority) != 0 }
            val after = fullPriority >= transformPriority
            // d-expand by other
            val inserts = op.inserts.transformExpand(transformInserts, after)
            // trans-expand other by expanded so they have the same context
            val inserted = inserts// TODO insertedSubset()

        }
    }
    TODO()
}

class Rebaseable(
    val revisions: List<Revision>,
    val text: Rope,
    val tombstones: Rope,
    val deletesFromUnion: Subset
)
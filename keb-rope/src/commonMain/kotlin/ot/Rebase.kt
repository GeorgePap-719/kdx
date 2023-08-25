package keb.ropes.ot

import keb.ropes.*
import keb.ropes.internal.replaceAll

/**
 * Rebase [ops] on top of [expandBy],
 * and return revision contents that can be appended as new revisions on top of revisions represented by [expandBy].
 */
fun MutableEngine.rebase(
    expandBy: MutableList<Pair<FullPriority, Subset>>,
    ops: List<DeltaOp>,
    // Kind of tricky parameter,
    // in the original implementation
    // author passes this as `mut`
    // but when calling the function,
    // the variable is a `let` (immutable).
    // From semantics perspective,
    // it does not make much sense
    // to mutate this variable directly
    // on the existing item in the collection.
    // As we use it for constructing the new (rebased) version.
    maxUndoSoFar: Int
): RebasedOp {
    val appRevisions: MutableList<Revision> = ArrayList(ops.size)
    var nextExpandBy: MutableList<Pair<FullPriority, Subset>> = ArrayList(expandBy.size)
    for (op in ops) {
        var inserts: InsertDelta<RopeLeaf>? = null
        var deletes: Subset? = null
        val fullPriority = FullPriority(op.priority, op.id.sessionId)
        for ((transformPriority, transformInserts) in expandBy) {
            // Should never be ==
            assert { fullPriority.compareTo(transformPriority) != 0 }
            val after = fullPriority >= transformPriority
            // d-expand by other
            inserts = op.inserts.transformExpand(transformInserts, after)
            // trans-expand other by expanded so they have the same context
            val inserted = inserts.getInsertedSubset()
            val newTransformInserts = transformInserts.transformExpand(inserted)
            // The `deletes` are already after our inserts,
            // but we need to include the other inserts.
            deletes = op.deletes.transformExpand(newTransformInserts)
            // On the next step,
            // we want things in `expandBy`
            // to have `op` in the context.
            nextExpandBy.add(transformPriority to newTransformInserts)
        }
        check(inserts != null)
        check(deletes != null)
        val textInserts = inserts.transformShrink(deletesFromUnion)
        val textWithInserts = textInserts.applyTo(text)
        val inserted = inserts.getInsertedSubset()

        val expandedDeletesFromUnion = deletesFromUnion.transformExpand(inserted)
        val newDeletesFromUnion = expandedDeletesFromUnion.union(deletes)
        val (newText, newTombstones) = shuffle(
            textWithInserts,
            tombstones,
            expandedDeletesFromUnion,
            newDeletesFromUnion
        )

        tryUpdateText(newText)
        tryUpdateTombstones(newTombstones)
        tryUpdateDeletesFromUnion(newDeletesFromUnion)

        val maxUndoSoFarAfterRebased = maxOf(maxUndoSoFar, op.undoGroup)
        appRevisions.add(
            Revision(
                id = op.id,
                maxUndoSoFar = maxUndoSoFarAfterRebased,
                edit = Edit(op.priority, op.undoGroup, deletes, inserted)
            )
        )
        expandBy.replaceAll(nextExpandBy)
        nextExpandBy = ArrayList(expandBy.size)
    }
    return RebasedOp(appRevisions, text, tombstones, deletesFromUnion)
}

class RebasedOp(
    val newRevisions: List<Revision>,
    val text: Rope,
    val tombstones: Rope,
    val deletesFromUnion: Subset
)

/**
 * An integer pointer to allow mutating original `maxUndoSoFar` var.
 */
class MaxUndoSoFarRef(var value: Int)
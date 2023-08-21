package keb.ropes.operations

import keb.ropes.*

/// Transform `revs`, which doesn't include information on the actual content of the operations,
/// into an `InsertDelta`-based representation that does by working backward from the text and tombstones.
fun computeDeltas(
    revisions: List<Revision>,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset
): List<DeltaOp> {
    val ops: MutableList<DeltaOp> = ArrayList(revisions.size)
    var curAllInserts = Subset(deletesFromUnion.length())
    for (revision in revisions.reversed()) {
        when (val content = revision.edit) {
            is Edit -> {
                val olderAllInserts = content.inserts.transformUnion(curAllInserts)
                val _tombstones = shuffleTombstones(text, tombstones, deletesFromUnion, olderAllInserts)
                val delta = synthesize(_tombstones.root, olderAllInserts, curAllInserts)
                val (inserts, _) = delta.factor()
                ops.add(
                    DeltaOp(
                        revision.id,
                        content.priority,
                        content.undoGroup,
                        inserts,
                        content.deletes
                    )
                )
                curAllInserts = olderAllInserts
            }

            is Undo -> error("Merge undo is not supported yet")
        }
    }
    return ops
}

class DeltaOp(
    val id: RevId,
    val priority: Int,
    val undoGroup: Int,
    val inserts: InsertDelta<RopeLeaf>,
    val deletes: Subset
)
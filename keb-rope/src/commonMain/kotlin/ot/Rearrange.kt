package keb.ropes.operations

import keb.ropes.*

/// Returns the operations in `revs` that don't have their `rev_id` in
/// `base_revs`, but modified so that they are in the same order but based on
/// the `base_revs`. This allows the rest of the merge to operate on only
/// revisions not shared by both sides.
///
/// Conceptually, see the diagram below, with `.` being base revs and `n` being
/// non-base revs, `N` being transformed non-base revs, and rearranges it:
/// .n..n...nn..  -> ........NNNN -> returns vec![N,N,N,N]
fun rearrange(
    revisions: List<Revision>,
    baseRevIds: Set<RevId>,
    headLen: Int
): List<Revision> {
    // `Transform` representing the characters added by common revisions after a point.
    var transform = Subset(headLen)
    val ops: MutableList<Revision> = ArrayList(revisions.size - baseRevIds.size)
    for (revision in revisions.reversed()) {
        val isBase = baseRevIds.contains(revision.id)
        val contents = when (val content = revision.edit) {
            is Edit -> {
                if (isBase) {
                    transform = content.inserts.transformUnion(transform)
                    null
                } else {
                    // Fast-forward this revision
                    // over all common ones after it.
                    val transformedInserts = content.inserts.transformExpand(transform)
                    val transformedDeletes = content.deletes.transformExpand(transform)
                    // We don't want new revisions
                    // before this to be transformed after us.
                    transform = transform.transformShrink(transformedInserts)
                    Edit(
                        content.priority,
                        content.undoGroup,
                        transformedInserts,
                        transformedDeletes
                    )
                }
            }

            is Undo -> error("Merge undo is not supported yet")
        }
        if (contents != null) {
            ops.add(Revision(revision.id, revision.maxUndoSoFar, contents))
        }
    }
    //TODO: research why we reverse here!
    // Though, it is self-explanatory at some point.
    ops.reverse()
    return ops
}
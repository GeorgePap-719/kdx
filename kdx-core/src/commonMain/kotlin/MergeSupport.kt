package kdx

// -------------------------------- merge-support --------------------------------

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
    baseRevisionIds: Set<RevisionId>,
    headLength: Int
): List<Revision> {
    // `Transform` representing the characters added by common revisions after a point.
    var transform = Subset(headLength)
    val ops: MutableList<Revision> = ArrayList(revisions.size - baseRevisionIds.size)
    for (revision in revisions.reversed()) {
        val isBase = baseRevisionIds.contains(revision.id)
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

/// Computes a series of priorities and "transforms" for the deltas on the right
/// from the new revisions on the left.
///
/// Applies an optimization where it combines sequential revisions with the
/// same priority into one "transform" to decrease the number of transforms that
/// have to be considered in `rebase` substantially for normal editing
/// patterns. Any large runs of typing in the same place by the same user (e.g
/// typing a paragraph) will be combined into a single segment in a transform
/// as opposed to thousands of revisions.
fun computeTransforms(revisions: List<Revision>): MutableList<Pair<FullPriority, Subset>> {
    val list = buildList<Pair<FullPriority, Subset>> {
        var lastPriority: Int? = null
        for (revision in revisions) {
            when (val content = revision.edit) {
                is Edit -> {
                    if (content.inserts.isEmpty()) continue
                    if (lastPriority != null && lastPriority == content.priority) {
                        val last = last()
                        val newLast = last.first to last.second.transformUnion(content.inserts)
                        this[this.lastIndex] = newLast
                    } else {
                        lastPriority = content.priority
                        val priority = FullPriority(content.priority, revision.id.sessionId)
                        add(priority to content.inserts)
                    }
                }

                is Undo -> continue
            }
        }
    }
    return list.toMutableList()
}

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
    ops.reverse()
    return ops
}

class DeltaOp(
    val id: RevisionId,
    val priority: Int,
    val undoGroup: Int,
    val inserts: InsertDelta<RopeLeaf>,
    val deletes: Subset
)

/// Find an index before which everything is the same
fun List<Revision>.findBaseIndex(other: List<Revision>): Int {
    assert { this.isNotEmpty() && other.isNotEmpty() }
    assert { this.first().id == other.first().id }
    return 1
}

/**
 * Returns a [Set] containing all [revision-ids][RevisionId] that are contained by both this [List] and the specified [List].
 */
fun List<Revision>.findCommon(other: List<Revision>): Set<RevisionId> {
    val thisIds = this.map { it.id }
    val otherIds = other.map { it.id }
    return thisIds intersect otherIds.toSet() // optimization: toSet()
}
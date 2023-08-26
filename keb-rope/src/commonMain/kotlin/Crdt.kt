package keb.ropes

import keb.ropes.internal.symmetricDifference

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
    val id: RevId,
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
 * Returns a [Set] containing all [revision-ids][RevId] that are contained by both this [List] and the specified [List].
 */
fun List<Revision>.findCommon(other: List<Revision>): Set<RevId> {
    val thisIds = this.map { it.id }
    val otherIds = other.map { it.id }
    return thisIds intersect otherIds.toSet() // optimization: toSet()
}

// -------------------------------- undo --------------------------------

// This computes undo all the way from the beginning.
fun MutableEngine.computeUndo(groups: Set<Int>): Pair<Revision, Subset> {
    val toggledGroups = undoneGroups.symmetricDifference(groups).toSet()
    val firstCandidate = findFirstUndoCandidateIndex(toggledGroups)
    // About `false` below:
    // don't invert undos
    // since our `firstCandidate`
    // is based on the current undo set,
    // not past.
    var deletesFromUnion = getDeletesFromUnionBeforeIndex(firstCandidate, false)
    val revView = revisions.subList(firstCandidate, revisions.size)
    for (revision in revView) {
        val content = revision.edit
        if (content !is Edit) continue
        if (groups.contains(content.undoGroup)) {
            if (content.inserts.isNotEmpty()) deletesFromUnion = deletesFromUnion.transformUnion(content.inserts)
        } else {
            if (content.inserts.isNotEmpty()) deletesFromUnion = deletesFromUnion.transformExpand(content.inserts)
            if (content.deletes.isNotEmpty()) deletesFromUnion = deletesFromUnion.union(content.deletes)
        }
    }
    val deletesXor = deletesFromUnion.xor(deletesFromUnion)
    val maxUndoSoFar = revisions.last().maxUndoSoFar
    val newRev = Revision(nextRevId, maxUndoSoFar, Undo(toggledGroups, deletesXor))
    return newRev to deletesFromUnion
}

/**
 * Returns the first [Revision] that could be affected by toggling a set of undo groups.
 */
private fun Engine.findFirstUndoCandidateIndex(toggledGroups: Set<Int>): Int {
    // There are no toggled groups,
    // return past-end.
    if (toggledGroups.isEmpty()) return revisions.size
    // Find the lowest toggled undo group number.
    val lowest = toggledGroups.first()
    for ((i, revision) in revisions.withIndex().reversed()) {
        if (revision.maxUndoSoFar < lowest) return i + 1 // +1 since we know the one we just found doesn't have it
    }
    // If not found in iteration,
    // then the first index is candidate.
    return 0
}

// -------------------------------- edit --------------------------------

/**
 * Returns a [Delta] that, when applied to [baseRevision], results in the current [head].
 * Otherwise, returns failed result if there is not at least one edit.
 */
fun Engine.tryDeltaRevHead(baseRevision: RevToken): EngineResult<DeltaRopeNode> {
    val index = indexOfRev(baseRevision)
    if (index == -1) return EngineResult.failure(EngineResult.MissingRevision(baseRevision))
    val prevFromUnion = getDeletesFromCurUnionForIndex(index)
    val oldTombstones = shuffleTombstones(
        text,
        tombstones,
        deletesFromUnion,
        prevFromUnion
    )
    val delta = synthesize(oldTombstones.root, prevFromUnion, deletesFromUnion)
    return EngineResult.success(delta)
}

fun Engine.mkNewRev(
    newPriority: Int,
    undoGroup: Int,
    baseRevision: RevToken,
    delta: DeltaRopeNode
): EngineResult<EditResult> {
    val index = indexOfRev(baseRevision)
    if (index == -1) return EngineResult.failure(EngineResult.MissingRevision(baseRevision))
    val (insertDelta, deletes) = delta.factor()
    // Rebase delta
    // to be on the base_rev union
    // instead of the text.
    val deletesAtRev = getDeletesFromUnionForIndex(index)
    // Validate delta.
    if (insertDelta.baseLen != deletesAtRev.lengthAfterDelete()) {
        return EngineResult.failure(EngineResult.MalformedDelta(insertDelta.baseLen, deletesAtRev.lengthAfterDelete()))
    }
    var unionInsertDelta = insertDelta.transformExpand(deletesAtRev, true)
    var newDeletes = deletes.transformExpand(deletesAtRev)
    // Rebase the delta to be on the head union
    // instead of the baseRevision union.
    val newFullPriority = FullPriority(newPriority, sessionId)
    val revsView = revisions.subList(index + 1, revisions.size)
    for (revision in revsView) {
        val content = revision.edit
        if (content is Edit) {
            if (content.inserts.isEmpty()) continue
            val fullPriority = FullPriority(content.priority, revision.id.sessionId)
            assert { newFullPriority != fullPriority } // should never be ==
            val after = newFullPriority >= fullPriority
            unionInsertDelta = unionInsertDelta.transformExpand(content.inserts, after)
            newDeletes = newDeletes.transformExpand(content.inserts)
        }
    }
    // Rebase the deletion
    // to be after the inserts
    // instead of directly on the head union.
    val newInserts = unionInsertDelta.getInsertedSubset()
    if (newInserts.isNotEmpty()) newDeletes = newDeletes.transformExpand(newInserts)
    // Rebase insertions on `text` and apply.
    val curDeletesFromUnion = deletesFromUnion
    val textInsertDelta = unionInsertDelta.transformShrink(curDeletesFromUnion)
    val textWithInserts = textInsertDelta.applyTo(text)
    val rebasedDeletesFromUnion = curDeletesFromUnion.transformExpand(newInserts)
    // Is the new edit in an undo group that was already undone due to concurrency?
    val undone = undoneGroups.contains(undoGroup)
    val toDelete = if (undone) newInserts else newDeletes
    val newDeletesFromUnion = rebasedDeletesFromUnion.union(toDelete)
    // Move deleted or undone-inserted "things"
    // from text to tombstones.
    val (newText, newTombstones) = shuffle(
        textWithInserts,
        tombstones,
        rebasedDeletesFromUnion,
        newDeletesFromUnion
    )
    val headRevision = revisions.last()
    val result = EditResult(
        Revision(
            id = nextRevId,
            maxUndoSoFar = maxOf(undoGroup, headRevision.maxUndoSoFar),
            edit = Edit(
                priority = newPriority,
                undoGroup = undoGroup,
                inserts = newInserts,
                deletes = newDeletes
            ),
        ),
        newText = newText,
        newTombstones = newTombstones,
        newDeletesFromUnion = newDeletesFromUnion
    )
    return EngineResult.success(result)
}

data class EditResult(val newRev: Revision, val newText: Rope, val newTombstones: Rope, val newDeletesFromUnion: Subset)

// -------------------------------- shared-helpers --------------------------------

/// Move sections from text to tombstones and vice versa based on a new and old set of deletions.
/// Returns a tuple of a new text `Rope` and a new `Tombstones` rope described by `new_deletes_from_union`.
fun shuffle(
    text: Rope,
    tombstones: Rope,
    oldDeletesFromUnion: Subset,
    newDeletesFromUnion: Subset
): Pair<Rope, Rope> {
    // Delta that deletes the right bits from the text
    val deletesDelta = synthesize(tombstones.root, oldDeletesFromUnion, newDeletesFromUnion)
    val newText = deletesDelta.applyTo(text)
    val newTombstones = shuffleTombstones(text, tombstones, oldDeletesFromUnion, newDeletesFromUnion)
    return newText to newTombstones
}

/// Move sections from text to tombstones and out of tombstones based on a new and old set of deletions
private fun shuffleTombstones(
    text: Rope,
    tombstones: Rope,
    oldDeletesFromUnion: Subset,
    newDeletesFromUnion: Subset
): Rope {
    // Taking the complement of deletes_from_union leads to an interleaving valid for swapped text and tombstones,
    // allowing us to use the same method to insert the text into the tombstones.
    val inverseTombstonesMap = oldDeletesFromUnion.complement()
    val moveDelta = synthesize(text.root, inverseTombstonesMap, newDeletesFromUnion.complement())
    return moveDelta.applyTo(tombstones)
}

internal fun DeltaRopeNode.applyTo(rope: Rope): Rope {
    val newRoot = applyTo(rope.root)
    return Rope(newRoot)
}
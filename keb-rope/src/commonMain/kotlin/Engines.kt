package keb.ropes

import keb.ropes.EngineResult.MalformedDelta
import keb.ropes.EngineResult.MissingRevision
import keb.ropes.ot.shuffle
import keb.ropes.ot.shuffleTombstones
import keb.ropes.ot.synthesize


/**
 * Returns the first [Revision] that could be affected by toggling a set of undo groups.
 */
fun Engine.findFirstUndoCandidateIndex(toggledGroups: Set<Int>): Int {
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

// since undo and gc replay history with transforms, we need an empty set
// of the union string length *before* the first revision.
fun Engine.emptySubsetBeforeFirstRev(): Subset {
    val first = revisions.first()
    // It will be immediately transform_expanded by inserts
    // if it is an `Edit`,
    // so length must be before.
    val len = when (val content = first.edit) {
        is Edit -> content.inserts.count(CountMatcher.ZERO)
        is Undo -> content.deletesBitXor.count(CountMatcher.ALL)
    }
    return Subset(len)
}

fun MutableEngine.editRev(
    priority: Int,
    undoGroup: Int,
    baseRev: RevToken,
    delta: DeltaRopeNode
) =
    tryEditRev(priority, undoGroup, baseRev, delta).getOrNull() ?: error("panic!") //TODO: research better handling

fun Engine.mkNewRev(
    newPriority: Int,
    undoGroup: Int,
    baseRevision: RevToken,
    delta: DeltaRopeNode
): EngineResult<EditResult> {
    val index = indexOfRev(baseRevision)
    if (index == -1) return EngineResult.failure(MissingRevision(baseRevision))
    val (insertDelta, deletes) = delta.factor()
    // Rebase delta
    // to be on the base_rev union
    // instead of the text.
    val deletesAtRev = getDeletesFromUnionForIndex(index)
    // Validate delta.
    if (insertDelta.baseLen != deletesAtRev.lengthAfterDelete()) {
        return EngineResult.failure(MalformedDelta(insertDelta.baseLen, deletesAtRev.lengthAfterDelete()))
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
        text = newText,
        tombstones = newTombstones,
        deletesFromUnion = newDeletesFromUnion
    )
    return EngineResult.success(result)
}

data class EditResult(val revision: Revision, val text: Rope, val tombstones: Rope, val deletesFromUnion: Subset)

/**
 * Returns a [Delta] that, when applied to [baseRevision], results in the current [head].
 * Otherwise, returns failed result if there is not at least one edit.
 */
fun Engine.tryDeltaRevHead(baseRevision: RevToken): EngineResult<DeltaRopeNode> {
    val index = indexOfRev(baseRevision)
    if (index == -1) return EngineResult.failure(MissingRevision(baseRevision))
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
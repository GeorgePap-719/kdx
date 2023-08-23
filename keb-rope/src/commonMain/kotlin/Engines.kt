package keb.ropes

import keb.ropes.EngineResult.MalformedDelta
import keb.ropes.EngineResult.MissingRevision
import keb.ropes.ot.shuffle
import keb.ropes.ot.shuffleTombstones
import keb.ropes.ot.synthesize

fun Engine.mkNewRev(
    newPriority: Int,
    undoGroup: Int,
    baseRevision: RevToken,
    delta: Delta<RopeLeaf>
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
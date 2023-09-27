package kdx

/**
 * Returns a [Delta] that, when applied to [baseRevision], results in the current [head].
 * Otherwise, returns failed result if there is not at least one edit.
 */
fun Engine.tryDeltaRevisionHead(baseRevision: RevisionToken): EngineResult<DeltaRopeNode> {
    val index = indexOfRevision(baseRevision)
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

// This operation has a number of stages;
// see https://xi-editor.io/docs/crdt-details.html#engineedit_rev
// for the algorithmic details.
/// Returns a tuple of a new `Revision` representing the edit based on the
/// current head, a new text `Rope`, a new tombstones `Rope` and a new `deletes_from_union`.
/// Returns an [`Error`] if `base_rev` cannot be found, or `delta.base_len`
/// does not equal the length of the text at `base_rev`.
fun Engine.mkNewRevision(
    newPriority: Int,
    undoGroup: Int,
    baseRevision: RevisionToken,
    delta: DeltaRopeNode
): EngineResult<EditResult> {
    val index = indexOfRevision(baseRevision)
    if (index == -1) return EngineResult.failure(EngineResult.MissingRevision(baseRevision))
    val (insertDelta, deletes) = delta.factor()
    println("index:$index")
    // Rebase delta
    // to be on the baseRevision union
    // instead of the text.
    val deletesAtRev = getDeletesFromUnionForIndex(index)
    println(deletesAtRev)
    // Validate delta.
    if (insertDelta.baseLength != deletesAtRev.lengthAfterDelete()) {
        return EngineResult.failure(
            EngineResult.MalformedDelta(
                revisionLength = deletesAtRev.lengthAfterDelete(),
                deltaLength = insertDelta.baseLength
            )
        )
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
            id = nextRevisionId,
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

data class EditResult(
    val newRevision: Revision,
    val newText: Rope,
    val newTombstones: Rope,
    val newDeletesFromUnion: Subset
)
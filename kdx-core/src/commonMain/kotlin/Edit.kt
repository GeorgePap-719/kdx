package kdx

/**
 * Returns a [Delta] that, when applied to [baseRevision], results in the current [head],
 * or returns [failed][EngineResult.Failed] result if there is not at least one edit.
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

/**
 * Creates a new [edit][EditResult] based in the current head.
 * If the the [baseRevision] cannot be found, or the delta's base-length is not equal of the text's base-length at [baseRevision],
 * it returns a [failed][EngineResult.Failed] result.
 */
fun Engine.createRevision(
    newPriority: Int,
    undoGroup: Int,
    baseRevision: RevisionToken,
    delta: DeltaRopeNode
): EngineResult<EditResult> {
    // This operation has a number of stages;
    // see https://xi-editor.io/docs/crdt-details.html#engineedit_rev
    // for the algorithmic details.
    //
    // Find `baseRevision` then move on, as new edit will be based on it.
    val index = indexOfRevision(baseRevision)
    if (index == -1) return EngineResult.failure(EngineResult.MissingRevision(baseRevision))
    val (inserts, deletes) = delta.factor()
    // Rebase delta to be on the `baseRevision` union instead of the text.
    val deletesAtRevision = getDeletesFromUnionForIndex(index)
    // Since we base new edit on `baseRevision`, then we should check
    // if `inserts` have the same base-length with `deletesAtRevision`
    // to ensure we refer to the same base document.
    if (inserts.baseLength != deletesAtRevision.lengthAfterDelete()) {
        return EngineResult.failure(
            EngineResult.MalformedDelta(
                revisionLength = deletesAtRevision.lengthAfterDelete(),
                deltaLength = inserts.baseLength
            )
        )
    }
    var unionInsertDelta = inserts.transformExpand(deletesAtRevision, true)
    var newDeletes = deletes.transformExpand(deletesAtRevision)
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
    // Rebase the deletion to be after the inserts
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
    // Move deleted or undone-inserted "characters" from text to tombstones.
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

/**
 * Represents a new "edit".
 */
data class EditResult(
    val newRevision: Revision,
    val newText: Rope,
    val newTombstones: Rope,
    val newDeletesFromUnion: Subset
)
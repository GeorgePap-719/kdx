package kdx

/**
 * Returns a [Delta] that, when applied to [baseRevision], results in the current [head],
 * or returns [failed][EngineResult.Failed] result if there is not at least one edit.
 */
fun Engine.tryDeltaRevisionHead(baseRevision: RevisionToken): EngineResult<DeltaRopeNode> {
    val index = indexOfRevision(baseRevision)
    if (index == -1) return EngineResult.failure(EngineResult.MissingRevision(baseRevision))
    val oldDeletesFromUnion = getDeletesFromCurUnionForIndex(index)
    val oldTombstones = shuffleTombstones(
        text,
        tombstones,
        deletesFromUnion,
        oldDeletesFromUnion
    )
    val delta = synthesize(oldTombstones, oldDeletesFromUnion, deletesFromUnion)
    return EngineResult.success(delta)
}

/**
 * Creates a new [edit][EditResult] based on the current head.
 * If the the [baseRevision] cannot be found, or the delta's base-length is not equal of the text's base-length at [baseRevision],
 * it returns a [failed][EngineResult.Failed] result.
 */
fun Engine.createRevision(
    priority: Int,
    undoGroup: Int,
    baseRevision: RevisionToken,
    delta: DeltaRopeNode
): EngineResult<EditResult> {
    // This operation has a number of stages;
    // see https://xi-editor.io/docs/crdt-details.html#engineedit_rev
    // for the algorithmic details.
    //
    // Find `baseRevision` as new edit will be based on it.
    val index = indexOfRevision(baseRevision)
    if (index == -1) return EngineResult.failure(EngineResult.MissingRevision(baseRevision))
    // Get a subset with `inserts` and a subset with `deletes`.
    val (inserts, deletes) = delta.factor()
    // Work backwards to retrieve `deletesFromUnion` at `index`.
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
    // Rebase delta to be on the `baseRevision` union instead of the text at the time of `baseRevision`.
    var unionInsertDelta = inserts.transformExpand(deletesAtRevision, true)
    var deletesExpanded = deletes.transformExpand(deletesAtRevision)
    // Rebase the delta to be on the head union instead of the `baseRevision` union.
    val newFullPriority = FullPriority(priority, sessionId)
    val revsView = revisions.subList(index + 1, revisions.size)
    for (revision in revsView) {
        val content = revision.edit
        if (content is Edit) {
            if (content.inserts.isEmpty()) continue
            val fullPriority = FullPriority(content.priority, revision.id.sessionId)
            val after = newFullPriority >= fullPriority
            unionInsertDelta = unionInsertDelta.transformExpand(content.inserts, after)
            deletesExpanded = deletesExpanded.transformExpand(content.inserts)
        }
    }
    // Rebase the `deletes` to be after the inserts instead of directly on the head union.
    val newInserts = unionInsertDelta.getInsertedSubset()
    if (newInserts.isNotEmpty()) deletesExpanded = deletesExpanded.transformExpand(newInserts)
    // Rebase insertions on `text` and apply.
    val curDeletesFromUnion = deletesFromUnion
    val textInsertDelta = unionInsertDelta.transformShrink(curDeletesFromUnion)
    val textWithInserts = textInsertDelta.applyTo(text)
    val rebasedDeletesFromUnion = curDeletesFromUnion.transformExpand(newInserts)
    // Is the new edit in an undo group that was already undone due to concurrency?
    val undone = undoneGroups.contains(undoGroup)
    val toDelete = if (undone) newInserts else deletesExpanded
    val newDeletesFromUnion = rebasedDeletesFromUnion.union(toDelete)
    // Move deleted or undone-inserted "characters" from text to tombstones.
    val (newText, newTombstones) = shuffle(
        text = textWithInserts,
        tombstones = tombstones,
        fromDeletesFromUnion = rebasedDeletesFromUnion,
        toDeletesFromUnion = newDeletesFromUnion
    )
    val headRevision = revisions.last()
    val result = EditResult(
        Revision(
            id = nextRevisionId,
            maxUndoSoFar = maxOf(undoGroup, headRevision.maxUndoSoFar),
            edit = Edit(
                priority = priority,
                undoGroup = undoGroup,
                inserts = newInserts,
                deletes = deletesExpanded
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
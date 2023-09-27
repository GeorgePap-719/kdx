package kdx

import kdx.internal.symmetricDifference

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
    val newRev = Revision(nextRevisionId, maxUndoSoFar, Undo(toggledGroups, deletesXor))
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
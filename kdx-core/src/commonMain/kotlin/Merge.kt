package kdx

import kdx.internal.replaceAll

// -------------------------------- merge-impl --------------------------------

/**
 * Rebases [deltaOps] on top of [expandBy], and returns the revision contents that can be appended as new revisions on
 * top of the revisions represented by [expandBy].
 *
 * This function is named "rebase" because it is analogous to "git-rebase" operation.
 */
fun rebase(
    /* The new revisions from `this` engine. */
    expandBy: List<Pair<FullPriority, Subset>>,
    /* Practically this is the new revisions
     from the `other` engine. */
    deltaOps: List<DeltaOp>,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset,
    // Kind of tricky parameter,
    // in the original implementation author passes this as `mut`
    // but when calling the function, the variable is a `let` (immutable).
    // From semantics perspective, it does not make much sense to mutate this variable directly
    // on the existing item in the collection.
    // Since we use it for constructing the new (rebased) version.
    maxUndoSoFar: Int
): RebaseResult {
    var mutText = text
    var mutTombstones = tombstones
    var mutDeletesFromUnion = deletesFromUnion
    val mutExpandBy = expandBy.toMutableList()
    var mutMaxUndoSoFar = maxUndoSoFar
    val revisions: MutableList<Revision> = ArrayList(deltaOps.size)
    var nextExpandBy: MutableList<Pair<FullPriority, Subset>> = ArrayList(mutExpandBy.size)
    for (deltaOp in deltaOps) {
        var inserts = deltaOp.inserts
        var deletes = deltaOp.deletes
        val fullPriority = FullPriority(deltaOp.priority, deltaOp.id.sessionId)
        for ((transformPriority, transformInserts) in mutExpandBy) {
            // Should never be ==
            assert { fullPriority.compareTo(transformPriority) != 0 }
            val after = fullPriority >= transformPriority
            // delta-expand by `other`.
            inserts = inserts.transformExpand(transformInserts, after)
            // transformExpand() other by expanded, so they have the same context.
            val inserted = inserts.getInsertedSubset()
            val newTransformInserts = transformInserts.transformExpand(inserted)
            // The `deletes` are already after our inserts,
            // but we need to include the other inserts.
            deletes = deletes.transformExpand(newTransformInserts)
            // On the next step,
            // we want things in `expandBy`
            // to have the `deltaOp` in the context.
            nextExpandBy.add(transformPriority to newTransformInserts)
        }
        // Update the text and tombstones.
        val textInserts = inserts.transformShrink(mutDeletesFromUnion)
        val textWithInserts = textInserts.applyTo(mutText)
        val inserted = inserts.getInsertedSubset()
        val expandedDeletesFromUnion = mutDeletesFromUnion.transformExpand(inserted)
        val newDeletesFromUnion = expandedDeletesFromUnion.union(deletes)
        val shuffled = shuffle(
            textWithInserts,
            mutTombstones,
            expandedDeletesFromUnion,
            newDeletesFromUnion
        )
        mutText = shuffled.first
        mutTombstones = shuffled.second
        mutDeletesFromUnion = newDeletesFromUnion
        // Get the max from both to not miss any "undo".
        mutMaxUndoSoFar = maxOf(mutMaxUndoSoFar, deltaOp.undoGroup)
        revisions.add(
            // Create a revision to append in history.
            Revision(
                id = deltaOp.id,
                maxUndoSoFar = mutMaxUndoSoFar,
                edit = Edit(
                    priority = deltaOp.priority,
                    undoGroup = deltaOp.undoGroup,
                    inserts = inserted,
                    deletes = deletes
                )
            )
        )
        // Update the transforms for the next round,
        // to include the `deltaOps` inserts.
        // And effectively, they become part of the base for both sides.
        mutExpandBy.replaceAll(nextExpandBy)
        nextExpandBy = ArrayList(mutExpandBy.size)
    }
    return RebaseResult(revisions, mutText, mutTombstones, mutDeletesFromUnion)
}

/**
 * Represents the rebased state.
 */
class RebaseResult(
    /**
     * The new revisions to be appended to history.
     */
    val revisions: List<Revision>,
    val text: Rope,
    val tombstones: Rope,
    val deletesFromUnion: Subset
)

/**
 * Helper function to compute the new "inserts" from `this` engine, along with its "priority" in order to
 * resolve the order of concurrent "inserts".
 *
 * This function also applies an optimization where it combines sequential revisions with the same priority
 * into one "transform" (insert) to decrease the number of transforms that have to be considered in `rebase()` phase
 * substantially for normal editing patterns. Any large runs of typing in the same place by the same user (e.g. typing a paragraph)
 * will be combined into a single segment in a transform as opposed to thousands of revisions.
 */
fun computeTransforms(revisions: List<Revision>): List<Pair<FullPriority, Subset>> = buildList {
    var lastPriority: Int? = null
    for (revision in revisions) {
        when (val content = revision.edit) {
            is Edit -> {
                if (content.inserts.isEmpty()) continue
                // Optimization for combining sequential revisions with the same priority.
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

/**
 * Transforms the given [revisions] relative to [text], [tombstones] and [deletesFromUnion]
 * into a representation that encodes "inserts" as an [InsertDelta] that can be "transformed"
 * using [InsertDelta.transformExpand] and eventually applied to the `text` of `this` engine.
 */
fun computeDeltas(
    revisions: List<Revision>,
    text: Rope,
    tombstones: Rope,
    deletesFromUnion: Subset
): List<DeltaOp> {
    val deltaOps: MutableList<DeltaOp> = ArrayList(revisions.size)
    var curAllInserts = Subset(deletesFromUnion.length())
    for (revision in revisions.reversed()) {
        when (val content = revision.edit) {
            is Edit -> {
                // Do not update `curAllInserts` yet, as we use it in synthesize().
                val olderAllInserts = content.inserts.transformUnion(curAllInserts)
                val shuffledTombstones = shuffleTombstones(text, tombstones, deletesFromUnion, olderAllInserts)
                val delta = synthesize(shuffledTombstones, olderAllInserts, curAllInserts)
                val (inserts, _) = delta.factor()
                deltaOps.add(
                    DeltaOp(
                        revision.id,
                        content.priority,
                        content.undoGroup,
                        inserts,
                        content.deletes
                    )
                )
                // Update `curAllInserts`.
                curAllInserts = olderAllInserts
            }

            is Undo -> error("Merge undo is not supported yet")
        }
    }
    deltaOps.reverse()
    return deltaOps
}

/**
 * Represents the transformed new revision from "other" engine that can be applied to `this` engine's `text`.
 */
class DeltaOp(
    val id: RevisionId,
    val priority: Int,
    val undoGroup: Int,
    /**
     * The inserted characters from the revision this `DeltaOp` represents.
     */
    val inserts: InsertDelta<RopeLeaf>,
    val deletes: Subset
)

/**
 * Transforms and reorders all the new revisions on each side after all the common revisions.
 * Returns the new "computed" revisions. The key idea is to make the general "merge" algorithm easier by "ignoring"
 * any of the common revisions and just work with the new revisions on each side.
 */
fun rearrange(
    revisions: List<Revision>,
    baseRevisionIds: Set<RevisionId>,
    headLength: Int
): List<Revision> {
    // `Transform` represents the characters added by common revisions after a point.
    var transform = if (headLength == 0) emptySubset() else Subset(headLength)
    val operations: MutableList<Revision> = ArrayList(revisions.size - baseRevisionIds.size)
    // Work the revisions backwards because the `transform`
    // is based on the current's document length.
    for (revision in revisions.reversed()) {
        val isBase = baseRevisionIds.contains(revision.id)
        val contents = when (val content = revision.edit) {
            is Edit -> {
                if (isBase) {
                    transform = content.inserts.transformUnion(transform)
                    null
                } else {
                    // Fast-forward this revision over all common ones **after** it.
                    val transformedInserts = content.inserts.transformExpand(transform)
                    val transformedDeletes = content.deletes.transformExpand(transform)
                    // We don't want new revisions before this to be transformed after us.
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
            operations.add(Revision(revision.id, revision.maxUndoSoFar, contents))
        }
    }
    // Undo the reverse.
    operations.reverse()
    return operations
}

/**
 * Tries to find the "base" of the two `Engines`.
 * The "base" is a prefix of the same length of both histories such that the set of revisions in both prefixes is the same.
 */
fun List<Revision>.findBaseIndex(other: List<Revision>): Int {
    assert { this.isNotEmpty() && other.isNotEmpty() }
    assert { this.first().id == other.first().id }
    // At the time xi was written, it always returns length of
    // `one` (it is assumed they share at least one ancestor).
    // While this is fine for now, we could provide a real implementation
    // if there is time.
    //TODO: provide proper implementation if there is time.
    return 1
}

/**
 * Returns common revisions of both engines, where they are the intersection of the two [Revision] sets after the "base".
 * Note that the common revisions aren’t necessarily in the same positions or order on each side.
 */
fun List<Revision>.findCommonRevisions(other: List<Revision>): Set<RevisionId> {
    val thisIds = this.map { it.id }
    val otherIds = other.map { it.id }
    return thisIds intersect otherIds.toSet() // optimization: toSet()
}
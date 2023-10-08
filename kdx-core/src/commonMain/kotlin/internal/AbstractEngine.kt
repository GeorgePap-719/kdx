package kdx.internal

import kdx.*

internal abstract class AbstractEngine : MutableEngine {

    override fun merge(other: Engine) {
        // First, the algorithm finds the "base",
        // so we can build the "common-revisions".
        val baseIndex = revisions.findBaseIndex(other.revisions)
        val newRevisionsFromThis = revisions.subList(baseIndex, revisions.size)
        val newRevisionsFromOther = other.revisions.subList(baseIndex, other.revisions.size)
        val commonRevisions = newRevisionsFromThis.findCommonRevisions(newRevisionsFromOther)
        // Skip common revisions on both sides to work only with the new revisions.
        val thisNewInserts = rearrange(newRevisionsFromThis, commonRevisions, deletesFromUnion.length())
        val otherNewInserts = rearrange(newRevisionsFromOther, commonRevisions, other.deletesFromUnion.length())
        // At this stage, we do have neither a `Delta` as we would in `tryEditRevision()`
        // nor an operation for resolving the order of concurrent "edits" based on priority.
        // Since the responsible operation for this is `Delta.transformExpand`,
        // and we cannot use `Subset.transformExpand` as it does a slightly different thing.
        // Use the computeDeltas() helper to "convert" the `otherNewInserts`
        // into a representation that encodes "inserts" as an `InsertDelta`.
        val deltaOps = computeDeltas(otherNewInserts, other.text, other.tombstones, other.deletesFromUnion)
        // Then use computeTransforms() helper to retrieve thisNewInserts` characters from `this` engine,
        // along with their priority in order to resolve the order of concurrent "edits".
        val expandBy = computeTransforms(thisNewInserts)
        // Before appending the changes from `other`, we need to "transform" the `deltaOps`
        // to be "rebased" on top of the `thisNewInserts` from `this`.
        val rebased = rebase(expandBy, deltaOps, maxUndoGroupId)
        // Update state.
        trySetText(rebased.text)
        trySetTombstones(rebased.tombstones)
        trySetDeletesFromUnion(rebased.deletesFromUnion)
        appendRevisions(rebased.newRevisions)
    }

    abstract fun appendRevisions(elements: List<Revision>): Boolean

    override fun rebase(
        // Probably this should be a read-only list.
        expandBy: MutableList<Pair<FullPriority, Subset>>,
        deltaOps: List<DeltaOp>,
        // Kind of tricky parameter,
        // in the original implementation author passes this as `mut`
        // but when calling the function, the variable is a `let` (immutable).
        // From semantics perspective, it does not make much sense to mutate this variable directly
        // on the existing item in the collection.
        // Since we use it for constructing the new (rebased) version.
        maxUndoSoFar: Int
    ): RebaseResult {
        val appRevisions: MutableList<Revision> = ArrayList(deltaOps.size)
        var nextExpandBy: MutableList<Pair<FullPriority, Subset>> = ArrayList(expandBy.size)
        for (deltaOp in deltaOps) {
            var inserts: InsertDelta<RopeLeaf>? = null
            var deletes: Subset? = null
            val fullPriority = FullPriority(deltaOp.priority, deltaOp.id.sessionId)
            for ((transformPriority, transformInserts) in expandBy) {
                // Should never be ==
                assert { fullPriority.compareTo(transformPriority) != 0 }
                val after = fullPriority >= transformPriority
                // d-expand by other
                inserts = deltaOp.inserts.transformExpand(transformInserts, after)
                // trans-expand other by expanded so they have the same context
                val inserted = inserts.getInsertedSubset()
                val newTransformInserts = transformInserts.transformExpand(inserted)
                // The `deletes` are already after our inserts,
                // but we need to include the other inserts.
                deletes = deltaOp.deletes.transformExpand(newTransformInserts)
                // On the next step,
                // we want things in `expandBy`
                // to have `deltaOp` in the context.
                nextExpandBy.add(transformPriority to newTransformInserts)
            }
            check(inserts != null)
            check(deletes != null)
            val textInserts = inserts.transformShrink(deletesFromUnion)
            val textWithInserts = textInserts.applyTo(text)
            val inserted = inserts.getInsertedSubset()

            val expandedDeletesFromUnion = deletesFromUnion.transformExpand(inserted)
            val newDeletesFromUnion = expandedDeletesFromUnion.union(deletes)
            val (newText, newTombstones) = shuffle(
                textWithInserts,
                tombstones,
                expandedDeletesFromUnion,
                newDeletesFromUnion
            )

            trySetText(newText)
            trySetTombstones(newTombstones)
            trySetDeletesFromUnion(newDeletesFromUnion)

            val maxUndoSoFarAfterRebased = maxOf(maxUndoSoFar, deltaOp.undoGroup)
            appRevisions.add(
                Revision(
                    id = deltaOp.id,
                    maxUndoSoFar = maxUndoSoFarAfterRebased,
                    edit = Edit(deltaOp.priority, deltaOp.undoGroup, deletes, inserted)
                )
            )
            expandBy.replaceAll(nextExpandBy)
            nextExpandBy = ArrayList(expandBy.size)
        }
        return RebaseResult(appRevisions, text, tombstones, deletesFromUnion)
    }

    // warning: this method is not thread-safe.
    override fun gc(gcGroups: Set<Int>) {
        var gcDeletes = emptySubsetBeforeFirstRevision()
        val retainRevs = mutableSetOf<RevisionId>()
        revisions.lastOrNull()?.let { retainRevs.add(it.id) }
        for (revision in revisions) {
            val content = revision.edit
            if (content !is Edit) continue
            if (!retainRevs.contains(revision.id) && gcGroups.contains(content.undoGroup)) {
                if (undoneGroups.contains(content.undoGroup)) {
                    if (content.inserts.isNotEmpty()) gcDeletes = gcDeletes.transformUnion(content.inserts)
                } else {
                    if (content.inserts.isNotEmpty()) gcDeletes = gcDeletes.transformExpand(content.inserts)
                    if (content.deletes.isNotEmpty()) gcDeletes = gcDeletes.union(content.deletes)
                }
                continue
            }
            if (content.inserts.isNotEmpty()) gcDeletes = gcDeletes.transformExpand(content.inserts)
        }
        if (gcDeletes.isNotEmpty()) {
            val notInTombstones = deletesFromUnion.complement()
            val deletesFromTombstones = gcDeletes.transformShrink(notInTombstones)
            val newTombstones = deletesFromTombstones.deleteFrom(tombstones.root)
            trySetTombstones(Rope(newTombstones))
            val newDeletesFromUnion = deletesFromUnion.transformShrink(gcDeletes)
            trySetDeletesFromUnion(newDeletesFromUnion)
        }
        val oldRevs = getAndClearRevisions()
        for (revision in oldRevs.reversed()) {
            when (val content = revision.edit) {
                is Edit -> {
                    val newGcDeletes =
                        if (content.inserts.isNotEmpty()) null else gcDeletes.transformShrink(content.inserts)
                    if (retainRevs.contains(revision.id) || gcGroups.contains(content.undoGroup)) {
                        val (inserts, deletes) = if (gcDeletes.isEmpty()) {
                            content.inserts to content.deletes
                        } else {
                            content.inserts.transformShrink(gcDeletes) to content.deletes.transformShrink(gcDeletes)
                        }
                        appendRevision(
                            Revision(
                                id = revision.id,
                                maxUndoSoFar = revision.maxUndoSoFar,
                                edit = Edit(content.priority, content.undoGroup, inserts, deletes)
                            )
                        )
                    }
                    newGcDeletes?.let { gcDeletes = it }
                }

                is Undo -> {
                    // We're super-aggressive about dropping these; after gc,
                    // the history of which `undos` were used
                    // to compute `deletesFromUnion` in edits may be lost.
                    if (retainRevs.contains(revision.id)) {
                        val newDeletesBitXor = if (gcDeletes.isEmpty()) {
                            content.deletesBitXor
                        } else {
                            content.deletesBitXor.transformShrink(gcDeletes)
                        }
                        appendRevision(
                            Revision(
                                id = revision.id,
                                maxUndoSoFar = revision.maxUndoSoFar,
                                edit = Undo(content.toggledGroups - gcGroups, newDeletesBitXor)
                            )
                        )
                    }
                }
            }
        }
        // Revert revisions in proper order,
        // as we iterated `oldRevs` in reversed order.
        reverseRevisions()
    }

    // since undo and gc replay history with transforms, we need an empty set
    // of the union string length *before* the first revision.
    private fun Engine.emptySubsetBeforeFirstRevision(): Subset {
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

    /**
     * Returns the current value of revisions and removes all elements from revisions.
     */
    private fun getAndClearRevisions(): List<Revision> {
        val cur = revisions
        clearRevisions()
        return cur
    }

    abstract fun clearRevisions()

    abstract fun reverseRevisions()

    override fun undo(groups: Set<Int>) {
        val (newRev, newDeletesFromUnion) = computeUndo(groups)
        // Update `text` and `tombstones`.
        val (newText, newTombstones) = shuffle(
            text,
            tombstones,
            deletesFromUnion,
            newDeletesFromUnion
        )
        trySetText(newText)
        trySetTombstones(newTombstones)
        trySetDeletesFromUnion(newDeletesFromUnion)
        trySetUndoneGroups(groups)
        appendRevision(newRev)
        incrementRevIdCountAndGet()
    }

    abstract fun trySetUndoneGroups(newUndoneGroups: Set<Int>): Boolean

    override fun tryEditRevision(
        priority: Int,
        undoGroup: Int,
        baseRevision: RevisionToken,
        delta: DeltaRopeNode
    ): EngineResult<Unit> {
        val result = createRevision(
            priority,
            undoGroup,
            baseRevision,
            delta
        )
        val newEdit = result.getOrElse { return EngineResult.failure(it) }
        incrementRevIdCountAndGet()
        appendRevision(newEdit.newRevision)
        trySetText(newEdit.newText)
        trySetTombstones(newEdit.newTombstones)
        trySetDeletesFromUnion(newEdit.newDeletesFromUnion)
        return EngineResult.success(Unit)
    }

    abstract fun incrementRevIdCountAndGet(): Int

    fun RevisionId.equalsInternal(other: RevisionId): Boolean {
        val baseIndex = indexOfRevision(this)
        if (baseIndex == -1) return false
        val baseSubset = getDeletesFromCurUnionForIndex(baseIndex)
        val otherIndex = indexOfRevision(other)
        if (otherIndex == -1) return false
        val otherSubset = getDeletesFromCurUnionForIndex(otherIndex)
        return baseSubset == otherSubset
    }

    abstract fun trySetText(value: Rope): Boolean

    abstract fun trySetTombstones(value: Rope): Boolean

    abstract fun trySetDeletesFromUnion(value: Subset): Boolean

    abstract fun appendRevision(element: Revision): Boolean
}
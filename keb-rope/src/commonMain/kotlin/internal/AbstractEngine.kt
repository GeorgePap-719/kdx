package keb.ropes.internal

import keb.ropes.*

internal abstract class AbstractEngine : MutableEngine {

    override fun rebase(
        expandBy: MutableList<Pair<FullPriority, Subset>>,
        ops: List<DeltaOp>,
        // Kind of tricky parameter,
        // in the original implementation
        // author passes this as `mut`
        // but when calling the function,
        // the variable is a `let` (immutable).
        // From semantics perspective,
        // it does not make much sense
        // to mutate this variable directly
        // on the existing item in the collection.
        // As we use it for constructing the new (rebased) version.
        maxUndoSoFar: Int
    ): RebasedResult {
        val appRevisions: MutableList<Revision> = ArrayList(ops.size)
        var nextExpandBy: MutableList<Pair<FullPriority, Subset>> = ArrayList(expandBy.size)
        for (op in ops) {
            var inserts: InsertDelta<RopeLeaf>? = null
            var deletes: Subset? = null
            val fullPriority = FullPriority(op.priority, op.id.sessionId)
            for ((transformPriority, transformInserts) in expandBy) {
                // Should never be ==
                assert { fullPriority.compareTo(transformPriority) != 0 }
                val after = fullPriority >= transformPriority
                // d-expand by other
                inserts = op.inserts.transformExpand(transformInserts, after)
                // trans-expand other by expanded so they have the same context
                val inserted = inserts.getInsertedSubset()
                val newTransformInserts = transformInserts.transformExpand(inserted)
                // The `deletes` are already after our inserts,
                // but we need to include the other inserts.
                deletes = op.deletes.transformExpand(newTransformInserts)
                // On the next step,
                // we want things in `expandBy`
                // to have `op` in the context.
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

            val maxUndoSoFarAfterRebased = maxOf(maxUndoSoFar, op.undoGroup)
            appRevisions.add(
                Revision(
                    id = op.id,
                    maxUndoSoFar = maxUndoSoFarAfterRebased,
                    edit = Edit(op.priority, op.undoGroup, deletes, inserted)
                )
            )
            expandBy.replaceAll(nextExpandBy)
            nextExpandBy = ArrayList(expandBy.size)
        }
        return RebasedResult(appRevisions, text, tombstones, deletesFromUnion)
    }

    override fun gc(gcGroups: Set<Int>) {
        TODO("Not yet implemented")
    }

    override fun merge(other: Engine) {
        val baseIndex = revisions.findBaseIndex(other.revisions)
        val thisToMerge = revisions.subList(baseIndex, revisions.size)
        val otherToMerge = other.revisions.subList(baseIndex, other.revisions.size)

        val common = thisToMerge.findCommon(otherToMerge)

        val thisNew = rearrange(thisToMerge, common, deletesFromUnion.length())
        val otherNew = rearrange(otherToMerge, common, other.deletesFromUnion.length())

        val otherDelta = computeDeltas(otherNew, other.text, other.tombstones, other.deletesFromUnion)
        val expandBy = computeTransforms(thisNew)

        val rebased = rebase(expandBy, otherDelta, maxUndoGroupId)

        trySetText(rebased.text)
        trySetTombstones(rebased.tombstones)
        trySetDeletesFromUnion(rebased.deletesFromUnion)
        appendRevisions(rebased.newRevisions)
    }

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

    override fun tryEditRev(
        priority: Int,
        undoGroup: Int,
        baseRev: RevToken,
        delta: DeltaRopeNode
    ): EngineResult<Unit> {
        val result = mkNewRev(
            priority,
            undoGroup,
            baseRev,
            delta
        )
        val newEdit = result.getOrElse { return EngineResult.failure(it) }
        incrementRevIdCountAndGet()
        appendRevision(newEdit.newRev)
        trySetText(newEdit.newText)
        trySetTombstones(newEdit.newTombstones)
        trySetDeletesFromUnion(newEdit.newDeletesFromUnion)
        return EngineResult.success(Unit)
    }

    fun RevId.equalsInternal(other: RevId): Boolean {
        val baseIndex = indexOfRev(this)
        if (baseIndex == -1) return false
        val baseSubset = getDeletesFromCurUnionForIndex(baseIndex)
        val otherIndex = indexOfRev(other)
        if (otherIndex == -1) return false
        val otherSubset = getDeletesFromCurUnionForIndex(otherIndex)
        return baseSubset == otherSubset
    }

    abstract fun trySetText(value: Rope): Boolean

    abstract fun trySetTombstones(value: Rope): Boolean

    abstract fun trySetDeletesFromUnion(value: Subset): Boolean

    abstract fun trySetUndoneGroups(newUndoneGroups: Set<Int>)

    abstract fun appendRevision(element: Revision): Boolean

    abstract fun appendRevisions(elements: List<Revision>): Boolean

    abstract fun incrementRevIdCountAndGet(): Int
}
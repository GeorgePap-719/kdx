package keb.ropes.internal

import keb.ropes.*
import keb.ropes.ot.*

internal abstract class AbstractEngine : MutableEngine {
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
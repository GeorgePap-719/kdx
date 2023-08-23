package keb.ropes.ot

import keb.ropes.RevId
import keb.ropes.Revision
import keb.ropes.assert

/// Find an index before which everything is the same
fun List<Revision>.findBaseIndex(other: List<Revision>): Int {
    assert { this.isNotEmpty() && other.isNotEmpty() }
    assert { this.first().id == other.first().id }
    return 1
}

/**
 * Returns a [Set] containing all [revision-ids][RevId] that are contained by both this [List] and the specified [List].
 */
fun List<Revision>.findCommon(other: List<Revision>): Set<RevId> {
    val thisIds = this.map { it.id }
    val otherIds = other.map { it.id }
    return thisIds intersect otherIds.toSet() // optimization: toSet()
}
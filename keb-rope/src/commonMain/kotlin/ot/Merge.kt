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

/// Find a set of revisions common to both lists
fun List<Revision>.findCommon(other: List<Revision>): Set<RevId> {
    val thisIds = this.map { it.id }
    val otherIds = other.map { it.id }
    TODO("intersection()")
}
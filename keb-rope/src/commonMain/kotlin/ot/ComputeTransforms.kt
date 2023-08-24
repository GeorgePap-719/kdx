package keb.ropes.ot

import keb.ropes.*


/// Computes a series of priorities and "transforms" for the deltas on the right
/// from the new revisions on the left.
///
/// Applies an optimization where it combines sequential revisions with the
/// same priority into one "transform" to decrease the number of transforms that
/// have to be considered in `rebase` substantially for normal editing
/// patterns. Any large runs of typing in the same place by the same user (e.g
/// typing a paragraph) will be combined into a single segment in a transform
/// as opposed to thousands of revisions.
fun computeTransforms(revisions: List<Revision>): MutableList<Pair<FullPriority, Subset>> {
    val list = buildList<Pair<FullPriority, Subset>> {
        var lastPriority: Int? = null
        for (revision in revisions) {
            when (val content = revision.edit) {
                is Edit -> {
                    if (content.inserts.isEmpty()) continue
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
    return list.toMutableList()
}
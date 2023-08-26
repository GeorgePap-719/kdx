package keb.ropes

// since undo and gc replay history with transforms, we need an empty set
// of the union string length *before* the first revision.
fun Engine.emptySubsetBeforeFirstRev(): Subset {
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
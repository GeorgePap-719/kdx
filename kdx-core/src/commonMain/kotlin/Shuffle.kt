package kdx

/// Move sections from text to tombstones and vice versa based on a new and old set of deletions.
/// Returns a tuple of a new text `Rope` and a new `Tombstones` rope described by `new_deletes_from_union`.
fun shuffle(
    text: Rope,
    tombstones: Rope,
    oldDeletesFromUnion: Subset,
    newDeletesFromUnion: Subset
): Pair<Rope, Rope> {
    // Delta that deletes the right bits from the text
    val deletesDelta = synthesize(tombstones.root, oldDeletesFromUnion, newDeletesFromUnion)
    val newText = deletesDelta.applyTo(text)
    val newTombstones = shuffleTombstones(text, tombstones, oldDeletesFromUnion, newDeletesFromUnion)
    return newText to newTombstones
}

/// Move sections from text to tombstones and out of tombstones based on a new and old set of deletions
internal fun shuffleTombstones(
    text: Rope,
    tombstones: Rope,
    oldDeletesFromUnion: Subset,
    newDeletesFromUnion: Subset
): Rope {
    // Taking the complement of deletes_from_union leads to an interleaving valid for swapped text and tombstones,
    // allowing us to use the same method to insert the text into the tombstones.
    val inverseTombstonesMap = oldDeletesFromUnion.complement()
    val moveDelta = synthesize(text.root, inverseTombstonesMap, newDeletesFromUnion.complement())
    return moveDelta.applyTo(tombstones)
}

internal fun DeltaRopeNode.applyTo(rope: Rope): Rope {
    val newRoot = applyTo(rope.root)
    return Rope(newRoot)
}
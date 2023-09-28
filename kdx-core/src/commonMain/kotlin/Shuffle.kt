package kdx

/**
 * Helper function for operations that compute the "new" `deletesFromUnion` to handle updating `text` and `tombstones`
 * to that new state. Returns the new `text` and `tombstones` as described by [toDeletesFromUnion].
 */
internal fun shuffle(
    text: Rope,
    tombstones: Rope,
    fromDeletesFromUnion: Subset,
    toDeletesFromUnion: Subset
): Pair<Rope, Rope> {
    // Delta that deletes the right bits from the text
    val deletesDelta = synthesize(tombstones.root, fromDeletesFromUnion, toDeletesFromUnion)
    val newText = deletesDelta.applyTo(text)
    val newTombstones = shuffleTombstones(text, tombstones, fromDeletesFromUnion, toDeletesFromUnion)
    return newText to newTombstones
}

/**
 * Creates new `tombstones` based on [toDeletesFromUnion] subsets with the given [text], [tombstones] and [fromDeletesFromUnion].
 * Technically this function moves sections from `text` to `tombstones` and out of `tombstones` based on a "from" and "to" set of deletions.
 */
internal fun shuffleTombstones(
    text: Rope,
    tombstones: Rope,
    fromDeletesFromUnion: Subset,
    toDeletesFromUnion: Subset
): Rope {
    // Taking the complement of `deletesFromUnion` leads to an interleaving valid for swapped `text` and `tombstones`,
    // allowing us to use the same method to insert the text into the tombstones.
    val inverseTombstonesMap = fromDeletesFromUnion.complement()
    val moveDelta = synthesize(text.root, inverseTombstonesMap, toDeletesFromUnion.complement())
    return moveDelta.applyTo(tombstones)
}

internal fun DeltaRopeNode.applyTo(rope: Rope): Rope {
    val newRoot = applyTo(rope.root)
    return Rope(newRoot)
}
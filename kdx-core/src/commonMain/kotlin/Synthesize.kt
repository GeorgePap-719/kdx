package kdx

import kotlin.math.min

/**
 * "Synthesize" a [Delta] from [tombstones][Rope] and two subsets of deletions;
 * one describing the "from" state and another the "to" state.
 * The [Delta] is relative from a [text][Rope] to a [text][Rope], not "union" to "union".
 * Also, anything in both subsets will be assumed to be missing.
 *
 * This can also be thought of as a set of insertions and one of deletions, with overlap doing nothing.
 * This function is the inverse of [factor].
 *
 * A typical use-case is to construct a [Delta] from current "text" to the targeted "text" at a specified revision.
 *
 * Note that since the zero segments of "deletesFromUnion" correspond to characters in "text" and the non-zero segments correspond to characters in "tombstones".
 * If we take the complement of "deletesFromUnion" we can use helpers designed for use with "tombstones" on "text" and vice-versa.
 * So we can use [synthesize] with the "text" and the complement of the old and new "deletesFromUnion"
 * to create the required [Delta] of the tombstones when it normally creates deltas of "text".
 *
 * @param tombstones the deleted characters from the current union-string.
 * @param fromDeletes the [Subset] of deletions which is based on [tombstones], relative to union-string.
 * @param toDeletes the [Subset] of deletions which is based on the target "text", relative to union-string.
 */
fun <T : NodeInfo> synthesize(
    tombstones: BTreeNode<T>,
    fromDeletes: Subset,
    toDeletes: Subset,
): Delta<T> {
    // We assume that `fromDeletes` are based on the `tombstones`.
    assert { tombstones.weight == fromDeletes.count(CountMatcher.NON_ZERO) }
    val changes = mutableListOf<DeltaElement<T>>()
    var offset = 0
    // 0-segments of `toDeletes` correspond to characters in from-text.
    val fromTextIterator = fromDeletes.complementIterator()
    var fromRange = fromTextIterator.next()
    val tombstonesMapper = fromDeletes.mapper(CountMatcher.NON_ZERO)
    // 0-segments of `toDeletes` correspond to characters in to-text.
    val toTextIterator = toDeletes.complementIterator()
    // Try to move both text iterators with the same step.
    // The procedure expects (and handles) the `fromTextIterator`
    // to move forward faster, as typically contains more 1-segments.
    // In case, `fromTextIterator` finishes first (fromRange == null)
    // then we insert all remaining ranges.
    // The logic is to reverse the deleted `tombstones`
    // and fill them into `toTextIterator`.
    for (toRange in toTextIterator) {
        // Note that even though the iterators loops over 0-segments,
        // the provided ranges (prevLen, curLen) are counting
        // the actual length of the subset.
        val (toPrevLen, toCurLen) = toRange
        // No more 0-segments to iterate.
            ?: break
        // `startIndex` marks the start of the delta element.
        // It is updated until we fill the "slice".
        var startIndex = toPrevLen
        while (startIndex < toCurLen) {
            // Move forward slices in `fromRange` until one overlaps with `toRange`.
            // In this operation, the `fromTextIterator` is the leader
            // as we base all the differences against him.
            // That's why we try to move forward the `fromTextIterator` first.
            while (fromRange != null) {
                if (fromRange.curLen > startIndex) break
                offset += fromRange.step
                fromRange = fromTextIterator.next()
            }
            // If we have a slice in the `fromRange`
            // with the character at `startIndex`, then we copy.
            // Practically, this checks if the above loop moved more than one step.
            if (fromRange != null && fromRange.prevLen <= startIndex) {
                val (fromPrevLen, fromCurLen) = fromRange
                // Take `endIndex` but do not exceed slice length.
                // Also, if `fromCurLen` is a shorter segment,
                // we update `endIndex` with that as a step.
                // This indicates the next step should be an insertion.
                val endIndex = min(toCurLen, fromCurLen)
                // Try to merge contiguous copies in the output.
                val startOffset = startIndex + offset - fromPrevLen
                val endOffset = endIndex + offset - fromPrevLen
                val lastElement = changes.lastOrNull()
                if (lastElement is Copy && lastElement.endIndex == startOffset) {
                    changes.replace(Copy(lastElement.startIndex, endOffset), lastElement)
                } else {
                    changes.add(Copy(startOffset, endOffset))
                }
                startIndex = endIndex
            } else {
                // If the character at `startIndex` isn't in the `fromRange`, then we insert.
                // Insert up until the next old `toRange` we could copy from,
                // or the end of this segment.
                var endIndex = toCurLen
                if (fromRange != null) endIndex = min(endIndex, fromRange.prevLen)
                // Use the mapper to insert the corresponding section of the "tombstones" rope.
                val tombstonesRange =
                    tombstonesMapper.documentIndexToSubset(startIndex)..tombstonesMapper.documentIndexToSubset(endIndex)
                val node = tombstones.subSequence(tombstonesRange)
                changes.add(Insert(node))
                startIndex = endIndex
            }
        }
    }
    val baseLen = fromDeletes.lengthAfterDelete()
    return DeltaSupport(changes, baseLen)
}

// Not clear-cut if we should have a non-existent item here.
private fun <T> MutableList<T>.replace(new: T, old: T) {
    val index = indexOf(old)
    assert { index != -1 }
    if (index == -1) return
    removeAt(index)
    add(index, new)
}
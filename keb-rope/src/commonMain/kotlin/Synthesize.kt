package keb.ropes

import kotlin.math.min

/// Synthesize a delta from a "union string" and two subsets: an old set
/// of deletions and a new set of deletions from the union. The Delta is
/// from text to text, not union to union; anything in both subsets will
/// be assumed to be missing from the Delta base and the new text. You can
/// also think of these as a set of insertions and one of deletions, with
/// overlap doing nothing. This is basically the inverse of `factor`.
///
/// Since only the deleted portions of the union string are necessary,
/// instead of requiring a union string the function takes a `tombstones`
/// rope which contains the deleted portions of the union string.
//
// /// Notes: this assumption could be an assertion?
/// The `from_dels` subset must be the interleaving of `tombstones` into the
/// union string.
///
/// ```no_run
/// # use xi_rope::rope::{Rope, RopeInfo};
/// # use xi_rope::delta::Delta;
/// # use std::str::FromStr;
/// fn test_synthesize(d : &Delta<RopeInfo>, r : &Rope) {
///     let (ins_d, del) = d.clone().factor();
///     let ins = ins_d.inserted_subset();
///     let del2 = del.transform_expand(&ins);
///     let r2 = ins_d.apply(&r);
///     let tombstones = ins.complement().delete_from(&r2);
///     let d2 = Delta::synthesize(&tombstones, &ins, &del);
///     assert_eq!(String::from(d2.apply(r)), String::from(d.apply(r)));
/// }
/// ```
// For if last_old.is_some() && last_old.unwrap().0 <= beg
//TODO: research the usage of this fun.
// Notes: the input of the function does not always represent "deletes".
fun <T : NodeInfo> synthesize(
    tombstones: BTreeNode<T>,
    /* The subset which tombstones are based on. */
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
            // Move forward slices in `fromRange`
            // until one overlaps where we want to fill.
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
                // This indicates the next (remaining slice) step should be an insertion.
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
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
fun <T : NodeInfo> synthesize(
    // union-string -> "a", "b", "c", "d", "e,
    tombstones: BTreeNode<T>, // "a", "b", "c" -> deleted characters.
    fromDeletes: Subset, // Subset[Segment(1,1),Segment(1,0),Segment(1,1)]
    toDeletes: Subset // toDeletesFromUnion
): Delta<T> {
    val baseLen = fromDeletes.lengthAfterDelete()
    val changes = mutableListOf<DeltaElement<T>>()
    var x = 0
    val oldRanges = fromDeletes.complementIterator()
    var curOld = oldRanges.next()
    val mapper = fromDeletes.mapper(CountMatcher.NON_ZERO)
    val toDelsIterator = toDeletes.complementIterator()
    // For each segment of the new text.
    while (toDelsIterator.hasNext()) {
        val (prevLen, curLen) = toDelsIterator.next() ?: break
        // Fill the whole segment.
        var begin = prevLen
        while (begin < curLen) {
            // Skip over ranges in old text
            // until one overlaps where we want to fill.
            while (curOld != null) {
                val (oldPrevLen, oldCurLen) = curOld
                if (oldCurLen > begin) break
                x += oldCurLen - oldPrevLen // step
                curOld = oldRanges.next()
            }
            // If we have a range in the old text
            // with the character at beg,
            // then we Copy.
            if (curOld != null && curOld.prevLen <= begin) {
                val (oldPrevLen, oldCurLen) = curOld
                val end = min(curLen, oldCurLen)
                // Try to merge contiguous copies in the output.
                val xbeg = begin + x - oldPrevLen // "beg - oldPrevLen + x" better for overflow?
                val xend = end + x - oldPrevLen // ditto
                val lastElement = changes.lastOrNull()
                val merged = if (lastElement is Copy && lastElement.endIndex == xbeg) {
                    changes.replace(Copy(lastElement.startIndex, xend), lastElement)
                    true
                } else {
                    false
                }
                if (!merged) changes.add(Copy(xbeg, xend))
                begin = end
            } else {
                // If the character at `beg` isn't in the old text,
                // then we insert.
                // Insert up until the next old range we could copy from,
                // or the end of this segment.
                var end = curLen
                if (curOld != null) end = min(end, curOld.prevLen)
                // Note: could try to aggregate insertions,
                // but not sure of the win.
                // Use the mapper to insert the corresponding section of the tombstones rope.
                val range = mapper.documentIndexToSubset(begin)..mapper.documentIndexToSubset(end)
                val node = tombstones.subSequence(range)
                changes.add(Insert(node))
                begin = end
            }
        }
    }
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
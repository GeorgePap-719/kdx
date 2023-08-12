package keb.ropes

fun <T : NodeInfo> simpleEdit(
    range: IntRange,
    node: BTreeNode<T>,
    baseLen: Int
): Delta<T> = buildDelta(baseLen) {
    if (node.isEmpty) {
        delete(range)
    } else {
        replace(range, node)
    }
}

/// Synthesize a delta from a "union string" and two subsets: an old set
/// of deletions and a new set of deletions from the union. The Delta is
/// from text to text, not union to union; anything in both subsets will
/// be assumed to be missing from the Delta base and the new text. You can
/// also think of these as a set of insertions and one of deletions, with
/// overlap doing nothing. This is basically the inverse of `factor`.
///
/// Since only the deleted portions of the union string are necessary,
/// instead of requiring a union string the function takes a `tombstones`
/// rope which contains the deleted portions of the union string. The
/// `from_dels` subset must be the interleaving of `tombstones` into the
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
// For if last_old.is_some() && last_old.unwrap().0 <= beg {. Clippy complaints
// about not using if-let, but that'd change the meaning of the conditional.
//TODO: research the usage of this fun.
fun <T : NodeInfo> synthesize(
    tombstones: BTreeNode<T>,
    fromDeletes: Subset,
    toDeletes: Subset
): Delta<T> {
    val baseLen = fromDeletes.lengthAfterDelete()
    val changes = mutableListOf<DeltaElement<T>>()
    var x = 0
    val oldRanges = fromDeletes.complementIterator()
    val lastOld = oldRanges.next()
    val mapper = fromDeletes.mapper(CountMatcher.ZERO)

    val toDelsIterator = toDeletes.complementIterator()
    // For each segment of the new text.
    while (toDelsIterator.hasNext()) {
        val (b, e) = toDelsIterator.next() ?: continue
        // Fill the whole segment.
        var beg = b
        while (beg < e) {
            // im starting to write oti nane..
            //
            TODO()
        }
    }
}
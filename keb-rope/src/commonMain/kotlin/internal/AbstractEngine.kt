package keb.ropes.internal

//internal abstract class AbstractEngine : MutableEngine {
//    override fun rebase(
//        expandBy: MutableList<Pair<FullPriority, Subset>>,
//        ops: List<DeltaOp>,
//        maxUndoSoFar: Int
//    ): Rebaseable {
//        val revisions: MutableList<Revision> = ArrayList(ops.size)
//        val nextExpandBy: MutableList<Pair<FullPriority, Subset>> = ArrayList(expandBy.size)
//        for (op in ops) {
//            var inserts: InsertDelta<RopeLeaf>? = null
//            var deletes: Subset? = null
//            val fullPriority = FullPriority(op.priority, op.id.sessionId)
//            for ((transformPriority, transformInserts) in expandBy) {
//                // Should never be ==
//                assert { fullPriority.compareTo(transformPriority) != 0 }
//                val after = fullPriority >= transformPriority
//                // d-expand by other
//                inserts = op.inserts.transformExpand(transformInserts, after)
//                // trans-expand other by expanded so they have the same context
//                val inserted = inserts.getInsertedSubset()
//                val newTransformInserts = transformInserts.transformExpand(inserted)
//                // The `deletes` are already after our inserts,
//                // but we need to include the other inserts.
//                deletes = op.deletes.transformExpand(newTransformInserts)
//                // On the next step,
//                // we want things in `expandBy`
//                // to have `op` in the context.
//                nextExpandBy.add(transformPriority to newTransformInserts)
//            }
//            check(inserts != null)
//            check(deletes != null)
//            val textInserts = inserts.transformShrink(deletesFromUnion)
//            val textWithInserts = textInserts.applyTo(text)
//            val inserted = inserts.getInsertedSubset()
//
//            val expandedDeletesFromUnion = deletesFromUnion.transformExpand(inserted)
//            val newDeletesFromUnion = expandedDeletesFromUnion.union(deletes)
//            val (newText, newTombstones) = shuffle(
//                textWithInserts,
//                tombstones,
//                expandedDeletesFromUnion,
//                newDeletesFromUnion
//            )
//        }
//    }
//}
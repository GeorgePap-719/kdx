package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class SynthesizeTest {
    @Test
    fun testSynthesize() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        // delta: "herald"
        // Break down delta into parts.
        val (inserts, deletes) = delta.factor()
        // Subset([Segment(length=1, count=0), Segment(length=3, count=1), Segment(length=10, count=0)])
        val insertedSubset = inserts.getInsertedSubset()
        println("insertedSubset:$insertedSubset")
        val deletesTransform = deletes.transformExpand(insertedSubset)
        val union = inserts.applyToString("hello world")
        println("unionString:$union")
        // Applying complement() on a subset which represents the insertions,
        // and then applying that subset will yield the inserted characters.
        val tombstones = insertedSubset.complement().deleteFromString(union.toString())
        // tombstones:era
        println("tombstones:${tombstones}")
        println(deletesTransform)
        // Reconstruct delta.
        val newDelta = synthesize(
            tombstones = Rope(tombstones).root,
            fromDeletes = insertedSubset,
            toDeletes = deletesTransform
        )
        assertEquals("herald", newDelta.applyToString("hello world").toString())
        // Applying complement() on a subset which represents the "deletions",
        // and then applying that subset will yield the deleted characters.
        val newDeltaTombstones = deletesTransform.complement().deleteFromString(union.toString())
        // newDeltaTombstones:ello wor
        println("newDeltaTombstones:$newDeltaTombstones")
        println(insertedSubset)
        // Inverse edit.
        val inverseDelta = synthesize(
            tombstones = Rope(newDeltaTombstones).root,
            fromDeletes = deletesTransform,
            toDeletes = insertedSubset
        )
        assertEquals("hello world", inverseDelta.applyToString("herald").toString())
    }
}
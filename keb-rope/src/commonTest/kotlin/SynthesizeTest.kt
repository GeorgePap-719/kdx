package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class SynthesizeTest {
    @Test
    fun testSynthesize() {
        val str = "hello world"
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        // delta: "herald"
        // Break down delta into parts.
        val (inserts, deletes) = delta.factor()
        // Subset([Segment(length=1, count=0), Segment(length=3, count=1), Segment(length=10, count=0)])
        val insertedSubset = inserts.getInsertedSubset()
        println("insertedSubset:$insertedSubset")
        val deletesTransform = deletes.transformExpand(insertedSubset)
        val union = inserts.applyToString(str)
        println("unionString:$union")
        // Applying complement() on a subset which represents the insertions,
        // and then applying that subset will yield the inserted characters.
        val tombstones = insertedSubset.complement().deleteFromString(union.toString())
        println("tombstones:${tombstones}")
        // Reconstruct delta.
        val newDelta = synthesize(
            tombstones = Rope(tombstones).root,
            fromDeletes = insertedSubset,
            toDeletes = deletesTransform
        )
        assertEquals("herald", newDelta.applyToString(str).toString())
        // Inverse edit.
        val newDeltaTombstones = deletesTransform.complement().deleteFromString(union.toString())
        println("text:$newDeltaTombstones")
        val inverseDelta = synthesize(
            tombstones = Rope(newDeltaTombstones).root,
            fromDeletes = deletesTransform,
            toDeletes = insertedSubset
        )
        assertEquals(str, inverseDelta.applyToString("herald").toString())
    }
}
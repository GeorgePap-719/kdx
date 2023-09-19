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
        val insertedSubset = inserts.getInsertedSubset()
        val deletesTransform = deletes.transformExpand(insertedSubset)
        val unionString = inserts.applyToString(str)
        println("unionString:$unionString")
        val tombstones = insertedSubset.complement().deleteFromString(unionString.toString())
        println("tombstones:${tombstones}")
        // Reconstruct delta.
        val newDelta = synthesize(
            tombstones = Rope(tombstones).root,
            fromDeletes = insertedSubset,
            toDeletes = deletesTransform
        )
        assertEquals("herald", newDelta.applyToString(str).toString())
        // Inverse edit.
        val text = deletesTransform.complement().deleteFromString(unionString.toString())
        val inverseDelta = synthesize(
            tombstones = Rope(text).root,
            fromDeletes = deletesTransform,
            toDeletes = insertedSubset
        )
        assertEquals(str, inverseDelta.applyToString("herald").toString())
    }
}
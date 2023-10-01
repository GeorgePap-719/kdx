package kdx

import kotlin.test.Test
import kotlin.test.assertEquals

class SynthesizeTest {

    @Test
    fun testSynthesizeBasic() {
        // union-string: "hello world 1"
        val text = "hello"
        val tombstones = Rope(" world 1")
        val deletesFromUnion = Subset(listOf(Segment(5, 0), Segment(8, 1)))
        val oldDeletesFromUnion = Subset(listOf(Segment(11, 0), Segment(2, 1)))
        val oldDelta = synthesize(
            tombstones = tombstones,
            fromDeletes = deletesFromUnion,
            toDeletes = oldDeletesFromUnion
        )
        assertEquals("hello world", oldDelta.applyToString(text).toString())
    }

    @Test
    fun testSynthesize() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, deletes) = delta.factor()
        val insertedSubset = inserts.getInsertedSubset()
        val deletesTransform = deletes.transformExpand(insertedSubset)
        val union = inserts.applyToString("hello world")
        // Applying complement() on a subset which represents the insertions,
        // and then applying that subset will yield the inserted characters.
        val tombstones = insertedSubset.complement().deleteFromString(union.toString())
        val newDelta = synthesize(
            tombstones = Rope(tombstones),
            fromDeletes = insertedSubset,
            toDeletes = deletesTransform
        )
        assertEquals("herald", newDelta.applyToString("hello world").toString())
        // Applying complement() on a subset which represents the "deletions",
        // and then applying that subset will yield the deleted characters.
        val newDeltaTombstones = deletesTransform.complement().deleteFromString(union.toString())
        val inverseDelta = synthesize(
            tombstones = Rope(newDeltaTombstones),
            fromDeletes = deletesTransform,
            toDeletes = insertedSubset
        )
        assertEquals("hello world", inverseDelta.applyToString("herald").toString())
    }
}
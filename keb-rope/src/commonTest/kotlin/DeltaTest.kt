package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class DeltaTest {

    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test
    fun testSimpleEdit() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val str = delta.applyToString("hello world")
        assertEquals("herald", str.toString())
        assertEquals(6, str.length)
    }

    @Test
    fun testFactor() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, deletes) = delta.factor()
        assertEquals("heraello world", inserts.applyToString("hello world").toString())
        assertEquals("hld", deletes.deleteFromString("hello world"))
    }

    @Test
    fun testFactorCanYieldSameResult() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, deletes) = delta.factor()
        val transform = deletes.transformExpand(inserts.getInsertedSubset())
        assertEquals("herald", transform.deleteFromString("heraello world"))
    }

    @Test
    fun testGetInsertedSubset() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, _) = delta.factor()
        assertEquals("hello world", inserts.getInsertedSubset().deleteFromString("heraello world"))
    }

    @Test
    fun testSynthesize() {
        val str = "hello world"
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        // delta: "herald"
        val (inserts, deletes) = delta.factor()
        val insertedSubset = inserts.getInsertedSubset()
        val deletesTransform = deletes.transformExpand(insertedSubset)
        val unionString = inserts.applyToString(str)
        val tombstones = insertedSubset.complement().deleteFromString(unionString.toString())
        println("tombstones:${tombstones}")
        val newDelta = synthesize(
            tombstones = Rope(tombstones).root,
            toDeletes = insertedSubset,
            fromDeletes = deletes
        )
        assertEquals("herald", newDelta.applyToString(str).toString())
    }
}

private fun DeltaRopeNode.applyToString(input: String): Rope {
    return applyTo(Rope(input))
}
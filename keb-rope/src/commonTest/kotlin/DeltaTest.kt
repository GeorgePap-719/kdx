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
}

fun DeltaRopeNode.applyToString(input: String): Rope {
    return applyTo(Rope(input))
}
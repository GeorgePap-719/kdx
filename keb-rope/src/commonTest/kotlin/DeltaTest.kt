package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class DeltaTest {

    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test
    fun testSimpleEdit() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        println(delta)
        val str = delta.applyToString("hello world")
        assertEquals("herald", str.toString())
        assertEquals(6, str.length)
    }

    @Test
    fun testFactor() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (d1, ss) = delta.factor()
        assertEquals("heraello world", d1.applyToString("hello world").toString())
    }
}

private fun DeltaRopeNode.applyToString(input: String): Rope {
    return applyTo(Rope(input))
}
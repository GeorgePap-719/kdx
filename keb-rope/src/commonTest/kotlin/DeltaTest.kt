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
    }
}

private fun DeltaRopeNode.applyToString(input: String): Rope {
    return applyTo(Rope(input))
}
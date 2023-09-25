package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineTest {
    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test
    // without individual tests,
    // this becomes too complex to handle.
    fun testSimpleEditRevision() {
        val engine = MutableEngine(Rope(simpleString))
        val firstRev = engine.headRevisionId.token()
        val tryEditRevision = engine.tryEditRevision(0, 1, firstRev, buildDelta1())
        println(tryEditRevision)
        engine.editRevision(0, 1, firstRev, buildDelta1())
        val headAsString = engine.head.toString()
        println(headAsString)
        assertEquals("0123456789abcDEEFghijklmnopqr999stuvz", headAsString)
    }

    private fun buildDelta1(): DeltaRopeNode = buildDelta(simpleString.length) {
        delete(10..<36)
        replace(39..<42, Rope("DEEF"))
        replace(54..<54, Rope("999"))
        delete(58..<61)
    }

    private fun buildDelta2(): DeltaRopeNode = buildDelta(simpleString.length) {
        replace(1..<3, Rope("!"))
        delete(10..<36)
        replace(42..<45, Rope("GI"))
        replace(54..54, Rope("888"))
        replace(59..<60, Rope("HI"))
    }
}
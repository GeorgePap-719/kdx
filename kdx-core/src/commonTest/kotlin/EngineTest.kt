package kdx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTest {
    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test
    fun testSimpleEditRevision() {
        val engine = MutableEngine(Rope(simpleString))
        val firstRevToken = engine.headRevisionId.token()
        engine.editRevision(0, 1, firstRevToken, buildDelta1())
        assertEquals("0123456789abcDEEFghijklmnopqr999stuvz", engine.head.toString())
    }

    @Test
    fun testEditRevisionEmptyDelta() {
        val engine = MutableEngine(Rope(simpleString))
        val firstRevToken = engine.headRevisionId.token()
        val emptyDelta = buildDelta<RopeLeaf>(simpleString.length) {}
        engine.editRevision(0, 1, firstRevToken, emptyDelta)
        assertEquals(simpleString, engine.head.toString())
        engine.editRevision(0, 1, firstRevToken, emptyDelta)
        assertEquals(simpleString, engine.head.toString())
    }

    @Test
    fun testEditRevisionConcurrent() {
        val engine = MutableEngine(Rope(simpleString))
        val firstRevToken = engine.headRevisionId.token()
        engine.editRevision(1, 1, firstRevToken, buildDelta1())
        engine.editRevision(0, 2, firstRevToken, buildDelta2())
        assertEquals("0!3456789abcDEEFGIjklmnopqr888999stuvHIz", engine.head.toString())
    }

    @Test
    fun testEditRevisionThrowsForIllegalDeltaLen() {
        val str = "hello"
        val engine = MutableEngine(Rope(str))
        val range = 1..<1
        val delta1 = buildDelta(str.length) {
            replace(range, Rope("1"))
        }
        val delta2 = buildDelta(str.length) {
            replace(range, Rope("2"))
        }
        var revisionToken = engine.headRevisionId.token()
        engine.editRevision(1, 1, revisionToken, delta1)
        // This second delta now has an incorrect length for the engine.
        revisionToken = engine.headRevisionId.token()
        val result = engine.tryEditRevision(1, 2, revisionToken, delta2)
        assertTrue(result.errorOrNull() is EngineResult.MalformedDelta)
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
        replace(54..<54, Rope("888"))
        replace(59..<60, Rope("HI"))
    }
}
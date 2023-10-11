package kdx

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This test-suite for [MutableEngine.merge] has a simple framework that allows to test complex scenarios while
 * keeping the code readable.
 */
class EngineMergeScenarioTest {

    /*
     * This scenario was tested in a whiteboard by hand from the xi's author.
     */
    @Test
    fun testMergeInsertOnlyWB() = testEngineMerge(3) {
        edit(2, 11, 1, parseDelta("ab"))
        merge(0, 2)
        merge(1, 2)
        assert("ab", 0)
        assert("ab", 1)
        assert("ab", 2)
        edit(0, 3, 1, parseDelta("-c-"))
        edit(0, 3, 1, parseDelta("---d"))
        assert("acbd", 0)
        //TODO: rest...
    }

    private fun parseDelta(input: String): DeltaRopeNode {
        TODO()
    }

    private fun testEngineMerge(numberOfPeers: Int, scenario: ScenarioDsl.() -> Unit) {
        val peers = createPeers(numberOfPeers)
        val dsl = ScenarioDsl(peers)
        dsl.scenario()
    }

    private fun createPeers(number: Int): List<MutableEngine> = buildList(number) {
        for (i in 0..<number) {
            val engine = emptyMutableEngine()
            engine.setSessionId(i * 1000L to 0)
            add(engine)
        }
    }

    private class ScenarioDsl(private val peers: List<MutableEngine>) {

        fun merge(leftPeerIndex: Int, rightPeerIndex: Int) {
            peers[leftPeerIndex].merge(peers[rightPeerIndex])
        }

        fun assert(expect: String, peerIndex: Int) {
            val engine = peers[peerIndex]
            assertEquals(expect, engine.head.toString(), "for peer:$peerIndex")
        }

        fun assertMaxUndoSoFar(expect: Int, peerIndex: Int) {
            val engine = peers[peerIndex]
            assertEquals(expect, engine.maxUndoGroupId, "for peer:$peerIndex")
        }

        fun assertAll(expect: String) {
            for ((index, engine) in peers.withIndex()) {
                assertEquals(expect, engine.head.toString(), "for peer:$index")
            }
        }

        fun edit(
            peerIndex: Int,
            priority: Int,
            undoGroup: Int,
            delta: DeltaRopeNode
        ) {
            val engine = peers[peerIndex]
            val token = engine.headRevisionId.token()
            engine.editRevision(priority, undoGroup, token, delta)
        }
    }
}
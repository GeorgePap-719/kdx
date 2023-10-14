package kdx

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This test-suite for [MutableEngine.merge] has a simple framework that allows to test complex scenarios while
 * keeping the code readable.
 */
class EngineMergeScenarioTest {

    @Test
    fun testMergeInsertOnlyWb() = testEngineMerge(3) {
        edit(2, 1, 1, parseDelta("ab"))
        merge(0, 2)
        merge(1, 2)
        assert("ab", 0)
        assert("ab", 1)
        assert("ab", 2)
        edit(0, 3, 1, parseDelta("-c-"))
        edit(0, 3, 1, parseDelta("---d"))
        assert("acbd", 0)
        edit(1, 5, 1, parseDelta("-p-"))
        edit(1, 5, 1, parseDelta("---j"))
        assert("apbj", 1)
        edit(2, 1, 1, parseDelta("z--"))
        merge(0, 2)
        merge(1, 2)
        assert("zacbd", 0)
        assert("zapbj", 1)
        merge(0, 1)
        assert("zacpbdj", 0)
    }

    @Test
    fun testMergePrioritiesWhichBreakTiesCorrect() = testEngineMerge(3) {
        edit(2, 1, 1, parseDelta("ab"))
        merge(0, 2)
        merge(1, 2)
        assert("ab", 0)
        assert("ab", 1)
        assert("ab", 2)
        edit(0, 3, 1, parseDelta("-c-"))
        edit(0, 3, 1, parseDelta("---d"))
        assert("acbd", 0)
        edit(1, 5, 1, parseDelta("-p-"))
        assert("apb", 1)
        edit(2, 4, 1, parseDelta("-r-"))
        merge(0, 2)
        merge(1, 2)
        assert("acrbd", 0)
        assert("arpb", 1)
        edit(1, 5, 1, parseDelta("----j"))
        assert("arpbj", 1)
        edit(2, 4, 1, parseDelta("---z"))
        merge(0, 2)
        merge(1, 2)
        assert("acrbdz", 0)
        assert("arpbzj", 1)
        merge(0, 1)
        assert("acrpbdzj", 0)
    }

    /**
     * Tests that merging again when there are no new revisions does nothing.
     */
    @Test
    fun testMergeIsIdempotent() = testEngineMerge(3) {
        edit(2, 1, 1, parseDelta("ab"))
        merge(0, 2)
        merge(1, 2)
        assert("ab", 0)
        assert("ab", 1)
        assert("ab", 2)
        edit(0, 3, 1, parseDelta("-c-"))
        edit(0, 3, 1, parseDelta("---d"))
        assert("acbd", 0)
        edit(1, 5, 1, parseDelta("-p-"))
        edit(1, 5, 1, parseDelta("---j"))
        merge(0, 1)
        assert("acpbdj", 0)
        merge(0, 1)
        merge(1, 0)
        merge(0, 1)
        merge(1, 0)
        assert("acpbdj", 0)
        assert("acpbdj", 1)
    }

    @Test
    fun testMergeIsAssociative() = testEngineMerge(6) {
        edit(2, 1, 1, parseDelta("ab"))
        merge(0, 2)
        merge(1, 2)
        edit(0, 3, 1, parseDelta("-c-"))
        edit(1, 5, 1, parseDelta("-p-"))
        edit(2, 2, 1, parseDelta("z--"))
        // Copy the current state.
        merge(3, 0)
        merge(4, 1)
        merge(5, 2)
        // Do the merge one direction.
        merge(1, 2)
        merge(0, 1)
        assert("zacpb", 0)
        merge(4, 3)
        merge(5, 4)
        assert("zacpb", 5)
        merge(0, 5)
        merge(2, 5)
        merge(4, 5)
        merge(1, 4)
        merge(3, 1)
        merge(5, 3)
        assertAll("zacpb")
    }

    @Test
    fun testMergeSimpleDelete1() = testEngineMerge(2) {
        edit(0, 1, 1, parseDelta("abc"))
        merge(1, 0)
        assert("abc", 0)
        assert("abc", 1)
        edit(0, 1, 1, parseDelta("!-d-"))
        assert("bdc", 0)
        edit(1, 3, 1, parseDelta("--efg!"))
        assert("abefg", 1)
        merge(1, 0)
        assert("bdefg", 1)
    }

    @Test
    fun testMergeSimpleDelete2() = testEngineMerge(2) {
        edit(0, 1, 1, parseDelta("ab"))
        merge(1, 0)
        assert("ab", 0)
        assert("ab", 1)
        edit(0, 1, 1, parseDelta("!-"))
        assert("b", 0)
        edit(1, 3, 1, parseDelta("-c-"))
        assert("acb", 1)
        merge(1, 0)
        assert("cb", 1)
    }

    @Test
    fun testMergeWb() = testEngineMerge(4) {
        edit(2, 1, 1, parseDelta("ab"))
        merge(0, 2)
        merge(1, 2)
        merge(3, 2)
        assertAll("ab")
        edit(2, 1, 1, parseDelta("!-"))
        assert("b", 2)
        edit(0, 3, 1, parseDelta("-c-"))
        edit(0, 3, 1, parseDelta("---d"))
        assert("acbd", 0)
        merge(0, 2)
        assert("cbd", 0)
        edit(1, 5, 1, parseDelta("-p-"))
        merge(1, 2)
        assert("pb", 1)
        edit(1, 5, 1, parseDelta("--j"))
        assert("pbj", 1)
        edit(3, 7, 1, parseDelta("z--"))
        merge(2, 3)
        merge(0, 2)
        merge(1, 2)
        assert("zcbd", 0)
        assert("zpbj", 1)
        merge(0, 1)
        assert("zcpbdj", 0)
    }

    @Test
    fun testMergeMaxUndoSoFar() = testEngineMerge(3) {
        edit(0, 1, 1, parseDelta("ab"))
        merge(1, 0)
        merge(2, 0)
        assertMaxUndoSoFar(1, 1)
        edit(0, 1, 2, parseDelta("!-"))
        edit(1, 3, 3, parseDelta("-!"))
        merge(1, 0)
        assertMaxUndoSoFar(3, 1)
        assertMaxUndoSoFar(2, 0)
        merge(0, 1)
        assertMaxUndoSoFar(3, 0)
        edit(2, 1, 1, parseDelta("!!"))
        merge(1, 2)
        assertMaxUndoSoFar(3, 1)
    }

    /**
     * This is a regression test to ensure session Ids are used to break ties in edit properties.
     * Otherwise, the results may be inconsistent.
     */
    @Test
    fun testMergeSessionPriorities() = testEngineMerge(3) {
        edit(0, 1, 1, parseDelta("ac"))
        merge(1, 0)
        merge(2, 0)
        assertAll("ac")
        edit(0, 1, 1, parseDelta("-d-"))
        assert("adc", 0)
        edit(1, 1, 1, parseDelta("-f-"))
        merge(2, 1)
        assert("afc", 1)
        assert("afc", 2)
        merge(2, 0)
        merge(0, 1)
        // These two will be different without using session IDs.
        assert("adfc", 2)
        assert("adfc", 0)
    }

    private fun parseDelta(input: String): DeltaRopeNode {
        val baseLen = input.filter { it == '-' || it == '!' }.length
        return buildDelta(baseLen) {
            var i = 0
            for (char in input) {
                when (char) {
                    '-' -> i++
                    '!' -> {
                        delete(i..<i + 1)
                        i++
                    }

                    else -> replace(i..<i, Rope(char.toString()))
                }
            }
        }
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
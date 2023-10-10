package kdx

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineMergeTest {

    @Test
    fun testBasicRearrange() {
        val subsetList = """
            ##
            -#-
            #---
            ---#-
            -----#
            #------
        """.trimIndent()
        val inserts = parseSubsetList(subsetList)
        val revisions = basicInsertOps(inserts, 1)
        val base = setOf(3, 5).map(::basicRevision).toSet()
        val rearranged = rearrange(revisions, base, 7)
        val rearrangedInserts = rearranged.map {
            when (val content = it.edit) {
                is Edit -> content.inserts
                is Undo -> error("not supported")
            }
        }
        debugSubsets(rearrangedInserts)
        val correctSubset = """
            -##-
            --#--
            ---#--
            #------
        """.trimIndent()
        val correct = parseSubsetList(correctSubset)
        assertEquals(correct, rearrangedInserts)
    }

    @Test
    fun testFindCommonRevisions() {
        val ids1 = idsToFakeRevisions(0, 2, 4, 6, 8, 10, 12)
        val ids2 = idsToFakeRevisions(0, 1, 2, 4, 5, 8, 9)
        val revisions = ids1.findCommonRevisions(ids2)
        val expected = setOf(0, 2, 4, 8).map(::basicRevision).toSet()
        assertEquals(expected, revisions)
    }

    @Test
    fun testFindBaseIndex() {
        val ids1 = idsToFakeRevisions(0, 2, 4, 6, 8, 10, 12)
        val ids2 = idsToFakeRevisions(0, 1, 2, 4, 5, 8, 9)
        val revisions = ids1.findBaseIndex(ids2)
        assertEquals(1, revisions)
    }

    private fun idsToFakeRevisions(vararg ids: Int): List<Revision> {
        val contents = Edit(
            priority = 0,
            undoGroup = 0,
            inserts = emptySubset(),
            deletes = emptySubset()
        )
        return ids.map { Revision(basicRevision(it), 1, contents) }
    }

    @Test
    fun testComputeDeltas() {
        val insertList = """
            -##-
            --#--
            ---#--
            #------
        """.trimIndent()
        val inserts = parseSubsetList(insertList)
        val revision = basicInsertOps(inserts, 1)
        val text = Rope("13456")
        val tombstones = Rope("27")
        val deletesFromUnion = parseSubset("-#----#")
        val deltas = computeDeltas(revision, text, tombstones, deletesFromUnion)
        var r = Rope("27")
        for (op in deltas) r = op.inserts.applyTo(r)
        assertEquals("1234567", r.toString())
    }

    private fun basicInsertOps(inserts: List<Subset>, priority: Int): List<Revision> {
        return inserts.mapIndexed { index, it ->
            val deletes = Subset(it.length())
            Revision(
                id = basicRevision(index + 1),
                maxUndoSoFar = index + 1,
                edit = Edit(priority, index + 1, it, deletes)
            )
        }
    }

    private fun basicRevision(index: Int): RevisionId = RevisionId(1, 0, index)

    private fun parseSubsetList(input: String): List<Subset> = input
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { parseSubset(it) }

    private fun parseSubset(input: String): Subset = buildSubset {
        for (char in input) {
            when (char) {
                '#' -> add(1, 1)
                'e' -> {} //  do nothing, used for empty subsets
                else -> add(1, 0)
            }
        }
    }

    private fun debugSubsets(input: List<Subset>) {
        if (DEBUG_ENABLED) for (subset in input) println(subset)
    }
}

private const val DEBUG_ENABLED = false
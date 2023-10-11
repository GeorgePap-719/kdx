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

    @Test
    fun testComputeTransforms1() {
        val insertList = """
            -##-
            --#--
            ---#--
            #------
        """.trimIndent()
        val inserts = parseSubsetList(insertList)
        val revisions = basicInsertOps(inserts, 1)
        val expandBy = computeTransforms(revisions)
        assertEquals(1, expandBy.size)
        assertEquals(1, expandBy.first().first.priority)
        assertEquals(parseSubset("#-####-"), expandBy.first().second)
    }

    @Test
    fun testComputeTransforms2() {
        val insertsList1 = """
            -##-
            --#--
        """.trimIndent()
        val revisions1 = basicInsertOps(parseSubsetList(insertsList1), 1).toMutableList()
        val insertsList2 = """
             ----
        """.trimIndent()
        val revisions2 = basicInsertOps(parseSubsetList(insertsList2), 4).toMutableList()
        revisions1.addAll(revisions2)
        val insertsList3 = """
             ---#--
             #------
        """.trimIndent()
        val revisions3 = basicInsertOps(parseSubsetList(insertsList3), 2)
        revisions1.addAll(revisions3)
        val expandBy = computeTransforms(revisions1)
        assertEquals(2, expandBy.size)
        assertEquals(1, expandBy.first().first.priority)
        assertEquals(2, expandBy[1].first.priority)
        assertEquals(parseSubset("-###-"), expandBy.first().second)
        assertEquals(parseSubset("#---#--"), expandBy[1].second)
    }

    @Test
    fun testRebase() {
        val insertsList = """
            --#-
            ----#
        """.trimIndent()
        val inserts = parseSubsetList(insertsList)

        val revisions = basicInsertOps(inserts, 1)
        val revisionsOther = basicInsertOps(inserts, 2)

        val textOther = Rope("zpbj")
        val tombstonesOther = Rope("a")
        val deletesFromUnionOther = parseSubset("-#---")
        val deltaOpsOther = computeDeltas(revisionsOther, textOther, tombstonesOther, deletesFromUnionOther)

        val text = Rope("zcbd")
        val tombstones = Rope("a")
        val deletesFromUnion = parseSubset("-#---")
        val expandBy = computeTransforms(revisions)

        val rebaseResult = rebase(expandBy, deltaOpsOther, text, tombstones, deletesFromUnion, 0)
        val rebasedInserts = rebaseResult.revisions.map {
            when (val content = it.edit) {
                is Edit -> content.inserts
                is Undo -> error("not supported")
            }
        }
        debugSubsets(rebasedInserts)
        val expected = parseSubsetList(
            """
            ---#--
            ------#
            """.trimIndent()
        )
        assertEquals(expected, rebasedInserts)
        assertEquals("zcpbdj", rebaseResult.text.toString())
        assertEquals("a", rebaseResult.tombstones.toString())
        assertEquals(parseSubset("-#-----"), rebaseResult.deletesFromUnion)
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
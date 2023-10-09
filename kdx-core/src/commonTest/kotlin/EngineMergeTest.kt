package kdx

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineMergeTest {

    @Test
    fun testBasicRearrange1() {
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

    private fun basicInsertOps(inserts: List<Subset>, priority: Int): List<Revision> {
        return inserts.mapIndexed { index, it ->
            val deletes = Subset(inserts.size)
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
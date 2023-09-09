package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultisetTest {

    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test // does this even has some value?
    fun testBuilder() {
        val subset = buildSubset {}
        assertTrue(subset.isEmpty())
    }

    @Test
    fun testApplyTo() {
        val ranges = listOf(
            0 to 1,
            2 to 4,
            6 to 11,
            13 to 14,
            15 to 18,
            19 to 23,
            24 to 26,
            31 to 32,
            33 to 35,
            36 to 37,
            40 to 44,
            45 to 48,
            49 to 51,
            52 to 57,
            58 to 59,
        )
        val subset = buildSubset {
            for ((start, end) in ranges) add(start, end, 1)
            paddingToLength(simpleString.length)
        }
        assertEquals("145BCEINQRSTUWZbcdimpvxyz", subset.deleteFromString(simpleString))
    }

    @Test
    fun testFindDeletions() {
        val str = "015ABDFHJOPQVYdfgloprsuvz"
        val subset = str.findDeletions(simpleString)
        assertEquals(str, subset.deleteFromString(simpleString))
        assertTrue(subset.isNotEmpty())
    }
}

//TODO: better name candidate diff()?
private fun String.findDeletions(other: String): Subset {
    return buildSubset {
        val base = other
        val final = this@findDeletions
        var j = 0
        for (i in base.indices) {
            if (j < final.length && final[j].code == base[i].code) {
                j++
            } else {
                add(i, i + 1, 1)
            }
        }
        paddingToLength(base.length)
    }
}
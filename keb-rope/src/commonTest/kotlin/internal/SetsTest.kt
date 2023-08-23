package keb.ropes.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SetsTest {

    @Test
    fun testSymmetricDifference() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(3, 4, 5)
        for (i in set1.symmetricDifference(set2)) {
            when (i) {
                1 -> assertEquals(1, i)
                2 -> assertEquals(4, i)
                3 -> assertEquals(3, i)
            }
        }
    }

    @Test
    fun testSymmetricDifferenceThrows() {
        val set1 = setOf(1, 2, 3, 5)
        val set2 = setOf(3, 4, 5)
        assertFailsWith<IllegalArgumentException> { set1.symmetricDifference(set2) }
    }

    @Test
    fun testIntersection() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(2, 4, 5)
        val result = set1.intersection(set2).toList()
        assertEquals(listOf(2), result)

        val set3 = setOf(1, 9, 7)
        val set4 = setOf(4, 5, 6, 7, 9)
        val result2 = set3.intersection(set4).toList()
        assertEquals(listOf(7, 9), result2)
    }
}
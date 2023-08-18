package ropes.internal

import keb.ropes.internal.symmetricDifference
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
}
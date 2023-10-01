package kdx.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetsTest {

    @Test
    fun testBasicSymmetricDifference() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(3, 4, 5)
        val symmetricDifference = set1.symmetricDifference(set2)
        assertContainsExactly(symmetricDifference, 1, 2, 4, 5)
    }

    @Test
    fun testBasicSymmetricDifference2() {
        val set1 = setOf(1, 2, 3)
        val set2 = setOf(4, 2, 3, 4)
        val symmetricDifference = set1.symmetricDifference(set2)
        assertContainsExactly(symmetricDifference, 1, 4)
    }

    private fun <T> assertContainsExactly(iterable: Iterable<T>, vararg element: T) {
        val elements = element.toSet()
        val mutableIterable = iterable.toMutableList()
        for (item in elements) {
            assertTrue(mutableIterable.contains(item))
            mutableIterable.remove(item)
        }
        assertTrue(mutableIterable.isEmpty(), "iterable contains extra items: $mutableIterable")
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
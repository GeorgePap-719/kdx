package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class RopeIteratorTest {

    @Test
    fun testIterator() {
        val string = createString(SIZE_OF_LEAF * 8)
        val rope = Rope(string)

        var index = 0
        for (char in rope) {
            assertEquals(char, string[index++])
        }
    }

    @Test
    fun minorStressTestIterator() {
        val string = createString(SIZE_OF_LEAF * 50)
        val rope = Rope(string)

        var index = 0
        for (char in rope) {
            assertEquals(char, string[index++])
        }
    }

    @Test
    fun testIteratorWithIndex() {
        val string = createString(SIZE_OF_LEAF * 8)
        val rope = Rope(string)

        var index = SIZE_OF_LEAF
        val iterator = rope.iteratorWithIndex(index)
        while (iterator.hasNext()) {
            assertEquals(iterator.next(), string[index++])
        }
    }

    @Test
    fun minorStressTestIteratorWithIndex() {
        val string = createString(SIZE_OF_LEAF * 50)
        val rope = Rope(string)

        var index = SIZE_OF_LEAF
        val iterator = rope.iteratorWithIndex(index)
        while (iterator.hasNext()) {
            assertEquals(iterator.next(), string[index++])
        }
    }
}
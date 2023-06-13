package ropes

import keb.assert
import keb.ropes.Rope
import kotlin.test.Test

class TestRope {

    @Test
    fun testIndex() {
        val rope = Rope("Test something")
        assert { rope[0] == 'T' }
        assert { rope[1] == 'e' }
        assert { rope[2] == 's' }
        assert { rope[3] == 't' }
        assert { rope[4] == ' ' }
        assert { rope[5] == 's' }
        assert { rope[6] == 'o' }
        assert { rope[7] == 'm' }
        assert { rope[7] == 'm' }
        assert { rope[14] == null }
    }

    @Test
    fun testBigIndexes() {
        val bigString = createString(10 * SIZE_OF_LEAF)
        val rope = Rope(bigString)

        for (index in bigString.indices) {
            assert { rope[index] == bigString[index] }
        }

        assert { rope[bigString.length] == null } // out of bounds
    }

    @Test
    fun stressTestIndexed() {
        val bigString = createString(100_000)
        val rope = Rope(bigString)

        for (index in bigString.indices) {
            assert { rope[index] == bigString[index] }
        }

        assert { rope[bigString.length] == null } // out of bounds
    }

    @Test
    fun stressTestForBigNumbersOutOfBounds() {
        val string = createString(SIZE_OF_LEAF)
        val rope = Rope(string)
        for (i in SIZE_OF_LEAF..SIZE_OF_LEAF * 100) {
            assert { rope[i] == null }
        }
    }

    @Test
    fun testLength() {
        val testValue = "Test something"
        val rope = Rope(testValue)
        assert { rope.length() == testValue.length }
    }

    @Test//TODO:
    fun testBigLengths() {
        val bigString = buildString {
            for (i in 0 until 64 * 32 * 10) {
                append("1")
            }
        }
        val rope = Rope(bigString)
        assert { rope.length() == bigString.length }
    }
}

private fun createString(factor: Int): String {
    return buildString {
        var indexStr = 0
        for (i in 0 until factor) {
            if (indexStr == 10) indexStr = 0
            append(indexStr++)
        }
    }
}

private const val SIZE_OF_LEAF = 64 * 32
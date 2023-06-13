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
        val bigString = buildString {
            var indexStr = 0
            for (i in 0 until 64 * 32 * 10) {
                if (indexStr == 10) indexStr = 0
                append(indexStr++)
            }
        }
        val len = bigString.length
        val rope = Rope(bigString)
        println(rope)
        println(bigString.length)

        assert { rope[0] == '0' }
        assert { rope[1] == '1' }
        assert { rope[2] == '2' }
        assert { rope[3] == '3' }
        assert { rope[4] == '4' }
        assert { rope[5] == '5' }
        assert { rope[6] == '6' }
        assert { rope[7] == '7' }
//
        println(rope[len - 1])
        assert { rope[len - 1] == '9' }

        assert { rope[len] == null }
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
        println(bigString.length)
        println(rope.length())
        assert { rope.length() == bigString.length }
    }
}
package ropes

import keb.assert
import keb.ropes.Rope
import kotlin.random.Random
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
        val random = Random
        val bigString = buildString {
            for (i in 0 until 64 * 32 * 10) {
                append(random.nextInt(0, 9))
            }
        }
        val rope = Rope(bigString)
        println(rope)
        println(rope.length())

//        assert { rope[0] == '0' }
//        assert { rope[1] == '1' }
//        assert { rope[2] == '2' }
//        assert { rope[3] == '3' }
//        assert { rope[4] == '4' }
//        assert { rope[5] == '5' }
//        assert { rope[6] == '6' }
//        assert { rope[7] == '7' }
//        println("indexOf:2986:${rope[2987]}")
//        assert { rope[rope.length()] == null }
    }

    @Test
    fun testLength() {
        val testValue = "Test something"
        val rope = Rope(testValue)
        assert { rope.length() == testValue.length }
    }

    @Test
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
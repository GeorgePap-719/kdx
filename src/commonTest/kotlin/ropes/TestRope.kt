package ropes

import keb.assert
import keb.ropes.Rope
import kotlin.test.Test

class TestRope {

    @Test
    fun testSearch() {
        val rope = Rope("Test something")
        assert { rope[0] == 'T' }
        val bigString = buildString {
            for (i in 0 until 64 * 32) {
                append("1")
            }
        }
        val rope2 = Rope(bigString)
        println(rope2[2044])

        println(rope2.toString())

//        assert { rope2[0] == '0' }
//        assert { rope2[1] == '1' }
//        assert { rope2[2] == '2' }
//        assert { rope2[3] == '3' }
//        assert { rope2[4] == '4' }
//        assert { rope2[5] == '5' }
//        assert { rope2[6] == '6' }
//        assert { rope2[7] == '7' }
//        assert { rope2[8] == '8' }
//        assert { rope2[2044] == '2' } // last number of 2047
//        assert { rope2[2045] == '0' } // last number of 2047
//        assert { rope2[2046] == '4' } // last number of 2047
//        assert { rope2[2047] == '7' } // last number of 2047
        assert { rope2[2048] == '7' } // last number of 2047
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
            for (i in 0 until 64 * 32) {
                append("1")
            }
        }
        val rope = Rope(bigString)
        assert { rope.length() == bigString.length }
    }
}
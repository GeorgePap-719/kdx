package ropes

import keb.assert
import keb.ropes.Rope
import keb.ropes.insert
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.Test
import kotlin.test.assertFailsWith

//TODO: refactor assert {} -> assert()
class RopeTest {

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
        assert { rope.length == testValue.length }
    }

    @Test
    fun testBigLengths() {
        val bigString = createString(SIZE_OF_LEAF * 10)
        val rope = Rope(bigString)
        assert { rope.length == bigString.length }
    }

    @Test
    fun stressTestLength() {
        val bigString = createString(SIZE_OF_LEAF * 1000)
        val rope = Rope(bigString)
        assert { rope.length == bigString.length }
    }

    @Test
    fun testSingleNodeInsertFirstWithRoomInLeaf() {
        val string = createString(SIZE_OF_LEAF - 1)
        val rope = Rope(string)
        val newRope = rope.insert(0, 'h')
        assert { newRope !== rope }
        assert { newRope[0] == 'h' }
    }

    //@Test//TODO: this cannot be tested easily for now.
    fun testInsertFirstWithRoomInLeaf() {
        val string = createString((SIZE_OF_LEAF - 1) * 8)
        val rope = Rope(string)
        val newRope = rope.insert(0, 'h')
        assert { newRope !== rope }
        assert { newRope[0] == 'h' }
    }

    @Test
    fun testInsertFirstWithNoRoomInLeaf() {
        val string = createString(SIZE_OF_LEAF * 8)
        val rope = Rope(string)
        val newRope = rope.insert(0, 'h')
        assert { newRope !== rope }
        assert { newRope[0] == 'h' }
    }

    @Test
    fun testInsertWithRandomIndex() {
        val string = createString(SIZE_OF_LEAF * 8)
        var rope = Rope(string)
        var len = rope.length
        val sb = StringBuilder(string)

        for (i in 0 until 100) {
            val randomI = Random.nextInt(0, string.length)
            rope = rope.insert(randomI, 'a')
            sb.insert(randomI, 'a')

            println(rope[randomI])
            assert { rope[randomI] == 'a' }
            assert { rope.length > len++ }
        }

        for (i in 0 until sb.length - 1) {
            assert { rope[i] == sb[i] }
        }
    }

    @Test
    fun stressTestInsertWithRandomIndex() {
        val string = createString(20_000)
        var rope = Rope(string)
        val sb = StringBuilder(string)

        for (i in 0 until 2500) { // 10_000 < takes too long.
            val randomI = Random.nextInt(0, string.length)
            val randomCodepoint = Random.nextInt(alphabet)
            val randomChar = randomCodepoint.toChar()
            rope = rope.insert(randomI, randomChar)
            sb.insert(randomI, randomChar)

            assert { rope[randomI] == randomChar }
        }

        for (i in 0 until sb.length - 1) assert { rope[i] == sb[i] }
    }

    @Test
    fun testWeAllowInsertOnLenIndex() {
        val size = 1000
        val rope = Rope(createString(size))
        rope.insert(size, 'a')
    }

    @Test
    fun testInsertThrowsForOutOfBounds() {
        val size = 1000
        val rope = Rope(createString(1000))
        assertFailsWith<IndexOutOfBoundsException> { rope.insert(size + 1, 'a') }
    }

    @Test
    fun testDeleteAtFirstIndexWithOneLeaf() {
        val string = createString(SIZE_OF_LEAF - 1)
        val rope = Rope(string)
        val newRope = rope.deleteAt(0)
        assert { newRope !== rope }
        assert { newRope[0] != '0' }
    }

    @Test
    fun testDeleteAtLastIndexWithOneLeaf() {
        val string = createString(SIZE_OF_LEAF - 1)
        val rope = Rope(string)
        val newRope = rope.deleteAt(rope.length - 1) // lastIndex == '6'
        assert { newRope !== rope }
        assert { newRope[rope.length] != '6' }
    }

    @Test
    fun testDeleteAtWithRandomIndex() {
        val string = createString(SIZE_OF_LEAF * 8)
        var rope = Rope(string)
        val sb = StringBuilder(string)
        var curLen: Int
        var afterDeleteLen: Int

        for (i in 0 until 100) {
            val randomI = Random.nextInt(0, string.length - i)
            curLen = rope.length
            rope = rope.deleteAt(randomI)
            afterDeleteLen = rope.length
            sb.deleteAt(randomI)

            assert { afterDeleteLen < curLen }
        }

        for (i in 0 until sb.length - 1) {
            assert { rope[i] == sb[i] }
        }
    }

    @Test
    fun testSubRope() {
        val string = createString(SIZE_OF_LEAF * 8)
        var rope = Rope(string)

        val randomI = Random.nextInt(0, string.length - 1)
        rope = rope.subRope(0, randomI)
        val newString = string.substring(0, randomI)

        for (i in 0 until newString.length - 1) {
            assert { rope[i] == newString[i] }
        }
    }

    @Test
    fun minorStressTestSubRope() {
        repeat(10) {
            val string = createString(SIZE_OF_LEAF * 8)
            var rope = Rope(string)

            val randomI = Random.nextInt(0, string.length - 1)
            rope = rope.subRope(0, randomI)
            val newString = string.substring(0, randomI)

            for (i in 0 until newString.length - 1) {
                assert { rope[i] == newString[i] }
            }
        }
    }

    @Test
    fun testDeleteRange() {
        val string = createString(SIZE_OF_LEAF * 8)
        var rope = Rope(string)

        val randomI = Random.nextInt(0, string.length - 1)
        rope = rope.removeRange(0, randomI)
        val newString = string.removeRange(0, randomI)

        for (i in 0 until newString.length - 1) {
            assert { rope[i] == newString[i] }
        }
    }

    @Test
    fun minorStressTestDeleteRange() {
        repeat(10) {
            val string = createString(SIZE_OF_LEAF * 8)
            var rope = Rope(string)

            val randomI = Random.nextInt(0, string.length - 1)
            rope = rope.removeRange(0, randomI)
            val newString = string.removeRange(0, randomI)

            for (i in 0 until newString.length - 1) {
                assert { rope[i] == newString[i] }
            }
        }
    }
}


private val alphabet = 97..122
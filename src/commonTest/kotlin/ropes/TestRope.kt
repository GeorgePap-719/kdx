package ropes

import keb.ropes.read32Chunks
import kotlin.test.Test

class TestRope {

    @Test
    fun testCreationOfRope() {
    }

    @Test
    fun testChunked() {

    }

    @Test
    fun testReadChunks() {
        val string = buildString {
            for (i in 0 until 64 * 32) {
                append("$i")
            }
        }
        val nodes = read32Chunks(string)

    }
}
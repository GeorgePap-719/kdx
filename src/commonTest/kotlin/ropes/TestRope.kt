package ropes

import kotlin.test.Test

class TestRope {

    @Test
    fun testCreationOfRope() {

    }

    @Test
    fun testChunked() {
        val stringList = buildList {
            for (i in 0 until 65) {
                add("$i")
            }
        }
        println(stringList.chunked(32))
    }
}
package ropes

import keb.ropes.InternalNode
import keb.ropes.read32Chunks
import kotlin.test.Test

class TestRope {

    @Test
    fun testCreationOfRope() {
        val root = InternalNode(0, 0)
        for (node in root) {

        }
    }

    @Test
    fun testReadChunks() {
        val string = buildString {
            for (i in 0 until 64 * 32) {
                append("$i")
            }
        }
        val nodes = read32Chunks(string)
        println(nodes.isEmpty())
        println(nodes.size)
        nodes.forEach {
            println(it.toStringDebug())
        }
    }

}
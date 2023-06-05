package ropes

import keb.ropes.*
import kotlin.test.Test

class TestBtree {

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

    @Test
    fun testSplit() {
        val string = buildString {
            for (i in 0 until 64 * 32) {
                append("$i")
            }
        }
        val root = btreeOf(string)
        println(root.toStringDebug())
        println(root.isBalanced())
    }

    @Test
    fun testBalancing() {
        val nodes = buildList {
            for (i in 0 until 64) {
                add(LeafNode("$i"))
            }
        }
        val root = createInvalidParent(nodes)

        val balancedRoot = root.rebalance()
        println(balancedRoot.isBalanced())
        println(balancedRoot.toStringDebug())
    }

    private fun createInvalidParent(nodes: List<BTreeNode>): InternalNode {
        val weight = nodes.first().height
        val height = nodes.maxOf { it.height } + 1
        return InternalNode(weight, height, nodes)
    }
}
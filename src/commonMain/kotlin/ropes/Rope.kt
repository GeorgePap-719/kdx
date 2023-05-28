package keb.ropes

open class Rope(value: String)

// btree impl

/**
 * Represents a self-balancing tree node.
 */
// Note:
// Each leaf node contains a string (see note below) and its length, called weight. Each intermediate node also contains
// a weight which is the sum of all the leaves in its left subtree.
open class BTreeNode(
    /**
     * If this node is a leaf, then this value represents the length of the string. Else holds the sum of the lengths of
     * all the leaves in its left subtree.
     */
    val weight: Int,
    val value: String? = null,
//    left: BTreeNode? = null,
//    right: BTreeNode? = null,
    val children: MutableList<BTreeNode> = mutableListOf(), // maybe this should be read-only?
    private val isRoot: Boolean = false
) {
    val isInternalNode: Boolean get() = children.isNotEmpty()
    val isLeafNode: Boolean get() = children.isEmpty()

    fun insert(value: String) {
        // To insert a new element, search the tree to find the leaf node where the new element should be added
    }
}

/**
 * Reads up to 32 chunks of characters and creates a node from them. The process is repeated until the end of [input].
 */
private fun readChunks(input: String): List<BTreeNode> {
    val len = input.length
    var charCount = 0
    val stringBuilder = StringBuilder()
    for ((index, char) in input.withIndex()) {
        if (charCount == CHUNK_SIZE) {
            val string = stringBuilder.toString()
            return listOf(BTreeNode(string.length, string)) + readChunks(
                input.subSequence(index, input.length - 1).toString()
            )
        }
        charCount++
    }
    error("should not reach here")
}

// naive impl, we might stackOverFlow for big inputs
private fun read32Chunks(input: String): List<BTreeNode> {
    var charCount = 0
    val stringBuilder = StringBuilder()
    while (charCount != CHUNK_SIZE && charCount < input.length) {
        stringBuilder.insert(charCount, input[charCount])
        charCount++
    }
    val string = stringBuilder.toString()
    val node = listOf(BTreeNode(string.length, string))
    if (charCount + 1 >= input.length) return node
    return node + read32Chunks(input.substring(charCount))
}

const val CHUNK_NUMBER = 32
const val CHUNK_SIZE = 64

//class RootBTreeNode : BTreeNode(0)
//
//class InternalBTreeNode
//
//class LeafBTreeNode(value: String) : BTreeNode(0, value)

fun bTreeOf(value: String): BTreeNode {
    require(value.isNotBlank()) { "input string cannot be blank" }
    require(value.isNotEmpty()) { "input string cannot be empty" }
    return BTreeNode(value.length, value)
}

// some questions
// 1. how we define how much of a string each node should hold?
// 2. should we ever edit a node? --> probably not, better to create a new one.
package keb.ropes

import keb.classSimpleName
import keb.hexAddress

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
    val children: MutableList<BTreeNode> = mutableListOf(), // maybe this should be read-only?
    private val isRoot: Boolean = false
) {
    val isInternalNode: Boolean get() = children.isNotEmpty()
    val isLeafNode: Boolean get() = children.isEmpty()

    fun insert(value: String) {
        // To insert a new element, search the tree to find the leaf node where the new element should be added
        read32Chunks(value)
    }

    // ###################
    // # Debug Functions #
    // ###################

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName(")
        sb.append("weight=$weight,")
        if (isInternalNode) {
            sb.append("children=$children,")
            sb.append("isInternalNode=true")
        } else {
            sb.append("value=$value,")
            sb.append("isLeafNode=true")
        }
        sb.append(")")
        return sb.toString()
    }

    internal fun toStringDebug(): String {
        val sb = StringBuilder()
        sb.append("$classSimpleName@$hexAddress(")
        sb.append("weight=$weight,")
        if (isInternalNode) {
            sb.append("isInternalNode=true")
        } else {
            sb.append("isLeafNode=true")
        }
        sb.append(")")
        return sb.toString()
    }
}

/**
 * Reads up to 32 chunks of characters and creates a node from them. The process is repeated until the end of [input].
 */
fun read32Chunks(input: String): List<BTreeNode> { //TODO: make it private?
    val chunks = readChunksOf64Chars(input)
    return buildList {
        chunks.chunked(CHUNK_NUMBER).forEach { chunks ->
            val valueNode = chunks.joinToString(separator = "") { it }
            add(BTreeNode(valueNode.length, valueNode))
        }
    }
}

private fun readChunksOf64Chars(input: String): List<String> {
    val chunks = mutableListOf<String>()
    val len = input.length
    var chunkCount = 0
    val chunkBuilder = StringBuilder()
    for (i in 0 until len) {
        if (chunkCount == CHUNK_SIZE) {
            val chunk = chunkBuilder.toString()
            chunks.add(chunk)
            chunkBuilder.clear()
            chunkCount = 0
        }
        if (i == len - 2) {
            chunkBuilder.append(input[i])
            val chunk = chunkBuilder.toString()
            chunks.add(chunk)
            break
        }
        chunkBuilder.append(input[i])
        chunkCount++
    }
    return chunks
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
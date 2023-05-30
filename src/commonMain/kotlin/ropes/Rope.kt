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
sealed class BTreeNode(
    /**
     * If this node is a leaf, then this value represents the length of the string. Else holds the sum of the lengths of
     * all the leaves in its left subtree.
     */
    val weight: Int,
) {
    abstract val isInternalNode: Boolean
    abstract val isLeafNode: Boolean

    fun insert(value: String) {
        // To insert a new element, search the tree to find the leaf node where the new element should be added
        read32Chunks(value)
        //TODO
    }

    // ###################
    // # Debug Functions #
    // ###################

//    override fun toString(): String {
//        val sb = StringBuilder()
//        sb.append("$classSimpleName(")
//        sb.append("weight=$weight,")
//        if (isInternalNode) {
//            sb.append("children=$children,")
//            sb.append("isInternalNode=true")
//        } else {
//            sb.append("value=$value,")
//            sb.append("isLeafNode=true")
//        }
//        sb.append(")")
//        return sb.toString()
//    }

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
//            add(BTreeNode(valueNode.length, valueNode))
            TODO()
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

//fun bTreeOf(value: String): BTreeNode {
//    require(value.isNotBlank()) { "input string cannot be blank" }
//    require(value.isNotEmpty()) { "input string cannot be empty" }
//    return BTreeNode(value.length, value)
//}

// some questions
// 1. how we define how much of a string each node should hold?
// 2. should we ever edit a node? --> probably not, better to create a new one.

/**
 * Represents a leaf-node in a [B-tree][https://en.wikipedia.org/wiki/B-tree#Notes].
 */
class LeafNode(weight: Int, private val value: String) : BTreeNode(weight) {
    override val isInternalNode: Boolean = false
    override val isLeafNode: Boolean = true

    val length: Int = value.length

    // Returns BTreeNode, in case splitting happened, else null (node has enough capacity).
    fun tryPushAndMaybeSplit(other: LeafNode): BTreeNode? {
        TODO("Not yet impl")
    }

    operator fun plus(other: LeafNode): LeafNode {
        TODO("Not yet impl")
    }

    operator fun minus(other: LeafNode): LeafNode {
        TODO("Not yet impl")
    }
}

private const val MIN_CHILDREN = 4
private const val MAX_CHILDREN = 8


/**
 * Represents an internal-node in a [B-tree][https://en.wikipedia.org/wiki/B-tree#Notes].
 */
// Notes:
// Internal node does not have insert operation().
class InternalNode(
    weight: Int,
    val children: List<BTreeNode> = listOf()
) : BTreeNode(weight) {
    override val isInternalNode: Boolean = true
    override val isLeafNode: Boolean = false
}






package keb.ropes

import keb.internal.EmptyIterator

//TODO: lineCount
open class RopeLeaf(val value: String, val lineCount: Int? = null) : Leaf, Iterable<Char>, CharSequence {
    override val weight: Int = value.length

    @Suppress("LeakingThis")
    override val isLegal: Boolean = weight <= MAX_SIZE_LEAF && value.isNotEmpty()
    override val isEmpty: Boolean = value.isEmpty()

    override fun iterator(): Iterator<Char> = value.iterator()

    @Suppress("LeakingThis")
    override val length: Int = weight
    override fun get(index: Int): Char = value[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = value.subSequence(startIndex, endIndex)

    fun add(index: Int, element: String): RopeLeaf {
        val newValue = buildString {
            val str = this
            for (i in str.indices) {
                if (i == index) append(element)
                append(str[i])
            }
            if (index == str.length) append(element)
        }
        return RopeLeaf(newValue)
    }

    fun deleteAt(index: Int): RopeLeaf {
        return RopeLeaf(value.deleteAt(index))
    }

    private fun String.deleteAt(index: Int): String = buildString {
        val str = this@deleteAt
        for (i in str.indices) {
            if (i == index) continue
            append(str[i])
        }
    }

    override fun toString(): String = "RopeLeaf($value,$lineCount)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RopeLeaf) return false
        if (value != other.value) return false
        return lineCount == other.lineCount
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + (lineCount ?: 0)
        return result
    }
}

internal object EmptyRopeLeaf : RopeLeaf("", null) {
    override val weight: Int = 0
    override val isLegal: Boolean = false
    override val length: Int = 0
    override val isEmpty: Boolean = true

    override fun get(index: Int): Char =
        throw IndexOutOfBoundsException("Empty leaf doesn't contain element at index:$index")

    override fun toString(): String = "RopeLeaf()"
    override fun equals(other: Any?): Boolean = other is RopeLeaf && other.isEmpty
    override fun hashCode(): Int = 1
    override fun iterator(): Iterator<Char> = EmptyIterator
}

fun RopeLeaf.add(index: Int, element: Char): RopeLeaf = add(index, element.toString())

typealias RopeInternalNode = InternalNode<RopeLeaf>
typealias RopeLeafNode = LeafNode<RopeLeaf>

private val emptyRopeLeafNode = RopeLeafNode(EmptyRopeLeaf)

internal fun RopeLeafNode(input: String): RopeLeafNode =
    if (input.isEmpty()) emptyRopeLeafNode else RopeLeafNode(RopeLeaf(input))

typealias RopeNode = BTreeNode<RopeLeaf>

internal fun createParent(nodes: List<RopeNode>): RopeInternalNode = createParent(nodes)

internal fun ropeNodeOf(input: String): RopeNode {
    return splitIntoNodes(input)
}

fun emptyRopeNode(): RopeNode {
    return emptyBTreeNode()
}

private fun splitIntoNodes(input: String): RopeNode {
    if (input.length <= MAX_SIZE_LEAF) return RopeLeafNode(input)
    val leaves = splitIntoLeaves(input)
    return merge(leaves)
}

private fun splitIntoLeaves(input: String): List<RopeLeafNode> {
    return buildList {
        var index = 0
        while (index < input.length) {
            val leafValue = input.substring(index, minOf(index + MAX_SIZE_LEAF, input.length))
            add(RopeLeafNode(leafValue))
            index += MAX_SIZE_LEAF
        }
    }
}

// ------ string-leaf utils ------

internal fun expandLeaf(leaf: RopeLeafNode): RopeInternalNode {
    val half = leaf.weight / 2
    val left = RopeLeafNode(leaf.leaf.substring(0, half))
    val right = RopeLeafNode(leaf.leaf.substring(half))
    return merge(left, right)
}

// ------ addXXX ------

// - Adds the element into the leaf and expands as necessary. Returns the resulting leaves.
//
// This operation creates one resulting string from the element and this leaf. This can
// be expensive in most cases. One solution is to avoid creating a big string by creating
// leaf nodes on the spot. Though, this is easier than done.
//TODO: research this if there is time.
internal fun RopeLeafNode.expandableAdd(index: Int, element: String): List<RopeLeafNode> {
    checkValueIndex(index, this)
    val newLen = leaf.length + element.length
    if (newLen <= MAX_SIZE_LEAF) return listOf(add(index, element))
    val newLeaf = leaf.add(index, element)
    return splitIntoLeaves(newLeaf.value)
}

internal fun RopeLeafNode.add(index: Int, element: Char): RopeLeafNode = add(index, element.toString())

/**
 * Returns a new leaf with the specified [element] inserted at the specified [index].
 *
 * @throws IndexOutOfBoundsException if [index] is greater than or equals to the length of this child.
 * @throws IllegalArgumentException if the resulting length exceeds the maximum size of a leaf.
 */
internal fun RopeLeafNode.add(index: Int, element: String): RopeLeafNode {
    checkValueIndex(index, this)
    val newLen = leaf.length + element.length
    require(newLen <= MAX_SIZE_LEAF) { "max size of a leaf is:$MAX_SIZE_LEAF, but got:$newLen" }
    if (index == 0) return RopeLeafNode(element + leaf.value)
    if (index == leaf.value.lastIndex + 1) return RopeLeafNode(leaf.value + element)
    val newValue = leaf.add(index, element)
    return RopeLeafNode(newValue)
}

// ------ deleteXXX ------

internal inline fun RopeLeafNode.deleteAtAndIfEmpty(index: Int, onEmpty: () -> RopeLeafNode): RopeLeafNode {
    checkElementIndex(index, this)
    val newValue = leaf.deleteAt(index)
    if (newValue.isEmpty()) return onEmpty()
    return RopeLeafNode(newValue)
}

internal fun RopeLeafNode.deleteAt(index: Int): RopeLeafNode {
    checkElementIndex(index, this)
    return RopeLeafNode(leaf.deleteAt(index))
}

private fun checkValueIndex(index: Int, leaf: RopeLeafNode) {
    if (index < 0 || index > leaf.leaf.lastIndex + 1) { // it is acceptable for an index to be right after the last-index
        throw IndexOutOfBoundsException("index:$index, leaf-length:${leaf.leaf.length}")
    }
}

private fun checkElementIndex(index: Int, leaf: RopeLeafNode) {
    if (index < 0 || index > leaf.leaf.lastIndex) {
        throw IndexOutOfBoundsException("index:$index, leaf-length:${leaf.weight}")
    }
}

internal const val MAX_SIZE_LEAF = 2048
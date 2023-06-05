package keb.ropes


/**
 * Represents a [Rope data structure](https://en.wikipedia.org/wiki/Rope_(data_structure)#Index).
 */
class Rope(value: String)

fun btreeOf(input: String): BTreeNode {
    if (input.length < MAX_SIZE_LEAF) {
        return LeafNode(input)
    } else {
        TODO("split")
    }
}

fun split() {

}

fun concat() {

}
package kdx

import kdx.btree.collectLeaves
import kdx.btree.merge

/**
 * Builds a rope using a [RopeBuilder].
 * It collects the strings through [RopeBuilder.add], and once it reaches 2048 characters, it creates a node.
 * This way, it is able to effectively create a [rope][Rope] from the collected nodes.
 *
 * Inspired from [fleet's blog](https://blog.jetbrains.com/fleet/2022/02/fleet-below-deck-part-ii-breaking-down-the-editor/)
 * on ropes.
 */
fun buildRope(action: RopeBuilder.() -> Unit): Rope {
    val builder = RopeBuilder()
    builder.action()
    return builder.build()
}

/**
 * Builder of the [Rope] instance provided by `Rope` factory function.
 */
class RopeBuilder internal constructor() {
    private val leaves = mutableListOf<RopeLeafNode>()
    private val currentString = StringBuilder(MAX_SIZE_LEAF)

    /**
     * Adds the given [input] to the end of this [Rope].
     */
    fun add(input: String) {
        if (currentString.length + input.length <= MAX_SIZE_LEAF) {
            currentString.append(input)
            return
        }
        val freeSpace = freeSpaceInCurrString(input.length)
        val strSlice = input.substring(0, freeSpace)
        currentString.append(strSlice)
        pushStringIntoLeaves()
        currentString.append(input.substring(freeSpace))
    }

    private fun freeSpaceInCurrString(growth: Int): Int {
        return MAX_SIZE_LEAF - growth
    }

    private fun pushStringIntoLeaves() {
        val leafStr = currentString.toString()
        if (leafStr.isEmpty()) return
        currentString.clear()
        leaves.add(RopeLeafNode(leafStr))
    }

    internal fun build(): Rope {
        pushStringIntoLeaves() // Collect remain strings if any
        val root = merge(leaves)
        return Rope(root)
    }
}

/**
 * Adds the given [input] to the end of this [Rope].
 */
fun RopeBuilder.add(input: Rope) {
    val leaves = input.collectLeaves()
    for (leaf in leaves) add(leaf.chars)
}

/**
 * Adds the given [input] to the end of this [Rope].
 */
fun RopeBuilder.add(input: RopeNode) {
    val leaves = input.collectLeaves()
    for (leaf in leaves) add(leaf.value.chars)
}

/**
 * Adds a sub-rope of the given [input] at indices from the specified [range], to the end of this [Rope].
 */
fun RopeBuilder.add(input: Rope, range: IntRange) {
    val rope = input.subRope(range)
    add(rope)
}

/**
 * Adds a sub-rope of the given [input] from a range starting at the [startIndex] and ending right before [endIndex]
 * (`endIndex` exclusive), to the end of this [Rope].
 */
fun RopeBuilder.add(input: Rope, startIndex: Int, endIndex: Int) {
    val rope = input.subRope(startIndex, endIndex)
    add(rope)
}
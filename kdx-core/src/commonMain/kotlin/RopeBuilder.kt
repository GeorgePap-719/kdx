package kdx

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

class RopeBuilder internal constructor() {
    private val leaves = mutableListOf<RopeLeafNode>()
    private val currentString = StringBuilder(MAX_SIZE_LEAF)
    //private var lineCount: Int = 0

    fun add(input: String) {
        if (currentString.length + input.length <= MAX_SIZE_LEAF) {
            currentString.append(input)
            return
        }
        val freeIndexesInCurrStr = MAX_SIZE_LEAF - input.length
        val rightInputSlice = input.substring(0, freeIndexesInCurrStr)
        currentString.append(rightInputSlice)
        pushStringIntoLeaves()
        currentString.append(input.substring(freeIndexesInCurrStr))
    }

    private fun pushStringIntoLeaves() {
        val leafStr = currentString.toString()
        if (leafStr.isEmpty()) return
        currentString.clear()
        leaves.add(RopeLeafNode(leafStr))
    }

    fun build(): Rope {
        pushStringIntoLeaves() // Collect remain strings if any.
        val root = merge(leaves)
        return Rope(root)
    }
}
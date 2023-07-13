package keb.ropes

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
        currentString.clear()
        leaves.add(RopeLeafNode(leafStr))
    }

    fun build(): Rope {
        val root = merge(leaves)
        return Rope(root)
    }
}
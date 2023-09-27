package kdx

fun Rope.mutate(action: RopeMutator.() -> Unit): Rope {
    val builder = RopeMutator(this)
    builder.action()
    return builder.build()
}

class RopeMutator internal constructor(private var rope: Rope) {
    val length get() = rope.length

    fun build(): Rope = rope

    fun add(node: RopeNode) {
        rope += Rope(node)
    }

    fun addAll(nodes: List<RopeNode>) {
        val parent = createParent(nodes)
        rope += Rope(parent)
    }

    //TODO: remove operations.
    // Removing nodes on rope needs research.

    fun insert(index: Int, element: String) {
        rope = rope.insert(index, element)
    }

    fun indexOf(element: Char): Int = rope.indexOf(element)

    fun deleteAt(index: Int) {
        rope = rope.deleteAt(index)
    }

    fun removeRange(startIndex: Int, endIndex: Int) {
        rope = rope.removeRange(startIndex, endIndex)
    }

    fun subRope(startIndex: Int, endIndex: Int) {
        rope = rope.subRope(startIndex, endIndex)
    }

    fun collectLeaves(): List<RopeLeaf> = rope.collectLeaves()

    operator fun get(index: Int) = rope[index]

    operator fun plus(other: Rope) {
        rope += other
    }

    operator fun plusAssign(other: Rope) {
        rope += other
    }
}
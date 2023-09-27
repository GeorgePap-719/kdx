package keb.ropes

fun DeltaBuilder<RopeLeaf>.replace(range: IntRange, rope: Rope) {
    replace(range, rope.root)
}
package kdx

fun DeltaBuilder<RopeLeaf>.replace(range: IntRange, rope: Rope) {
    replace(range, rope.root)
}
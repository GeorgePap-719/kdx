package kdx.internal

import kdx.assert

/*
 * Helpers with ranges.
 * They exist mostly to cope with `intervals.rs`.
 */

internal fun IntRange.translate(amount: Int): IntRange {
    return IntRange(first + amount, last + amount)
}

internal fun IntRange.translateNeg(amount: Int): IntRange {
    assert { first >= amount }
    return IntRange(first - amount, last - amount)
}

// Returns a closed-open range.
internal fun IntRange.intoInterval(upperBound: Int): IntRange {
    return if (last > upperBound) first..<upperBound else this
}

@Suppress("EmptyRange")
internal fun emptyClosedOpenRange(): IntRange = 0..<0

internal fun IntRange.isNotEmpty(): Boolean = !isEmpty()
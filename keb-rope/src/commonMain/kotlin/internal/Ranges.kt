package keb.ropes.internal

import keb.ropes.assert

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
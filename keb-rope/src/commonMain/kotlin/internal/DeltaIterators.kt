package keb.ropes.internal

import keb.ropes.*

/**
 * The key idea is that the iterator is a special get type,
 * which can be invoked continuously to move to next indexes.
 * The logic is similar to `RopeIterator`.
 */
internal abstract class AbstractDeltaIterator : DeltaIterator {
    /**
     * Stores the element retrieved by [hasNext] or a special [ITERATOR_FINISHED] token if the iteration finished.
     * If [hasNext] has not been invoked yet, `null` is stored.
     */
    protected var nextResult: Any? = null // DeltaRegion || null || FINISHED

    final override operator fun next(): DeltaRegion {
        // Read the already received result or `null` if [hasNext] has not been invoked yet.
        val result = nextResult
        check(result != null) { "`hasNext()` has not been invoked" }
        nextResult = null
        // Is this iterator closed?
        if (result === ITERATOR_FINISHED) throw NoSuchElementException()
        return result as DeltaRegion
    }
}

internal class DeltaInsertsIterator<T : NodeInfo>(delta: Delta<T>) : AbstractDeltaIterator() {
    private var pos = 0 // offset || step?
    private var lastEnd = 0 // index || prevEndIndex?

    private val iterator = delta.changes.iterator()

    override fun hasNext(): Boolean {
        if (nextResult === ITERATOR_FINISHED) return false
        for (element in iterator) {
            when (element) {
                is Copy -> {
                    pos += element.endIndex - element.startIndex
                    lastEnd = element.endIndex
                }

                is Insert -> {
                    nextResult = DeltaRegion(lastEnd, pos, element.length)
                    pos += element.length
                    lastEnd += element.length
                    return true
                }
            }
        }
        nextResult = ITERATOR_FINISHED
        return false
    }
}

internal class DeltaDeletesIterator<T : NodeInfo>(
    delta: Delta<T>,
    private var pos: Int = 0,
    private var lastEnd: Int = 0,
    private var baseLen: Int = 0
) : AbstractDeltaIterator() {
    private val iterator = delta.changes.iterator()

    override fun hasNext(): Boolean {
        if (nextResult === ITERATOR_FINISHED) return false
        for (element in iterator) {
            when (element) {
                is Copy -> {
                    if (element.startIndex > lastEnd) {
                        nextResult = DeltaRegion(lastEnd, pos, element.startIndex - lastEnd)
                    }
                    pos += element.endIndex - element.startIndex
                    lastEnd = element.endIndex
                    return true
                }

                is Insert -> {
                    pos += element.length
                    lastEnd += element.length
                }
            }
        }
        if (lastEnd < baseLen) {
            nextResult = DeltaRegion(lastEnd, pos, baseLen - lastEnd)
            lastEnd = baseLen
            return true
        }
        nextResult = ITERATOR_FINISHED
        return false
    }
}

// Internal result for [DeltaIterator.nextResult].
// Indicates the iterator finished.
private val ITERATOR_FINISHED = Symbol("FINISHED")
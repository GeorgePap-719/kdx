package keb.ropes.internal

import java.util.concurrent.ConcurrentLinkedDeque

internal typealias ConcurrentLinkedList<T> = ConcurrentLinkedDeque<T>

internal fun <T> List<T>.toConcurrentLinkedList(): ConcurrentLinkedList<T> {
    return ConcurrentLinkedList<T>().also { it.addAll(this) }
}
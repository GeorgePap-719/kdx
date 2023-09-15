package keb.server

import reactor.core.CoreSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.many
import java.util.concurrent.atomic.AtomicInteger

class MutableSharedFlux<T : Any> : Flux<T>() {
    private val spins = 100
    private val flux = many().multicast().directBestEffort<T>()

    val statistics = AtomicInteger(1)

    override fun subscribe(p0: CoreSubscriber<in T>) {
        flux.asFlux().subscribe(p0)
    }

    fun emit(value: T) {
        if (tryEmit(value)) return
        var count = 0
        while (true) {
            if (count > spins) error("FAIL_NON_SERIALIZED")
            if (tryEmit(value)) break
            count++
        }
        updateStatistics(count)
    }

    fun emitWithDelay(value: T) {
        if (tryEmit(value)) return
        var count = 0
        while (true) {
            if (count > spins) error("FAIL_NON_SERIALIZED")
            if (tryEmit(value)) break
            count++
            Thread.sleep(10)
        }
        updateStatistics(count)
    }

    fun emitWithLock(value: T) {
        synchronized(this) {
            if (!tryEmit(value)) error("FAIL_NON_SERIALIZED")
        }
    }

    fun tryEmit(value: T): Boolean {
        val emitted = flux.tryEmitNext(value)
        return when (emitted) {
            Sinks.EmitResult.OK -> true
            Sinks.EmitResult.FAIL_TERMINATED -> error("unexpected")
            Sinks.EmitResult.FAIL_OVERFLOW -> {
                println("getting inside OVERFLOW")
                true
            }

            Sinks.EmitResult.FAIL_CANCELLED -> error("unexpected")
            Sinks.EmitResult.FAIL_NON_SERIALIZED -> false
            Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER -> true
        }
    }

    private fun updateStatistics(count: Int) {
        statistics.getAndUpdate {
            if (it < count) {
                count
            } else {
                it
            }
        }
    }
}
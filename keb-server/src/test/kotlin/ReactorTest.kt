package keb.server

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.test.assertFails

class ReactorTest {

    @Test
    fun simpleTest() = runBlocking {
        val fails = AtomicInteger(0)
        val flux = MutableSharedFlux<String>()

        launch(Dispatchers.Default) {
            flux.subscribe()
        }

        repeat(10_000) {
            if (!flux.tryEmit("hey")) fails.incrementAndGet()
        }
        println(fails.get())
        assert(fails.get() == 0)
    }

    @Test
    fun simpleTest2() = runBlocking<Unit> {
        val fails = AtomicInteger(0)
        val flux = MutableSharedFlux<String>()

        launch(Dispatchers.Default) {
            flux.subscribe()
        }

        withContext(Dispatchers.Default) {
            massiveRun {
                if (!flux.tryEmit("hey")) fails.incrementAndGet()
            }
        }

        println("------ fails:${fails.get()} ------")
        assert(fails.get() != 0)
    }

    @Test
    fun testBusyLoopEmit() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)

        launch(Dispatchers.Default) {
            flux.subscribe {
                emitted.incrementAndGet()
            }
        }

        assertFails {
            try {
                withContext(Dispatchers.Default) {
                    massiveRun {
                        flux.emit("hey")
                    }
                }
            } catch (e: Throwable) {
                println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
                throw e
            }
        }
    }

    @Test
    fun testBusyLoopWithDelayEmit() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)

        launch(Dispatchers.Default) {
            flux.subscribe {
                emitted.incrementAndGet()
            }
        }
        try {
            withContext(Dispatchers.Default) {
                massiveRun {
                    flux.emitWithDelay("hey")
                }
            }
        } catch (e: Throwable) {
            println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
            throw e
        }
        assert(emitted.get() == 100_000)
    }

    @Test
    fun stressTestBusyLoopWithDelayEmit() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)
        val verifier = AtomicInteger(0)
        val step = 100_000

        launch(Dispatchers.Default) {
            flux.subscribe {
                emitted.incrementAndGet()
            }
        }
        repeat(1000) {
            try {
                withContext(Dispatchers.Default) {
                    massiveRun {
                        flux.emitWithDelay("hey")
                    }
                }
            } catch (e: Throwable) {
                println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
                throw e
            }
            verifier.compareAndSet(verifier.get(), verifier.get() + step)
            assert(emitted.get() == verifier.get())
        }
    }

    @Test
    fun testLockedEmit() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)

        launch(Dispatchers.Default) {
            flux.subscribe {
                emitted.incrementAndGet()
            }
        }
        try {
            withContext(Dispatchers.Default) {
                massiveRun {
                    flux.emitWithLock("hey")
                }
            }
        } catch (e: Throwable) {
            println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
            throw e
        }
        assert(emitted.get() == 100_000)
    }

    @Test
    fun stressTestLockedEmit() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)

        launch(Dispatchers.Default) {
            flux.subscribe {
                emitted.incrementAndGet()
            }
        }
        repeat(1000) {
            try {
                withContext(Dispatchers.Default) {
                    massiveRun {
                        flux.emitWithLock("hey")
                    }
                }
            } catch (e: Throwable) {
                println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
                throw e
            }
        }
        println(emitted.get())
    }

    @Test
    fun stressTestBusyLoopWithDelayEmitAndMultipleSubscribers() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)
        val collectors = 50_000

        for (i in 0..<collectors) {
            launch(Dispatchers.Default) {
                flux.subscribe {
                    emitted.incrementAndGet()
                }
            }
        }

        repeat(10) {
            try {
                withContext(Dispatchers.Default) {
                    massiveRun {
                        flux.emitWithDelay("hey")
                    }
                }
            } catch (e: Throwable) {
                println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
                throw e
            }
        }
    }

    // 42749 ms
    // 49133 ms
    @Test
    fun stressTestLockedEmitAndMultipleSubscribers() = runBlocking<Unit> {
        val flux = MutableSharedFlux<String>()
        val emitted = AtomicInteger(0)
        val collectors = 50_000

        for (i in 0..<collectors) {
            launch(Dispatchers.Default) {
                flux.subscribe {
                    emitted.incrementAndGet()
                }
            }
        }

        repeat(10) {
            try {
                withContext(Dispatchers.Default) {
                    massiveRun {
                        flux.emitWithLock("hey")
                    }
                }
            } catch (e: Throwable) {
                println("stats after throwing:${flux.statistics} and emitted:${emitted.get()}")
                throw e
            }
        }
        println("emitted with div:${emitted.get()}")
    }

    // 7103 ms
    // 6776 ms
    @Test
    fun stressTestSharedFlow() = runBlocking {
        val flow = MutableSharedFlow<String>(
            extraBufferCapacity = 10_000,
            onBufferOverflow = BufferOverflow.DROP_LATEST
        )
        val emitted = AtomicInteger(0)
        val collectors = 50_000
        val jobs = mutableListOf<Job>()
        for (i in 0..<collectors) {
            val job = launch(Dispatchers.Default) {
                flow.collect {
                    emitted.incrementAndGet()
                }
            }
            jobs.add(job)
        }

        repeat(10) {
            try {
                withContext(Dispatchers.Default) {
                    massiveRun {
                        flow.emit("hey")
                    }
                }
            } catch (e: Throwable) {
                println("stats after throwing emitted:${emitted.get()}")
                throw e
            }
        }
        println("emitted with div:${emitted.get()}")
        jobs.forEach { it.cancelAndJoin() }
    }

    private suspend fun massiveRun(action: suspend () -> Unit) {
        val n = 100  // number of coroutines to launch
        val k = 1000 // times an action is repeated by each coroutine
        val time = measureTimeMillis {
            coroutineScope { // scope for coroutines
                repeat(n) {
                    launch {
                        repeat(k) { action() }
                    }
                }
            }
        }
        println("Completed ${n * k} actions in $time ms")
    }
}
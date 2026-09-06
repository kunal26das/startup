package io.github.kunal26das.startup

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AndroidX serializes every initialization inside one lock, so the same shared code has to
 * be safe to drive from several threads off Android too. Without a lock this test sees
 * more creations than components and hands different threads different instances.
 */
class StartupConcurrencyTest {

    /** Starts every test from an empty count. */
    @BeforeTest
    fun reset() {
        ConcurrentCounter.reset()
    }

    /** Eight threads asking for two components create each of them exactly once. */
    @Test
    fun createsEachComponentOnceWhenManyThreadsAsk() {
        val appInitializer = AppInitializer(DefaultContext)
        appInitializer.engine.install(
            StartupManifest {
                lazyInitializer<ConcurrentAInitializer> { ConcurrentAInitializer() }
                lazyInitializer<ConcurrentBInitializer> { ConcurrentBInitializer() }
            },
        )
        val gate = CountDownLatch(1)
        val components = ConcurrentLinkedQueue<Any>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val workers = List(THREADS) {
            Thread {
                try {
                    gate.await()
                    components.add(
                        appInitializer.initializeComponent(initializerKey<ConcurrentAInitializer>()),
                    )
                    components.add(
                        appInitializer.initializeComponent(initializerKey<ConcurrentBInitializer>()),
                    )
                } catch (throwable: Throwable) {
                    failures.add(throwable)
                }
            }
        }
        workers.forEach(Thread::start)
        gate.countDown()
        workers.forEach(Thread::join)
        assertEquals(emptyList(), failures.toList())
        assertEquals(2, ConcurrentCounter.created)
        assertEquals(THREADS * 2, components.size)
        assertEquals(2, components.toSet().size)
    }

    private companion object {

        private const val THREADS = 8
    }
}

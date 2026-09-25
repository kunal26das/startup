package io.github.kunal26das.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Drives the Kotlin/Native [StartupLock] from real threads. The desktop suite asks the same
 * questions of the JVM lock, which is `java.util.concurrent.locks.ReentrantLock`; this one is
 * a pthread mutex of the library's own, and nothing else runs it under contention.
 */
class StartupLockTest {

    /** Starts every test from an empty log and a fresh probe. */
    @BeforeTest
    fun reset() {
        TestLog.clear()
        LockProbe.reset()
    }

    /** Eight threads asking for two components create each of them exactly once. */
    @Test
    fun createsEachComponentOnceWhenManyThreadsAsk() {
        val appInitializer = AppInitializer(DefaultContext)
        appInitializer.engine.install(
            StartupManifest {
                lazyInitializer<AlphaInitializer> { AlphaInitializer() }
                lazyInitializer<BetaInitializer> { BetaInitializer() }
            },
        )
        val products = runBlocking {
            List(THREADS) {
                async(Dispatchers.Default) {
                    listOf(
                        appInitializer.initializeComponent(initializerKey<BetaInitializer>()),
                        appInitializer.initializeComponent(initializerKey<AlphaInitializer>()),
                    )
                }
            }.awaitAll().flatten()
        }
        assertEquals(listOf("alpha", "beta"), TestLog.created)
        assertEquals(THREADS * 2, products.size)
    }

    /** A wave task that resolves a component from another thread is refused, not left waiting. */
    @Test
    fun refusesAReentrantCallFromAWaveTaskOnAnotherThread() {
        val appInitializer = AppInitializer(DefaultContext)
        LockProbe.appInitializer = appInitializer
        appInitializer.engine.install(
            StartupManifest {
                metaData<LockReentrantInitializer> { LockReentrantInitializer() }
                lazyInitializer<AlphaInitializer> { AlphaInitializer() }
            },
        ) { wave ->
            runBlocking { wave.map { task -> async(Dispatchers.Default) { task() } }.awaitAll() }
        }
        assertContains(LockProbe.outcome.value.orEmpty(), "running a WaveRunner task")
        assertEquals(emptyList(), TestLog.created)
    }

    /**
     * A thread that is running no task waits for the install and is then served. The holding
     * component does not return until that thread has asked, so the wait is on the lock.
     */
    @Test
    fun servesAThreadThatWaitedForTheInstall() {
        val appInitializer = AppInitializer(DefaultContext)
        val waiter = CoroutineScope(Dispatchers.Default).async {
            while (LockProbe.holding.value == 0) delay(1)
            LockProbe.waiting.value = 1
            appInitializer.initializeComponent(initializerKey<AlphaInitializer>())
        }
        appInitializer.engine.install(
            StartupManifest {
                metaData<LockHoldingInitializer> { LockHoldingInitializer() }
                lazyInitializer<AlphaInitializer> { AlphaInitializer() }
            },
        )
        assertEquals("alpha", runBlocking { withTimeout(TIMEOUT_MILLIS) { waiter.await() } })
        assertEquals(listOf(LockHoldingInitializer.NAME, "alpha"), TestLog.created)
    }

    private companion object {

        private const val THREADS = 8

        private const val TIMEOUT_MILLIS = 10_000L
    }
}

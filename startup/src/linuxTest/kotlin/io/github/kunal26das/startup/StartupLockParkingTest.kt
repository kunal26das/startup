@file:OptIn(ExperimentalForeignApi::class)

package io.github.kunal26das.startup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.posix.CLOCK_THREAD_CPUTIME_ID
import platform.posix.clock_gettime
import platform.posix.timespec
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * A thread waiting for the engine lock sleeps instead of spinning. The Kotlin/Native lock used
 * to be a compare-and-set loop over `sched_yield`, which burned most of a core for as long as
 * the install it waited for ran. Every functional test passes either way; only the waiting
 * thread's own CPU time tells the two apart. This reads it through `CLOCK_THREAD_CPUTIME_ID`,
 * whose cinterop spelling is Linux's own, and CI runs it on `ubuntu-latest`.
 */
class StartupLockParkingTest {

    /** Starts every test from an empty log and a fresh probe. */
    @BeforeTest
    fun reset() {
        TestLog.clear()
        LockProbe.reset()
    }

    /** Waiting half a second for an install costs the waiting thread almost no CPU time. */
    @Test
    fun aWaitingThreadParksInsteadOfSpinning() {
        LockProbe.holdMillis = HOLD_MILLIS
        val appInitializer = AppInitializer(DefaultContext)
        val waiter = CoroutineScope(Dispatchers.Default).async {
            while (LockProbe.holding.value == 0) delay(1)
            LockProbe.waiting.value = 1
            val cpuBefore = threadCpuNanos()
            val waitStart = TimeSource.Monotonic.markNow()
            appInitializer.initializeComponent(initializerKey<AlphaInitializer>())
            longArrayOf(threadCpuNanos() - cpuBefore, waitStart.elapsedNow().inWholeNanoseconds)
        }
        appInitializer.engine.install(
            StartupManifest {
                metaData<LockHoldingInitializer> { LockHoldingInitializer() }
                lazyInitializer<AlphaInitializer> { AlphaInitializer() }
            },
        )
        val (cpu, wall) = runBlocking { withTimeout(TIMEOUT_MILLIS) { waiter.await() } }
        assertTrue(wall >= HOLD_MILLIS * NANOS_PER_MILLI / 2, "the waiting thread never waited: $wall ns")
        assertTrue(cpu * 4 < wall, "the waiting thread burned $cpu ns of CPU while waiting $wall ns")
    }

    private fun threadCpuNanos(): Long = memScoped {
        val time = alloc<timespec>()
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, time.ptr)
        time.tv_sec * NANOS_PER_SECOND + time.tv_nsec
    }

    private companion object {

        private const val HOLD_MILLIS = 500L

        private const val TIMEOUT_MILLIS = 10_000L

        private const val NANOS_PER_MILLI = 1_000_000L

        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}

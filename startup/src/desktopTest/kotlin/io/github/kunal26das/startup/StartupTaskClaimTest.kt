package io.github.kunal26das.startup

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A [WaveRunner] that invokes one task twice is a contract violation the engine reports by
 * name. Detecting it with a read-then-write flag only caught the sequential shape, which is
 * the harmless one: two threads entering `invoke` before either finished both passed, and
 * the component was created twice with nothing left to report it.
 */
class StartupTaskClaimTest {

    /** Starts every test from an empty log. */
    @BeforeTest
    fun reset() {
        WaveLog.reset()
    }

    /** Two threads entering the same task at once create the component once. */
    @Test
    fun claimsATaskOnceWhenTwoThreadsEnterItTogether() {
        val entered = AtomicInteger()
        val both = CountDownLatch(2)
        val failure = AtomicReference<Throwable?>(null)
        val task = StartupTask(initializerKey<WaveOkInitializer>()) {
            entered.incrementAndGet()
            both.countDown()
            both.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        List(2) {
            thread(isDaemon = true) {
                try {
                    task()
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                    both.countDown()
                }
            }
        }.forEach { it.join(TIMEOUT_SECONDS * MILLIS_PER_SECOND) }
        assertEquals(1, entered.get())
        val thrown = assertIs<StartupException>(failure.get())
        assertContains(thrown.message.orEmpty(), "twice")
    }

    /** A task a host built for its own runner test runs like any other. */
    @Test
    fun runsATaskAHostBuiltItself() {
        val task = StartupTask(initializerKey<WaveOkInitializer>()) { WaveLog.record(WaveOkInitializer.NAME) }
        task()
        assertEquals(listOf(WaveOkInitializer.NAME), WaveLog.recorded)
        assertEquals("WaveOkInitializer", task.toString())
    }

    private companion object {

        private const val TIMEOUT_SECONDS = 5L
        private const val MILLIS_PER_SECOND = 1000L
    }
}

package io.github.kunal26das.startup

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A second invocation records its refusal while the claimed task is still running. */
class StartupTaskDuplicateRaceTest {

    /** Finishing the claimed body cannot erase a refusal recorded by a second thread. */
    @Test
    fun keepsADuplicateRefusalWhenTheBodySucceedsLater() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val task = StartupTask(initializerKey<AlphaInitializer>()) {
            entered.countDown()
            assertTrue(finish.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
        val worker = thread(isDaemon = true) { task() }
        try {
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertFailsWith<StartupException> { task() }
        } finally {
            finish.countDown()
            worker.join(TIMEOUT_SECONDS * MILLIS_PER_SECOND)
        }
        assertTrue(!worker.isAlive, "the claimed task never finished")
        assertTrue(task.completed)
        assertContains(assertIs<StartupException>(task.failure).message.orEmpty(), "twice")
    }

    /** A refused second call cannot overwrite the initializer's eventual failure. */
    @Test
    fun keepsTheBodyFailureWhenItFailsAfterADuplicateRefusal() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val bodyFailure = IllegalStateException("body failed")
        val caught = AtomicReference<Throwable?>(null)
        val task = StartupTask(initializerKey<AlphaInitializer>()) {
            entered.countDown()
            assertTrue(finish.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            throw bodyFailure
        }
        val worker = thread(isDaemon = true) {
            try {
                task()
            } catch (throwable: Throwable) {
                caught.set(throwable)
            }
        }
        try {
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertFailsWith<StartupException> { task() }
        } finally {
            finish.countDown()
            worker.join(TIMEOUT_SECONDS * MILLIS_PER_SECOND)
        }
        assertTrue(!worker.isAlive, "the claimed task never finished")
        assertSame(bodyFailure, assertIs<StartupException>(caught.get()).cause)
        assertSame(task.wrappedFailure, caught.get())
        assertSame(bodyFailure, task.failure)
    }

    private companion object {

        private const val TIMEOUT_SECONDS = 5L
        private const val MILLIS_PER_SECOND = 1000L
    }
}

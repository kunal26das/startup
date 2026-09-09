package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A task preserves both an initializer's original failure and a refused duplicate call. */
class StartupTaskFailureTest {

    /** Any body failure fits the declared export contract while retaining its original cause. */
    @Test
    fun wrapsAndRecordsTheOriginalBodyFailure() {
        val failure = IllegalStateException("body failed")
        val task = StartupTask(initializerKey<AlphaInitializer>()) { throw failure }
        val thrown = assertFailsWith<StartupException> { task() }
        assertSame(failure, thrown.cause)
        assertSame(failure, task.failure)
        assertSame(thrown, task.wrappedFailure)
        assertEquals(listOf(initializerKey<AlphaInitializer>()), thrown.components)
    }

    /** An existing structured startup failure keeps its own diagnostic and identity. */
    @Test
    fun preservesAnExistingStartupException() {
        val failure = StartupException("already named", null, listOf(initializerKey<AlphaInitializer>()))
        val task = StartupTask(initializerKey<AlphaInitializer>()) { throw failure }
        assertSame(failure, assertFailsWith<StartupException> { task() })
        assertSame(failure, task.failure)
        assertNull(task.wrappedFailure)
    }

    /** Dropping the duplicate exception does not make a successfully created task valid. */
    @Test
    fun recordsADuplicateInvocationAfterSuccess() {
        var created = 0
        val task = StartupTask(initializerKey<AlphaInitializer>()) { created++ }
        task()
        assertFailsWith<StartupException> { task() }
        assertEquals(1, created)
        assertTrue(task.completed)
        val failure = assertIs<StartupException>(task.failure)
        assertContains(failure.message.orEmpty(), "twice")
        assertEquals(listOf(initializerKey<AlphaInitializer>()), failure.components)
    }

    /** A duplicate call must not replace the useful cause from the initializer's body. */
    @Test
    fun keepsTheBodyFailureAfterADuplicateInvocation() {
        val failure = IllegalStateException("body failed")
        val task = StartupTask(initializerKey<AlphaInitializer>()) { throw failure }
        assertFailsWith<StartupException> { task() }
        assertFailsWith<StartupException> { task() }
        assertSame(failure, task.failure)
    }
}

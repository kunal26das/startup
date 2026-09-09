package io.github.kunal26das.startup

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/** Normalizing a task failure must preserve the whole wave's diagnostics. */
class StartupTaskFailureAttributionTest {

    /** Leaves the shared initializer log empty for the next test. */
    @AfterTest
    fun clear() {
        WaveLog.reset()
    }

    /** A runner's first propagated wrapper retains its cause and names every failed task. */
    @Test
    fun attributesEveryFailureWhenTheRunnerPropagatesItsFirstWrapper() {
        val appInitializer = AppInitializer(DefaultContext)
        val manifest = StartupManifest {
            metaData<WaveFailingInitializer> { WaveFailingInitializer() }
            metaData<WaveAlsoFailingInitializer> { WaveAlsoFailingInitializer() }
        }
        var originalFailure: Throwable? = null
        val failure = assertFailsWith<StartupException> {
            appInitializer.engine.install(manifest) { wave ->
                var firstFailure: StartupException? = null
                for (task in wave) {
                    try {
                        task()
                    } catch (throwable: StartupException) {
                        if (firstFailure == null) {
                            firstFailure = throwable
                            originalFailure = task.failure
                        }
                    }
                }
                throw checkNotNull(firstFailure)
            }
        }
        assertSame(originalFailure, failure.cause)
        assertEquals(WaveFailingInitializer.NAME, assertIs<IllegalStateException>(failure.cause).message)
        assertEquals(
            listOf(initializerKey<WaveFailingInitializer>(), initializerKey<WaveAlsoFailingInitializer>()),
            failure.components,
        )
        assertContains(failure.message.orEmpty(), "WaveFailingInitializer")
        assertContains(failure.message.orEmpty(), "WaveAlsoFailingInitializer")
    }
}

package io.github.kunal26das.startup

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What a [WaveRunner] owes the engine, and what the engine owes it back.
 *
 * [WaveRunner.run] can only be trusted to have run a wave if that is checked, and a wave
 * that fails has to leave the same state behind as a sequential step that fails, or a
 * caller that retries pays for the components that already succeeded a second time.
 */
class WaveContractTest {

    /** Starts every test from an empty log. */
    @BeforeTest
    fun reset() {
        WaveLog.reset()
    }

    /**
     * A component that succeeded beside one that failed stays created.
     *
     * The sequential path records each component as it goes, so a failure never costs the
     * ones before it. Recording a whole wave only after the runner returned lost every
     * sibling of a failure, and a caller retrying re-ran them.
     */
    @Test
    fun keepsComponentsThatSucceededBesideOneThatFailed() {
        val appInitializer = AppInitializer(DefaultContext)
        assertFailsWith<StartupException> {
            appInitializer.engine.install(failingWave(), sequentialRunner())
        }
        assertEquals(listOf(WaveOkInitializer.NAME, WaveFailingInitializer.NAME), WaveLog.recorded)
        appInitializer.initializeComponent(initializerKey<WaveOkInitializer>())
        assertEquals(listOf(WaveOkInitializer.NAME, WaveFailingInitializer.NAME), WaveLog.recorded)
    }

    /** The failure names the component that threw rather than the whole wave. */
    @Test
    fun blamesTheComponentThatFailedRatherThanItsWave() {
        val appInitializer = AppInitializer(DefaultContext)
        val exception = assertFailsWith<StartupException> {
            appInitializer.engine.install(failingWave(), sequentialRunner())
        }
        assertContains(exception.message.orEmpty(), "WaveFailingInitializer")
        assertTrue("WaveOkInitializer" !in exception.message.orEmpty())
    }

    /** A runner that never invokes a task is reported, rather than filing a component that is not there. */
    @Test
    fun reportsARunnerThatSkippedATask() {
        val appInitializer = AppInitializer(DefaultContext)
        val exception = assertFailsWith<StartupException> {
            appInitializer.engine.install(singleComponentWave()) { }
        }
        assertContains(exception.message.orEmpty(), "WaveOkInitializer")
        assertContains(exception.message.orEmpty(), "returned before the task finished")
        assertEquals(emptyList(), WaveLog.recorded)
    }

    /** A runner that catches a task's failure and returns anyway is reported with that failure as the cause. */
    @Test
    fun reportsARunnerThatSwallowedAFailure() {
        val appInitializer = AppInitializer(DefaultContext)
        val exception = assertFailsWith<StartupException> {
            appInitializer.engine.install(singleFailingWave()) { wave ->
                wave.forEach { task ->
                    try {
                        task()
                    } catch (throwable: Throwable) {
                        throwable.message
                    }
                }
            }
        }
        assertContains(exception.message.orEmpty(), "WaveFailingInitializer")
        assertEquals(WaveFailingInitializer.NAME, exception.cause?.message)
    }

    /** A runner that invokes the same task twice is reported, rather than creating a component twice. */
    @Test
    fun reportsARunnerThatRanATaskTwice() {
        val appInitializer = AppInitializer(DefaultContext)
        val exception = assertFailsWith<StartupException> {
            appInitializer.engine.install(singleComponentWave()) { wave ->
                wave.forEach { it() }
                wave.forEach { it() }
            }
        }
        assertContains(exception.message.orEmpty(), "twice")
        assertEquals(listOf(WaveOkInitializer.NAME), WaveLog.recorded)
    }

    /** A task names the component it will create, which is what lets a runner route a wave. */
    @Test
    fun namesTheComponentEachTaskWillCreate() {
        val appInitializer = AppInitializer(DefaultContext)
        val named = mutableListOf<String>()
        appInitializer.engine.install(singleComponentWave()) { wave ->
            wave.forEach { task ->
                named.add(task.toString())
                assertEquals(initializerKey<WaveOkInitializer>(), task.component)
                task()
            }
        }
        assertEquals(listOf("WaveOkInitializer"), named)
    }

    /**
     * Two failures in one wave name two components, in the message and in
     * [StartupException.components] alike.
     *
     * The message is rendered from every task that recorded a failure, so attaching only the
     * first left a caller reading `components` a shorter list than the sentence beside it.
     */
    @Test
    fun blamesEveryComponentOfAWaveThatFailed() {
        val appInitializer = AppInitializer(DefaultContext)
        val exception = assertFailsWith<StartupException> {
            appInitializer.engine.install(twoFailingWave()) { wave ->
                wave.forEach { task ->
                    try {
                        task()
                    } catch (throwable: Throwable) {
                        throwable.message
                    }
                }
            }
        }
        assertContains(exception.message.orEmpty(), "WaveFailingInitializer")
        assertContains(exception.message.orEmpty(), "WaveAlsoFailingInitializer")
        assertEquals(
            listOf(
                initializerKey<WaveFailingInitializer>(),
                initializerKey<WaveAlsoFailingInitializer>(),
            ),
            exception.components,
        )
    }

    private fun twoFailingWave() = StartupManifest {
        metaData<WaveFailingInitializer> { WaveFailingInitializer() }
        metaData<WaveAlsoFailingInitializer> { WaveAlsoFailingInitializer() }
    }

    private fun failingWave() = StartupManifest {
        metaData<WaveOkInitializer> { WaveOkInitializer() }
        metaData<WaveFailingInitializer> { WaveFailingInitializer() }
    }

    private fun singleComponentWave() = StartupManifest {
        metaData<WaveOkInitializer> { WaveOkInitializer() }
    }

    private fun singleFailingWave() = StartupManifest {
        metaData<WaveFailingInitializer> { WaveFailingInitializer() }
    }

    /**
     * Runs a whole wave on the calling thread, letting the first failure out only once
     * every other task has had its turn. A runner that stopped at the first throw would
     * hide the very state this test is about.
     */
    private fun sequentialRunner() = WaveRunner { wave ->
        var failure: Throwable? = null
        for (task in wave) {
            try {
                task()
            } catch (throwable: Throwable) {
                if (failure == null) failure = throwable
            }
        }
        failure?.let { throw it }
    }
}

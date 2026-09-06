package io.github.kunal26das.startup

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A dependency edge that does not wait is not a dependency edge.
 *
 * An [Initializer] whose real work is a `suspend` call has nowhere to await inside
 * `create`, so the idiomatic escape is to launch the work and return — and then the graph
 * orders the launches rather than the completions, which is not what
 * [Initializer.dependencies] promises. [CoroutineInitializer] is what closes that.
 */
class CoroutineInitializerTest {

    /** Starts every test from an empty log. */
    @BeforeTest
    fun reset() {
        WaveLog.reset()
    }

    /** A dependent starts only after its suspending dependency has finished, not after it started. */
    @Test
    fun createsACoroutineDependencyToCompletionBeforeItsDependent() {
        val appInitializer = AppInitializer(DefaultContext)
        appInitializer.engine.install(
            StartupManifest {
                metaData<CoroutineSlowInitializer> { CoroutineSlowInitializer() }
                metaData<CoroutineDependentInitializer> { CoroutineDependentInitializer() }
            },
        )
        assertEquals(
            listOf(
                CoroutineSlowInitializer.STARTED,
                CoroutineSlowInitializer.FINISHED,
                CoroutineDependentInitializer.NAME,
            ),
            WaveLog.recorded,
        )
    }

    /** The component a suspending initializer produced is cached like any other. */
    @Test
    fun cachesWhatACoroutineInitializerProduced() {
        val appInitializer = AppInitializer(DefaultContext)
        appInitializer.engine.install(
            StartupManifest {
                lazyInitializer<CoroutineSlowInitializer> { CoroutineSlowInitializer() }
            },
        )
        val first = appInitializer.initializeComponent(initializerKey<CoroutineSlowInitializer>())
        val second = appInitializer.initializeComponent(initializerKey<CoroutineSlowInitializer>())
        assertEquals(first, second)
        assertEquals(
            listOf(CoroutineSlowInitializer.STARTED, CoroutineSlowInitializer.FINISHED),
            WaveLog.recorded,
        )
    }
}

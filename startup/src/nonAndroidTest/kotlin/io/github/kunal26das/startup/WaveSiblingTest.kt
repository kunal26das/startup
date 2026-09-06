package io.github.kunal26das.startup

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A component of the wave being run is in flight without being a cycle.
 *
 * The engine marks a whole wave as in flight before handing it over, because nothing it
 * created can be written back until [WaveRunner.run] returns. Reading that state through the
 * same guard a nested [Initializer.create] trips reported a sibling as a cycle and rendered
 * a path — `Alpha -> WaveSibling -> Alpha` — whose every edge is invented, since components
 * that share a wave never declare one another.
 */
class WaveSiblingTest {

    /** Starts every test from an empty process, an empty log and no recorded refusal. */
    @BeforeTest
    fun reset() {
        Startup.reset()
        TestLog.clear()
        WaveSiblingInitializer.refusal = null
        WaveDependentSiblingInitializer.refusal = null
    }

    /** The refusal names the real limit, and never claims a cycle. */
    @Test
    fun refusesASiblingOfTheWaveBeingRunWithoutCallingItACycle() {
        Startup.install(DefaultContext, siblings()) { wave -> wave.forEach { it() } }
        assertEquals(listOf("alpha", WaveSiblingInitializer.REFUSED), TestLog.created)
        val refusal = assertIs<StartupException>(WaveSiblingInitializer.refusal)
        assertContains(refusal.message.orEmpty(), "member of the wave currently being run")
        assertContains(refusal.message.orEmpty(), "dependencies()")
        assertTrue(
            "Cycle detected" !in refusal.message.orEmpty(),
            "a wave sibling is not a cycle, but the refusal called it one: ${refusal.message}",
        )
        assertEquals(listOf(initializerKey<AlphaInitializer>()), refusal.components)
    }

    /** The same manifest with no runner serves the call, as it always did. */
    @Test
    fun servesTheSameCallWhenNoRunnerIsInvolved() {
        Startup.install(DefaultContext, siblings())
        assertEquals(listOf("alpha", WaveSiblingInitializer.RESOLVED), TestLog.created)
        assertNull(WaveSiblingInitializer.refusal)
    }

    /**
     * A component of an *earlier* wave is served under a runner, because by then it really
     * has been written back. This is what keeps the refusal above narrow: it is the wave in
     * flight that cannot be read, not every component a runner ever touched.
     */
    @Test
    fun servesAComponentAnEarlierWaveAlreadyCreated() {
        Startup.install(DefaultContext, dependent()) { wave -> wave.forEach { it() } }
        assertEquals(listOf("alpha", WaveDependentSiblingInitializer.RESOLVED), TestLog.created)
        assertNull(WaveDependentSiblingInitializer.refusal)
    }

    /**
     * A component that asks for itself inside a wave is refused the same way, and never with
     * a rendered path. `creating` holds the whole wave under a runner rather than a nesting
     * stack, so the walk that serves the sequential path printed `Self -> Alpha -> Self` here,
     * naming an edge between two components that share a wave and declare nothing of each
     * other. Without a runner it is still `Cycle detected: Self -> Self`, which
     * `StartupRuntimeTest.rejectsAComponentThatAsksForItself` pins.
     */
    @Test
    fun refusesAComponentThatAsksForItselfInsideAWaveWithoutInventingAPath() {
        val manifest = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            metaData<SelfCallingInitializer> { SelfCallingInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest) { wave -> wave.forEach { it() } }
        }
        val self = componentName(initializerKey<SelfCallingInitializer>())
        assertContains(exception.message.orEmpty(), "Cannot initialize $self.")
        assertContains(exception.message.orEmpty(), "own create asking for itself")
        assertTrue(
            componentName(initializerKey<AlphaInitializer>()) !in exception.message.orEmpty(),
            "the refusal named a component that shares a wave but no edge: ${exception.message}",
        )
    }

    private fun siblings() = StartupManifest {
        metaData<AlphaInitializer> { AlphaInitializer() }
        metaData<WaveSiblingInitializer> { WaveSiblingInitializer() }
    }

    private fun dependent() = StartupManifest {
        metaData<AlphaInitializer> { AlphaInitializer() }
        metaData<WaveDependentSiblingInitializer> { WaveDependentSiblingInitializer() }
    }
}

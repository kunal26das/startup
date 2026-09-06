package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.StartupPlanner
import io.github.kunal26das.startup.initializerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared manifest's declared edges really do separate its components into waves, which is
 * what makes it installable under a `WaveRunner`: a task may read back only what an earlier
 * wave created.
 *
 * This asserts on a [StartupPlanner] plan rather than on an install, and that is deliberate.
 * `Startup` is a process singleton and every test in this module shares it, so by the time any
 * one of them runs the graph is usually already built; an install here would plan nothing, hand
 * a runner zero waves, and pass whatever the engine did. A plan is a pure function of the
 * manifest, so it says the same thing however the suite is ordered. `:startup`'s own
 * `WaveSiblingTest` is where the runner behaviour itself is pinned, against an isolated engine.
 */
class SampleWavePlanTest {

    /** Every dependency these components declare is planned into a strictly earlier wave. */
    @Test
    fun everyDeclaredDependencyLandsInAnEarlierWave() {
        val plan = StartupPlanner.plan(
            SampleStartup.manifest,
            SampleStartup.manifest.eagerComponents,
            emptySet(),
        )
        val waveOf = mutableMapOf<AnyInitializerKey, Int>()
        plan.waves.forEachIndexed { index, wave -> wave.forEach { waveOf[it] = index } }
        assertEquals(emptyList(), everyComponent().filter { it !in waveOf }, "missing from $waveOf")
        for ((component, dependencies) in declaredEdges()) {
            for (dependency in dependencies) {
                assertTrue(
                    waveOf.getValue(dependency) < waveOf.getValue(component),
                    "$dependency is not in an earlier wave than $component: $waveOf",
                )
            }
        }
    }

    private fun everyComponent(): List<AnyInitializerKey> = listOf(
        initializerKey<AnalyticsInitializer>(),
        initializerKey<CrashReportingInitializer>(),
        initializerKey<RuntimeInfoInitializer>(),
        initializerKey<NetworkInitializer>(),
        initializerKey<LoggerInitializer>(),
    )

    /**
     * What each component says it needs, read from the components themselves rather than
     * restated here, so that dropping a `dependencies()` override is visible to this test
     * only as the wave it moves the component into.
     */
    private fun declaredEdges(): Map<AnyInitializerKey, List<AnyInitializerKey>> = mapOf(
        initializerKey<AnalyticsInitializer>() to AnalyticsInitializer().dependencies(),
        initializerKey<CrashReportingInitializer>() to CrashReportingInitializer().dependencies(),
        initializerKey<RuntimeInfoInitializer>() to RuntimeInfoInitializer().dependencies(),
        initializerKey<NetworkInitializer>() to NetworkInitializer().dependencies(),
        initializerKey<LoggerInitializer>() to LoggerInitializer().dependencies(),
    )
}

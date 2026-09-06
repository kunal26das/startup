package io.github.kunal26das.startup.sample

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the report every non-Android entry point prints, on whichever target is executing.
 *
 * The assertions are the three things a human reads the report for: what was created and
 * in what order, which platform SDK started, and what the log looked like afterwards.
 * They are stable under repetition because a component is created once per process, so
 * only the tracked event accumulates.
 */
class SampleReportTest {

    /** The numbered section lists every component in the order the planner created it. */
    @Test
    fun theReportNumbersTheInitializationOrder() {
        assertEquals(
            listOf("logger ready", "crash reporting ready", "network ready", "analytics ready"),
            SampleLauncher.report().mapNotNull { ORDER.matchEntire(it)?.groupValues?.get(1) },
        )
    }

    /** The report names the SDK this target's `actual` initializer started. */
    @Test
    fun theReportNamesThePlatformSdk() {
        assertEquals(true, SampleLauncher.report().contains("  ${Platform.crashReportingSdk}"))
    }

    /** The report names the runtime that planned and created the graph. */
    @Test
    fun theReportNamesTheRuntime() {
        assertEquals(true, SampleLauncher.report().contains("  io.github.kunal26das.startup"))
    }

    /** The report ends with what the tracked event wrote to the shared logger. */
    @Test
    fun theReportEndsWithTheTrackedEvent() {
        assertEquals(
            listOf("  track launch", "  GET /events/launch"),
            SampleLauncher.report().takeLast(2),
        )
    }

    private companion object {
        private val ORDER = Regex("  \\d+  (.+)")
    }
}

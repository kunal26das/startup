package io.github.kunal26das.startup.sample

import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the shared bootstrap on a non-Android target and checks what it built. */
class SampleStartupTest {

    /**
     * The eager component and both of its dependencies exist, in dependency order.
     *
     * The assertion is on relative order rather than on the whole log, because
     * `Startup` is a process singleton: every test in this module shares one [Logger],
     * so any other test that logs would break an exact comparison.
     */
    @Test
    fun bootstrapBuildsTheWholeGraph() {
        val analytics = SampleLauncher.launch()
        analytics.track("launch")
        val messages = analytics.logger.messages
        val expected = listOf("logger ready", "track launch", "GET /events/launch")
        val indices = expected.map { messages.indexOf(it) }
        assertEquals(emptyList(), indices.filter { it < 0 }, "missing from $messages")
        assertEquals(indices.sorted(), indices, "out of order: $messages")
    }
}

package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/** The task keeps its existing declared exception type for Java and Swift callers. */
class StartupTaskExceptionExportTest {

    /**
     * JVM exception metadata exposes the same common annotation Native uses for Swift.
     * Widening it to Throwable would introduce a checked exception for existing Java
     * callers, so task bodies are normalized to the declared type instead.
     */
    @Test
    fun preservesTheDeclaredStartupExceptionContract() {
        val invoke = StartupTask::class.java.getDeclaredMethod("invoke")
        assertEquals(listOf(StartupException::class.java), invoke.exceptionTypes.toList())
    }
}

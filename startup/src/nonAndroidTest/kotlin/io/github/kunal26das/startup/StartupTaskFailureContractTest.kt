package io.github.kunal26das.startup

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** A runner that catches a task refusal must still leave the install failed. */
class StartupTaskFailureContractTest {

    /** Leaves the shared initializer log empty for the next test. */
    @AfterTest
    fun clear() {
        TestLog.clear()
    }

    /** Swift runners cannot rethrow, so the engine must notice a swallowed duplicate. */
    @Test
    fun reportsASwallowedDuplicateInvocation() {
        val appInitializer = AppInitializer(DefaultContext)
        val manifest = StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } }
        val failure = assertFailsWith<StartupException> {
            appInitializer.engine.install(manifest) { wave ->
                wave.forEach { task ->
                    task()
                    assertFailsWith<StartupException> { task() }
                }
            }
        }
        assertContains(assertIs<StartupException>(failure.cause).message.orEmpty(), "twice")
        assertEquals(listOf(initializerKey<AlphaInitializer>()), failure.components)
    }
}

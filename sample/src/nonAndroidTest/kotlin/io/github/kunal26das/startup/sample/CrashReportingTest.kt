package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.DefaultContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the `expect`/`actual` initializer on whichever target is running, so every
 * platform's `actual` is executed rather than merely compiled.
 */
class CrashReportingTest {

    /** The platform SDK is started and reported through the shared component. */
    @Test
    fun theEagerBootstrapStartsThePlatformSdk() {
        SampleStartup.bootstrap(DefaultContext)
        assertEquals(Platform.crashReportingSdk, SampleStartup.crashReporting(DefaultContext).sdk)
    }

    /** Every target resolves to a real SDK name, so no `actual` returns a placeholder. */
    @Test
    fun thePlatformNamesAnSdk() {
        assertEquals("CrashReporter", Platform.crashReportingSdk.takeLast("CrashReporter".length))
    }

    /** The platform initializer shares the logger created by the common one. */
    @Test
    fun thePlatformInitializerReusesTheSharedLogger() {
        SampleStartup.bootstrap(DefaultContext)
        val crashReporting = SampleStartup.crashReporting(DefaultContext)
        val logger = SampleStartup.analytics(DefaultContext).logger
        crashReporting.report("boom")
        assertEquals(
            listOf("crash boom via ${Platform.crashReportingSdk}"),
            logger.messages.filter { it.startsWith("crash boom") },
        )
    }
}

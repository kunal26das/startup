package io.github.kunal26das.startup.sample

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Android call sites in [AndroidSampleStartup].
 *
 * Their value is that they compile: they reach shared initializers through the verbatim
 * AndroidX spelling, against `android.content.Context` and AndroidX's own class tokens.
 * Running them needs a real context, so the signatures are asserted instead.
 */
class AndroidSampleStartupTest {

    /** The analytics call site takes a real Android context and returns the shared type. */
    @Test
    fun analyticsTakesAnAndroidContext() {
        val method = AndroidSampleStartup::class.java.getDeclaredMethod("analytics", Context::class.java)
        assertEquals(Analytics::class.java, method.returnType)
    }

    /** The eagerness call site delegates to AndroidX and answers with a boolean. */
    @Test
    fun isEagerTakesAnAndroidContext() {
        val method = AndroidSampleStartup::class.java.getDeclaredMethod("isEager", Context::class.java)
        assertEquals(Boolean::class.javaPrimitiveType, method.returnType)
    }

    /** A key on Android is a class token, so the shared manifest already names its components fully. */
    @Test
    fun theEagerComponentsAreNamedFully() {
        assertEquals(
            listOf(
                "io.github.kunal26das.startup.sample.AnalyticsInitializer",
                "io.github.kunal26das.startup.sample.CrashReportingInitializer",
                "io.github.kunal26das.startup.sample.RuntimeInfoInitializer",
            ),
            SampleStartup.manifest.eagerComponents.map { it.name },
        )
    }
}

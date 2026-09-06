package io.github.kunal26das.startup.sample

import androidx.startup.Initializer

import kotlin.test.Test
import kotlin.test.assertEquals
import java.lang.reflect.Modifier

/**
 * Pins the Android `actual` against what AndroidX actually requires of it.
 *
 * These are the two ways a platform-specific initializer breaks on Android only: the
 * `actual` stops being an `androidx.startup.Initializer`, or it loses the public
 * no-argument constructor that `InitializationProvider` reflects on. Both compile
 * everywhere and fail at process start.
 */
class CrashReportingContractTest {

    /** The Android `actual` is an AndroidX initializer, so the provider can use it. */
    @Test
    fun theActualIsAnAndroidxInitializer() {
        assertEquals(
            true,
            Initializer::class.java.isAssignableFrom(CrashReportingInitializer::class.java),
        )
    }

    /** AndroidX instantiates it with `getDeclaredConstructor().newInstance()`. */
    @Test
    fun theActualHasAPublicNoArgumentConstructor() {
        val constructor = CrashReportingInitializer::class.java.getDeclaredConstructor()
        assertEquals(true, Modifier.isPublic(constructor.modifiers))
        assertEquals(true, constructor.newInstance() is Initializer<*>)
    }

    /** Its dependency list carries the AndroidX signature, not a wrapper type. */
    @Test
    fun theDependenciesSignatureIsTheAndroidxOne() {
        assertEquals(
            "java.util.List<java.lang.Class<? extends androidx.startup.Initializer<?>>>",
            CrashReportingInitializer::class.java
                .getDeclaredMethod("dependencies").genericReturnType.toString(),
        )
    }
}

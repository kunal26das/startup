package io.github.kunal26das.startup.sample

import androidx.startup.Initializer
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Android `actual` of the `expect` over `BaseInitializer` shape.
 *
 * [CrashReportingContractTest] pins the same contract for the shape that extends
 * [io.github.kunal26das.startup.Initializer] and redeclares both members. This one covers
 * the shape README recommends by default, where `dependencies()` is inherited from
 * `BaseInitializer` rather than written out, because that is the difference AndroidX's
 * reflection could notice.
 */
class RuntimeInfoContractTest {

    /** Inheriting `dependencies()` still compiles to an AndroidX initializer. */
    @Test
    fun theActualIsAnAndroidxInitializer() {
        assertEquals(
            true,
            Initializer::class.java.isAssignableFrom(RuntimeInfoInitializer::class.java),
        )
    }

    /** AndroidX instantiates it with `getDeclaredConstructor().newInstance()`. */
    @Test
    fun theActualHasAPublicNoArgumentConstructor() {
        val constructor = RuntimeInfoInitializer::class.java.getDeclaredConstructor()
        assertEquals(true, Modifier.isPublic(constructor.modifiers))
        assertEquals(true, constructor.newInstance() is Initializer<*>)
    }

    /** The inherited dependency list carries AndroidX's signature, not a wrapper type. */
    @Test
    fun theInheritedDependenciesSignatureIsTheAndroidxOne() {
        assertEquals(
            "java.util.List<java.lang.Class<? extends androidx.startup.Initializer<?>>>",
            RuntimeInfoInitializer::class.java
                .getMethod("dependencies").genericReturnType.toString(),
        )
        assertEquals(emptyList(), RuntimeInfoInitializer().dependencies())
    }
}

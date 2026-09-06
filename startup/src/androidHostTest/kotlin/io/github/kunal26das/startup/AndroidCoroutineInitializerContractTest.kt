package io.github.kunal26das.startup

import android.content.ContextWrapper
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the Android bytecode contract for [CoroutineInitializer].
 *
 * A suspending component is the one shape whose `create` nobody writes: it is inherited
 * from a Kotlin interface that extends a Java one, and `androidx.startup.AppInitializer`
 * reaches it by building the class with `getDeclaredConstructor().newInstance()` and
 * calling `create` through `androidx.startup.Initializer`. Nothing about that path is
 * checked by compiling — a `create` left abstract on the interface, or one that returned
 * without awaiting, would compile and then fail inside `InitializationProvider`. So this
 * test walks the same path AndroidX walks, on the same bytecode it loads.
 */
class AndroidCoroutineInitializerContractTest {

    /** A suspending initializer is an AndroidX initializer, with nothing in between. */
    @Test
    fun aCoroutineInitializerImplementsTheAndroidxInterface() {
        assertEquals(
            true,
            androidx.startup.Initializer::class.java
                .isAssignableFrom(CoroutineHostInitializer::class.java),
        )
    }

    /**
     * `create` carries a body on the interface itself rather than only in a `DefaultImpls`
     * holder. A Kotlin implementation would work either way, because the compiler writes a
     * forwarding method into every class it compiles; an implementation AndroidX loads
     * from anywhere else — Java, or bytecode this compiler never saw — only works when the
     * body is a JVM default method.
     */
    @Test
    fun createIsADefaultMethodOnTheInterfaceItself() {
        val create = CoroutineInitializer::class.java.getDeclaredMethod("create", Context::class.java)
        assertEquals(false, Modifier.isAbstract(create.modifiers))
    }

    /** So is the [Initializer.dependencies] that a leaf implementation never writes. */
    @Test
    fun dependenciesIsADefaultMethodOnTheInterfaceItself() {
        val dependencies = CoroutineInitializer::class.java.getDeclaredMethod("dependencies")
        assertEquals(false, Modifier.isAbstract(dependencies.modifiers))
        assertEquals(
            DEPENDENCIES_SIGNATURE,
            CoroutineHostInitializer::class.java.getMethod("dependencies")
                .genericReturnType.toString(),
        )
    }

    /** AndroidX builds every component reflectively, so the constructor has to be public. */
    @Test
    fun aCoroutineInitializerHasAPublicNoArgumentConstructor() {
        val instance = CoroutineHostInitializer::class.java.getDeclaredConstructor().newInstance()
        assertEquals(true, androidx.startup.Initializer::class.java.isInstance(instance))
    }

    /**
     * The whole contract in one call: AndroidX reads `dependencies`, then invokes `create`
     * through its own interface, and what comes back is what [CoroutineInitializer.createAsync]
     * returned after suspending — from a thread that is not the one AndroidX called on.
     */
    @Test
    fun theAndroidxReflectivePathRunsCreateAsyncToCompletion() {
        val type: Class<*> = CoroutineHostInitializer::class.java
        val instance = type.getDeclaredConstructor().newInstance()
        val dependencies = androidx.startup.Initializer::class.java.getMethod("dependencies")
        assertEquals(emptyList<Any>(), dependencies.invoke(instance))
        val create = androidx.startup.Initializer::class.java.getMethod("create", Context::class.java)
        val product = create.invoke(instance, ContextWrapper(null)) as String
        assertTrue(product.startsWith("${CoroutineHostInitializer.PRODUCT}:"))
        assertNotEquals(
            Thread.currentThread().name,
            product.substringAfter(':'),
        )
    }

    private companion object {

        private const val DEPENDENCIES_SIGNATURE =
            "java.util.List<java.lang.Class<? extends androidx.startup.Initializer<?>>>"
    }
}

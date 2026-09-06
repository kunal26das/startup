package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Android bytecode contract. A component written once in shared code has to be
 * indistinguishable from one written directly against AndroidX, or `InitializationProvider`
 * will not discover it and `AppInitializer` will not be able to build it. Every assertion
 * here would still compile if the typealias layer grew a wrapper type, and every one of
 * them would fail.
 */
class AndroidInitializerContractTest {

    /** A shared initializer implements AndroidX's own interface, with nothing in between. */
    @Test
    fun aSharedInitializerImplementsTheAndroidxInterface() {
        assertEquals(
            true,
            androidx.startup.Initializer::class.java.isAssignableFrom(BetaInitializer::class.java),
        )
    }

    /** Its erased dependencies signature is the one AndroidX declares. */
    @Test
    fun theDependenciesSignatureIsTheAndroidxOne() {
        assertEquals(
            DEPENDENCIES_SIGNATURE,
            BetaInitializer::class.java.getMethod("dependencies").genericReturnType.toString(),
        )
    }

    /** So does the signature [BaseInitializer] hands to everything that extends it. */
    @Test
    fun baseInitializerCarriesTheSameSignature() {
        assertEquals(
            DEPENDENCIES_SIGNATURE,
            BaseInitializer::class.java.getMethod("dependencies").genericReturnType.toString(),
        )
    }

    /** AndroidX builds every component reflectively, so the constructor has to be public. */
    @Test
    fun aSharedInitializerHasAPublicNoArgumentConstructor() {
        val type: Class<*> = AlphaInitializer::class.java
        val instance = type.getDeclaredConstructor().newInstance()
        assertEquals(true, androidx.startup.Initializer::class.java.isInstance(instance))
    }

    /** A key is the very class token AndroidX works with. */
    @Test
    fun aKeyIsTheAndroidxClassToken() {
        assertEquals<Any>(AlphaInitializer::class.java, initializerKey<AlphaInitializer>())
    }

    /**
     * The `expect class` that extends [BaseInitializer] keeps the same contract, which is
     * what makes it a legal replacement for the longer shape that redeclares
     * `dependencies` as well as `create`.
     */
    @Test
    fun aPlatformSpecificInitializerKeepsTheAndroidxContract() {
        val type: Class<*> = PlatformInitializer::class.java
        val instance = type.getDeclaredConstructor().newInstance()
        assertEquals(true, androidx.startup.Initializer::class.java.isInstance(instance))
        assertEquals(
            DEPENDENCIES_SIGNATURE,
            type.getMethod("dependencies").genericReturnType.toString(),
        )
    }

    /**
     * The memberless `expect class`, whose `actual` overrides the inherited `create`
     * without an `actual` modifier, keeps the same contract. The redeclaration is a
     * documentation choice, not something AndroidX reflection depends on.
     */
    @Test
    fun aMemberlessPlatformSpecificInitializerKeepsTheAndroidxContract() {
        val type: Class<*> = MemberlessInitializer::class.java
        val instance = type.getDeclaredConstructor().newInstance()
        assertEquals(true, androidx.startup.Initializer::class.java.isInstance(instance))
        assertEquals(
            DEPENDENCIES_SIGNATURE,
            type.getMethod("dependencies").genericReturnType.toString(),
        )
    }

    /**
     * A key names its class the way `Class.forName` needs it named, which is what every
     * Android diagnostic prints and what makes an AndroidManifest parity test possible at
     * all. Off Android a key can only name its class simply, which is why this assertion
     * lives here.
     */
    @Test
    fun aKeyNamesItsClassFully() {
        assertEquals(
            "io.github.kunal26das.startup.AlphaInitializer",
            componentName(initializerKey<AlphaInitializer>()),
        )
    }

    /** A key built from an instance is the same class token the reified overload emits. */
    @Test
    fun anInstanceKeyIsTheAndroidxClassToken() {
        assertEquals<Any>(AlphaInitializer::class.java, initializerKey(AlphaInitializer()))
    }

    private companion object {

        private const val DEPENDENCIES_SIGNATURE =
            "java.util.List<java.lang.Class<? extends androidx.startup.Initializer<?>>>"
    }
}

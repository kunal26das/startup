package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Keys have to behave as values, because the whole registry is keyed on them. */
class InitializerKeyTest {

    /** Two keys for the same initializer are equal and hash alike. */
    @Test
    fun keysForTheSameInitializerAreEqual() {
        assertEquals(initializerKey<AlphaInitializer>(), initializerKey<AlphaInitializer>())
        assertEquals(
            initializerKey<AlphaInitializer>().hashCode(),
            initializerKey<AlphaInitializer>().hashCode(),
        )
    }

    /** Keys for different initializers are not equal. */
    @Test
    fun keysForDifferentInitializersDiffer() {
        assertNotEquals<AnyInitializerKey>(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
        )
    }

    /** The reified overload and the reflective one produce the same key. */
    @Test
    fun bothOverloadsAgree() {
        assertEquals<AnyInitializerKey>(
            initializerKey<AlphaInitializer>(),
            initializerKey(AlphaInitializer::class),
        )
    }

    /**
     * The instance overload agrees with the other two. This is the only overload Swift
     * and Objective-C can call, so a key built from an instance has to name the same
     * component a Kotlin dependency list names by class.
     */
    @Test
    fun theInstanceOverloadAgrees() {
        assertEquals<AnyInitializerKey>(
            initializerKey<AlphaInitializer>(),
            initializerKey(AlphaInitializer()),
        )
        assertEquals<AnyInitializerKey>(
            initializerKey(AlphaInitializer::class),
            initializerKey(AlphaInitializer()),
        )
        assertNotEquals<AnyInitializerKey>(
            initializerKey(AlphaInitializer()),
            initializerKey(BetaInitializer()),
        )
    }

    /** A key works as a map key, which is what the registry and the planner rely on. */
    @Test
    fun keysWorkAsMapKeys() {
        val byComponent = linkedMapOf<AnyInitializerKey, String>(
            initializerKey<AlphaInitializer>() to "alpha",
        )
        assertEquals("alpha", byComponent[initializerKey<AlphaInitializer>()])
        assertEquals(listOf<AnyInitializerKey>(initializerKey<AlphaInitializer>()), byComponent.keys.toList())
    }

    /** A key names itself, which is what every diagnostic in the library prints. */
    @Test
    fun keysNameThemselves() {
        assertEquals(
            "AlphaInitializer",
            componentName(initializerKey<AlphaInitializer>()).substringAfterLast('.'),
        )
    }
}

package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * A component that produced nothing is a component, and reading it back has to say so.
 *
 * [AppInitializer.initializeComponent] returns a non-null `T`, so the only thing it could
 * do with a null product was fail the cast — a `NullPointerException` raised inside the
 * library, naming neither the component nor the reason, and on Kotlin/Native an abort
 * rather than an exception a Swift caller could catch.
 */
class NullProductTest {

    /** Installing a component that produces nothing succeeds, as it does on Android. */
    @Test
    fun installsAComponentThatProducesNothing() {
        appInitializer().engine.install(manifest())
    }

    /** The read that can represent an absent product answers with it. */
    @Test
    fun readsAnAbsentProductAsNull() {
        val appInitializer = appInitializer()
        appInitializer.engine.install(manifest())
        assertNull(appInitializer.initializeComponentOrNull(initializerKey<NullProductInitializer>()))
    }

    /** The read that cannot names the component and points at the one that can. */
    @Test
    fun namesTheComponentWhenATypedReadCannotRepresentIt() {
        val appInitializer = appInitializer()
        appInitializer.engine.install(manifest())
        val exception = assertFailsWith<StartupException> {
            appInitializer.initializeComponent(typedKey())
        }
        assertContains(exception.message.orEmpty(), "NullProductInitializer")
        assertContains(exception.message.orEmpty(), "initializeComponentOrNull")
    }

    private fun appInitializer() = AppInitializer(DefaultContext)

    private fun manifest() = StartupManifest {
        metaData<NullProductInitializer> { NullProductInitializer() }
    }

    /**
     * The key as the Objective-C export hands it over, where the type argument is erased
     * and nothing stops a component typed `Any?` reaching a read that promises `Any`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun typedKey(): InitializerKey<out Initializer<Any>> =
        initializerKey<NullProductInitializer>() as InitializerKey<out Initializer<Any>>
}

package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the cheaper platform-specific initializer shape: an `expect class` extending
 * [BaseInitializer] redeclares `create` and nothing else, because [BaseInitializer]
 * already carries a concrete `dependencies`. The `expect` in [PlatformInitializer]
 * compiling on all eleven targets is most of the assertion; this checks it behaves.
 */
class PlatformInitializerTest {

    /** The dependency list comes from [BaseInitializer], not from a redeclaration. */
    @Test
    fun theShapeInheritsAnEmptyDependencyList() {
        assertEquals(emptyList(), PlatformInitializer().dependencies())
    }

    /** It has one key on every target, whichever `actual` was compiled in. */
    @Test
    fun theShapeHasOneKeyOnEveryTarget() {
        val manifest = StartupManifest { metaData<PlatformInitializer> { PlatformInitializer() } }
        assertEquals(true, manifest.isEager(initializerKey<PlatformInitializer>()))
        assertEquals<AnyInitializerKey>(
            initializerKey<PlatformInitializer>(),
            initializerKey(PlatformInitializer()),
        )
    }
}

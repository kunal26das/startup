package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the registration overloads that take a key the caller computed instead of one the
 * compiler reified. They are what puts a host-supplied initializer in the graph, and they
 * are the only shape Swift and Objective-C can call at all.
 */
class StartupManifestBuilderTest {

    /** Registering by a runtime key produces the very manifest the reified overloads do. */
    @Test
    fun aRuntimeKeyRegistersTheSameEntry() {
        val reified = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            lazyInitializer<BetaInitializer> { BetaInitializer() }
            remove<GammaInitializer>()
        }
        val runtime = StartupManifest {
            metaData(initializerKey(AlphaInitializer())) { AlphaInitializer() }
            lazyInitializer(initializerKey(BetaInitializer())) { BetaInitializer() }
            remove(initializerKey(GammaInitializer()))
        }
        assertEquals(reified.components, runtime.components)
        assertEquals(reified.eagerComponents, runtime.eagerComponents)
        assertEquals(reified.androidManifestMetadata(), runtime.androidManifestMetadata())
    }

    /** A runtime key and a reified one are one key, so a later entry still overrides. */
    @Test
    fun aRuntimeKeyOverridesAReifiedEntry() {
        val manifest = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            lazyInitializer(initializerKey<AlphaInitializer>()) { AlphaInitializer() }
        }
        assertEquals(false, manifest.isEager(initializerKey<AlphaInitializer>()))
        val expected: List<AnyInitializerKey> = listOf(initializerKey<AlphaInitializer>())
        assertEquals(expected, manifest.components)
    }

    /** A tombstone registered by a runtime key hides an included entry like any other. */
    @Test
    fun aRuntimeTombstoneHidesAnIncludedEntry() {
        val library = StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } }
        val application = StartupManifest {
            include(library)
            remove(initializerKey<AlphaInitializer>())
        }
        assertEquals(false, initializerKey<AlphaInitializer>() in application)
        assertEquals(emptyList(), application.eagerComponents)
    }

    /** The planner sees a runtime-keyed entry exactly as it sees a reified one. */
    @Test
    fun aRuntimeKeyedEntryIsPlannedLikeAnyOther() {
        val supplied: Initializer<*> = RuntimeKeyInitializer()
        val manifest = StartupManifest {
            metaData(initializerKey(supplied)) { supplied }
            lazyInitializer<AlphaInitializer> { AlphaInitializer() }
        }
        val plan = StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<RuntimeKeyInitializer>(),
        )
        assertEquals(expected, plan.order)
    }
}

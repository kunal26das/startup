package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises the registry: eager versus lazy, tombstones and composition. */
class StartupManifestTest {

    private val manifest = StartupManifest {
        metaData<AlphaInitializer> { AlphaInitializer() }
        lazyInitializer<BetaInitializer> { BetaInitializer() }
        remove<GammaInitializer>()
    }

    /** Only a merged entry is eager; a lazily registered one is not. */
    @Test
    fun onlyMergedEntriesAreEager() {
        assertEquals(true, manifest.isEager(initializerKey<AlphaInitializer>()))
        assertEquals(false, manifest.isEager(initializerKey<BetaInitializer>()))
        val expected: List<AnyInitializerKey> = listOf(initializerKey<AlphaInitializer>())
        assertEquals(expected, manifest.eagerComponents)
    }

    /** A tombstone is neither eager nor resolvable, but a lazy entry is still resolvable. */
    @Test
    fun aTombstoneIsNotRegistered() {
        assertEquals(false, manifest.isEager(initializerKey<GammaInitializer>()))
        assertEquals(false, initializerKey<GammaInitializer>() in manifest)
        assertEquals(true, initializerKey<BetaInitializer>() in manifest)
    }

    /** Only resolvable entries are listed as components. */
    @Test
    fun componentsExcludeTombstones() {
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
        )
        assertEquals(expected, manifest.components)
    }

    /** When two manifests disagree about a component, the later one wins. */
    @Test
    fun laterEntriesWin() {
        val eager = StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } }
        val lazy = StartupManifest { lazyInitializer<AlphaInitializer> { AlphaInitializer() } }
        assertEquals(false, (eager + lazy).isEager(initializerKey<AlphaInitializer>()))
        assertEquals(true, (lazy + eager).isEager(initializerKey<AlphaInitializer>()))
    }

    /** An application can remove an entry a library contributed. */
    @Test
    fun removeHidesAnIncludedEntry() {
        val library = StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } }
        val application = StartupManifest {
            include(library)
            remove<AlphaInitializer>()
        }
        assertEquals(false, initializerKey<AlphaInitializer>() in application)
        assertEquals(emptyList(), application.eagerComponents)
    }

    /** An included manifest contributes its entries, and later declarations override them. */
    @Test
    fun includeComposesManifests() {
        val library = StartupManifest { lazyInitializer<AlphaInitializer> { AlphaInitializer() } }
        val application = StartupManifest {
            include(library)
            metaData<BetaInitializer> { BetaInitializer() }
        }
        assertEquals(true, initializerKey<AlphaInitializer>() in application)
        val expected: List<AnyInitializerKey> = listOf(initializerKey<BetaInitializer>())
        assertEquals(expected, application.eagerComponents)
    }
}

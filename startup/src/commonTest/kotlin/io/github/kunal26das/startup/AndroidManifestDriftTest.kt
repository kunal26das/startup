package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `StartupManifest` and an AndroidManifest are two registries and only Android reads
 * the second one, so a component present in one and missing from the other misbehaves on
 * exactly one platform. These pin what that drift is reported as.
 */
class AndroidManifestDriftTest {

    private val manifest = StartupManifest {
        metaData<AlphaInitializer> { AlphaInitializer() }
        lazyInitializer<BetaInitializer> { BetaInitializer() }
        remove<GammaInitializer>()
    }

    /** Nothing is reported when the AndroidManifest declares exactly the eager components. */
    @Test
    fun agreementReportsNothing() {
        assertEquals(
            emptyList(),
            manifest.androidManifestDrift(setOf(componentName(initializerKey<AlphaInitializer>()))),
        )
    }

    /** An eager component the AndroidManifest omits would silently never run on Android. */
    @Test
    fun reportsAnEagerComponentTheAndroidManifestOmits() {
        val drift = manifest.androidManifestDrift(emptySet())
        assertEquals(1, drift.size)
        assertEquals(
            "${componentName(initializerKey<AlphaInitializer>())} is eager in the StartupManifest " +
                "and is not declared in the AndroidManifest, so it never runs on Android.",
            drift.single(),
        )
    }

    /** A lazy or removed component the AndroidManifest declares runs eagerly on Android alone. */
    @Test
    fun reportsAComponentTheAndroidManifestDeclaresAndTheRegistryDoesNot() {
        val drift = manifest.androidManifestDrift(
            setOf(
                componentName(initializerKey<AlphaInitializer>()),
                componentName(initializerKey<BetaInitializer>()),
                componentName(initializerKey<GammaInitializer>()),
            ),
        )
        assertEquals(
            listOf(
                "${componentName(initializerKey<BetaInitializer>())} is lazy in the StartupManifest " +
                    "and is declared in the AndroidManifest, so it runs eagerly on Android alone.",
                "${componentName(initializerKey<GammaInitializer>())} is removed from the " +
                    "StartupManifest and is declared in the AndroidManifest, so it still runs on Android.",
            ),
            drift,
        )
    }

    /**
     * A declared name this registry never heard of is an initializer written directly
     * against `androidx.startup`, which a mixed application is free to have. Reporting it
     * would make the check useless in exactly the applications that need it most.
     */
    @Test
    fun ignoresAComponentTheRegistryNeverHeardOf() {
        assertEquals(
            emptyList(),
            manifest.androidManifestDrift(
                setOf(
                    componentName(initializerKey<AlphaInitializer>()),
                    "com.example.LegacyInitializer",
                ),
            ),
        )
    }
}

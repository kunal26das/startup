package io.github.kunal26das.startup.sample

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Holds the sample's `AndroidManifest.xml` and [SampleStartup.manifest] in step.
 *
 * The two are genuinely separate sources of truth: off Android the shared manifest object
 * decides what is created, while on Android AndroidX reads only the XML. A component
 * added to one and forgotten in the other initializes on ten targets and silently does
 * nothing on the eleventh, which is the failure this test exists to prevent.
 */
class AndroidManifestParityTest {

    /**
     * Every eagerly registered component appears as a `<meta-data>` entry, and nothing
     * else does. The library reports the difference itself, so a failure names the
     * components that drifted rather than printing two sets to compare by eye.
     */
    @Suppress("DEPRECATION")
    @Test
    fun theManifestDeclaresExactlyTheEagerComponents() {
        assertEquals(
            emptyList(),
            SampleStartup.manifest.androidManifestDrift(
                NAME.findAll(manifestText()).map { it.groupValues[1] }.toSet(),
            ),
        )
    }

    /** The generated metadata is those `<meta-data>` lines and nothing surrounding them. */
    @Test
    fun theGeneratedMetadataIsOnlyTheMetaDataLines() {
        val generated = AndroidSampleStartup.androidManifestMetadata()
        assertEquals(
            NAME.findAll(manifestText()).map { it.groupValues[1] }.toSet(),
            NAME.findAll(generated).map { it.groupValues[1] }.toSet(),
        )
        assertEquals(false, generated.contains("<provider"))
        assertEquals(false, generated.contains("<application"))
        assertEquals(false, generated.contains("<manifest"))
        assertEquals(
            generated.lines().size,
            SampleStartup.manifest.eagerComponents.size,
        )
    }

    /** The manifest merges into AndroidX's own provider rather than declaring a second one. */
    @Test
    fun theManifestMergesIntoTheAndroidxProvider() {
        val text = manifestText()
        assertEquals(true, text.contains("androidx.startup.InitializationProvider"))
        assertEquals(true, text.contains("tools:node=\"merge\""))
    }

    private fun manifestText(): String {
        val path = System.getProperty(MANIFEST_PROPERTY)
        assertNotNull(path, "$MANIFEST_PROPERTY is not set; declare the manifest as a test input")
        val file = File(path)
        assertEquals(true, file.isFile, "AndroidManifest.xml not found at $path")
        return file.readText()
    }

    private companion object {
        private const val MANIFEST_PROPERTY = "startup.sample.androidManifest"
        private val NAME = Regex("<meta-data[^>]*?android:name=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
    }
}

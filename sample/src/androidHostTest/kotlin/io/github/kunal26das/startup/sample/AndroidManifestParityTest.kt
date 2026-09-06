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
 *
 * From 2.0.0 the library reconciles nothing, so this is written entirely against the API
 * that stays: the XML is read from disk, and the shared side is
 * `eagerComponents.map { it.name }`, which works because on Android an
 * `InitializerKey` **is** a `java.lang.Class` and therefore already names its component
 * the way `Class.forName` needs it named. This is the shape to copy into an application.
 *
 * The XML is on the test task's inputs, so editing it reruns this test rather than
 * leaving the task `UP-TO-DATE` with the drift undetected.
 */
class AndroidManifestParityTest {

    /**
     * Every eagerly registered component appears as a `<meta-data>` entry, and nothing
     * else does. Both directions fail: a component the XML omits never runs on Android,
     * and one the XML declares while the shared manifest keeps it lazy, removed or unknown
     * runs eagerly on Android alone.
     */
    @Test
    fun theManifestDeclaresExactlyTheEagerComponents() {
        assertEquals(
            SampleStartup.manifest.eagerComponents.map { it.name }.toSet(),
            NAME.findAll(manifestText()).map { it.groupValues[1] }.toSet(),
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

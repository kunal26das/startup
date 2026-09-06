package io.github.kunal26das.startup.sample

import android.content.Context
import androidx.startup.AppInitializer
import io.github.kunal26das.startup.androidManifestDrift

/**
 * The Android half of the sample. The components come from shared code, yet they are
 * reached here with the verbatim AndroidX spelling and the verbatim AndroidX types,
 * which is the proof that the shared initializers really are `androidx.startup`
 * initializers.
 */
object AndroidSampleStartup {

    /** Resolves the shared analytics client through AndroidX's own runtime. */
    fun analytics(context: Context): Analytics =
        AppInitializer.getInstance(context).initializeComponent(AnalyticsInitializer::class.java)

    /** Whether AndroidX considers the analytics client eagerly initialized. */
    fun isEager(context: Context): Boolean =
        AppInitializer.getInstance(context).isEagerlyInitialized(AnalyticsInitializer::class.java)

    /**
     * Uses a declaration deprecated in 1.1.0 and removed in 2.0.0, and is kept only so the
     * sample still covers it. From 2.0.0 the AndroidManifest is the source of truth and the
     * parity test reads it directly.
     *
     * The `<meta-data>` lines to paste into the `androidx.startup.InitializationProvider`
     * block of the application's AndroidManifest so that AndroidX starts the same
     * components [SampleStartup.manifest] declares.
     */
    @Suppress("DEPRECATION")
    fun androidManifestMetadata(): String = SampleStartup.manifest.androidManifestMetadata()

    /**
     * Uses a declaration deprecated in 1.1.0 and removed in 2.0.0, and is kept only so the
     * sample still covers it.
     *
     * Every disagreement between [SampleStartup.manifest] and the `<meta-data>` entries
     * the merged AndroidManifest of the running application really declares, read back
     * from `PackageManager`. Empty here, which is what the report shows, and what
     * `verifyAndroidManifest` would have thrown about had it not been.
     */
    @Suppress("DEPRECATION")
    fun androidManifestDrift(context: Context): List<String> =
        SampleStartup.manifest.androidManifestDrift(context)
}

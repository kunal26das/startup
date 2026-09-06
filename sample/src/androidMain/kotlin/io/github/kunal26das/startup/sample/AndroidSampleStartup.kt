package io.github.kunal26das.startup.sample

import android.content.Context
import androidx.startup.AppInitializer

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
}

package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.DefaultContext

/**
 * Runs the shared bootstrap on the platforms that have no `android.content.Context`,
 * using the [DefaultContext] the library supplies.
 */
object SampleLauncher {

    /** Boots the sample and returns its analytics client. */
    fun launch(): Analytics {
        SampleStartup.bootstrap(DefaultContext)
        return SampleStartup.analytics(DefaultContext)
    }

    /**
     * Boots the sample and renders [SampleReport] over it. This is the whole body of
     * every non-Android entry point; on Android the equivalent call needs no bootstrap,
     * because `androidx.startup.InitializationProvider` has already run.
     */
    fun report(): List<String> {
        SampleStartup.bootstrap(DefaultContext)
        return SampleReport.lines(DefaultContext)
    }
}

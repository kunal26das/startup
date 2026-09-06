package io.github.kunal26das.startup

/**
 * Resolves a component of its own wave from inside [create].
 *
 * Two eager components that declare nothing land in the same Kahn level, and a component of
 * the wave being run is the one re-entrant call the installing thread cannot be served
 * either: nothing a wave creates is written back until [WaveRunner.run] returns. It lives
 * beside [WaveSiblingTest] rather than in `commonTest` only because that test drives the
 * process-wide [Startup] through [DefaultContext], which is a non-Android type.
 */
class WaveSiblingInitializer : BaseInitializer<String>() {

    /** Asks for [AlphaInitializer], keeping whatever the engine answered with. */
    override fun create(context: Context): String {
        try {
            Startup.getInstance(context).initializeComponent(initializerKey<AlphaInitializer>())
            TestLog.record(RESOLVED)
        } catch (exception: StartupException) {
            refusal = exception
            TestLog.record(REFUSED)
        }
        return "waveSibling"
    }

    /** What the engine answered the re-entrant call with, across one test. */
    companion object {

        /** Recorded when the engine served the call. */
        const val RESOLVED = "waveSibling:resolved"

        /** Recorded when the engine refused it. */
        const val REFUSED = "waveSibling:refused"

        /** The refusal, if there was one. */
        var refusal: StartupException? = null
    }
}

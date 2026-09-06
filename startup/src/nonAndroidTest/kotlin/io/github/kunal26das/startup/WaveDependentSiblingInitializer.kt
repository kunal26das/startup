package io.github.kunal26das.startup

/**
 * Resolves the component it declares, from inside [create], under a runner.
 *
 * Declaring the edge is what puts [AlphaInitializer] in an earlier wave, and an earlier wave
 * is written back before this one is handed over. So this is the same call
 * [WaveSiblingInitializer] makes and the answer is the opposite one, which is what makes the
 * refusal a statement about the wave in flight rather than about runners.
 */
class WaveDependentSiblingInitializer : Initializer<String> {

    /** Reads [AlphaInitializer] back, keeping whatever the engine answered with. */
    override fun create(context: Context): String {
        try {
            Startup.getInstance(context).initializeComponent(initializerKey<AlphaInitializer>())
            TestLog.record(RESOLVED)
        } catch (exception: StartupException) {
            refusal = exception
            TestLog.record(REFUSED)
        }
        return "waveDependentSibling"
    }

    /** The whole point of this component: the edge is declared rather than imperative. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<AlphaInitializer>())

    /** What the engine answered the re-entrant call with, across one test. */
    companion object {

        /** Recorded when the engine served the call. */
        const val RESOLVED = "waveDependentSibling:resolved"

        /** Recorded when the engine refused it. */
        const val REFUSED = "waveDependentSibling:refused"

        /** The refusal, if there was one. */
        var refusal: StartupException? = null
    }
}

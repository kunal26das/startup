package io.github.kunal26das.startup

/**
 * Resolves another component from inside its own `create`.
 *
 * This is the pattern AndroidX documents, and it is what makes the difference between a
 * runner that stays on the installing thread and one that does not observable.
 */
class WaveReentrantInitializer : BaseInitializer<Any>() {

    /** Asks for the lazy component, recording whether the engine served or refused it. */
    override fun create(context: Context): Any {
        val appInitializer = requireNotNull(WaveReentry.appInitializer)
        try {
            appInitializer.initializeComponent(initializerKey<WaveLazyInitializer>())
            WaveLog.record(RESOLVED)
        } catch (throwable: Throwable) {
            WaveReentry.failure = throwable
            WaveLog.record(REFUSED)
        }
        return Any()
    }

    /** How the engine answered the re-entrant call. */
    companion object {

        /** Recorded when the engine served the re-entrant call. */
        const val RESOLVED = "reentrant:resolved"

        /** Recorded when the engine refused it. */
        const val REFUSED = "reentrant:refused"
    }
}

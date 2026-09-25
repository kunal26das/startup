package io.github.kunal26das.startup

/**
 * Resolves [AlphaInitializer] from inside its own `create` and records how the engine
 * answered, so a test can tell a served call from a refused one on any thread.
 */
class LockReentrantInitializer : BaseInitializer<Unit>() {

    /** Asks for [AlphaInitializer], recording [RESOLVED] or the refusal's message. */
    override fun create(context: Context) {
        val appInitializer = requireNotNull(LockProbe.appInitializer)
        val outcome = try {
            appInitializer.initializeComponent(initializerKey<AlphaInitializer>())
            RESOLVED
        } catch (exception: StartupException) {
            exception.message.orEmpty()
        }
        LockProbe.outcome.value = outcome
    }

    /** What this component records when the engine serves it. */
    companion object {

        /** Recorded when the engine served the call. */
        const val RESOLVED = "resolved"
    }
}

package io.github.kunal26das.startup

/** Depends on [AlphaInitializer]. One half of the diamond. */
class BetaInitializer : Initializer<String> {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("beta")
        return "beta"
    }

    /** Requires [AlphaInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<AlphaInitializer>())
}

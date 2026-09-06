package io.github.kunal26das.startup

/** Depends on [AlphaInitializer]. The other half of the diamond. */
class GammaInitializer : Initializer<String> {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("gamma")
        return "gamma"
    }

    /** Requires [AlphaInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<AlphaInitializer>())
}

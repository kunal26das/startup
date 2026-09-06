package io.github.kunal26das.startup

/** The tip of the diamond: depends on both [BetaInitializer] and [GammaInitializer]. */
class DeltaInitializer : Initializer<String> {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("delta")
        return "delta"
    }

    /** Requires [BetaInitializer] and [GammaInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(
        initializerKey<BetaInitializer>(),
        initializerKey<GammaInitializer>(),
    )
}

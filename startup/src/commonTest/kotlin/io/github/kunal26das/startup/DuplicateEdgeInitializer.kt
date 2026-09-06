package io.github.kunal26das.startup

/** Declares the same dependency twice, which must count as one edge. */
class DuplicateEdgeInitializer : Initializer<String> {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("duplicateEdge")
        return "duplicateEdge"
    }

    /** Requires [AlphaInitializer], listed twice on purpose. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(
        initializerKey<AlphaInitializer>(),
        initializerKey<AlphaInitializer>(),
    )
}

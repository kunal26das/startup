package io.github.kunal26das.startup

/** Declares [BackEdgeCallerInitializer], closing the loop that only shows up at runtime. */
class BackEdgeCalleeInitializer : Initializer<String> {

    /** Never runs. */
    override fun create(context: Context): String {
        TestLog.record("backEdgeCallee")
        return "backEdgeCallee"
    }

    /** Requires [BackEdgeCallerInitializer], which is still being created. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<BackEdgeCallerInitializer>())
}

package io.github.kunal26das.startup

/** What [NestedCallerInitializer] asks for, two hops away from closing the loop. */
class NestedRootInitializer : Initializer<String> {

    /** Never runs. */
    override fun create(context: Context): String {
        TestLog.record("nestedRoot")
        return "nestedRoot"
    }

    /** Requires [NestedMiddleInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<NestedMiddleInitializer>())
}

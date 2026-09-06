package io.github.kunal26das.startup

/** The hop between [NestedRootInitializer] and the component still being created. */
class NestedMiddleInitializer : Initializer<String> {

    /** Never runs. */
    override fun create(context: Context): String {
        TestLog.record("nestedMiddle")
        return "nestedMiddle"
    }

    /** Requires [NestedCallerInitializer], which is still in flight. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<NestedCallerInitializer>())
}

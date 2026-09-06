package io.github.kunal26das.startup

/** Closes the loop back onto [DeepCallerInitializer], which is still being created. */
class DeepTailInitializer : Initializer<String> {

    /** Never runs. */
    override fun create(context: Context): String {
        TestLog.record("deepTail")
        return "deepTail"
    }

    /** Requires [DeepCallerInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<DeepCallerInitializer>())
}

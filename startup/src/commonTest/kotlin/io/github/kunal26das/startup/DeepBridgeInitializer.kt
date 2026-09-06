package io.github.kunal26das.startup

/** What [DeepCallerInitializer] asks for, and the only thing naming the next hop. */
class DeepBridgeInitializer : Initializer<String> {

    /** Never runs. */
    override fun create(context: Context): String {
        TestLog.record("deepBridge")
        return "deepBridge"
    }

    /** Requires [DeepNestedCallerInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<DeepNestedCallerInitializer>())
}

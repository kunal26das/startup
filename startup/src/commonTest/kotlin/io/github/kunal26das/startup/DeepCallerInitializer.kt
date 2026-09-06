package io.github.kunal26das.startup

/**
 * The outer half of a cycle that closes two nested `create` calls deep. It asks for
 * [DeepBridgeInitializer], which needs [DeepNestedCallerInitializer] first, so the
 * component created next inside it is one this class never named.
 */
class DeepCallerInitializer : BaseInitializer<String>() {

    /** Records the attempt, then re-enters the runtime. */
    override fun create(context: Context): String {
        TestLog.record("deepCaller")
        Startup.getInstance(context).initializeComponent(initializerKey<DeepBridgeInitializer>())
        return "deepCaller"
    }
}

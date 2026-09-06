package io.github.kunal26das.startup

/**
 * The inner half: created while [DeepCallerInitializer] is still in flight, though nothing
 * connects the two directly, and it re-enters the runtime once more.
 */
class DeepNestedCallerInitializer : BaseInitializer<String>() {

    /** Records the attempt, then re-enters the runtime again. */
    override fun create(context: Context): String {
        TestLog.record("deepNestedCaller")
        Startup.getInstance(context).initializeComponent(initializerKey<DeepTailInitializer>())
        return "deepNestedCaller"
    }
}

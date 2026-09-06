package io.github.kunal26das.startup

/**
 * Asks for [NestedRootInitializer] from inside [create]. The way back to this class runs
 * through [NestedMiddleInitializer], so the closing hops are only visible in the plan the
 * nested call builds, never in the set of components already in flight.
 */
class NestedCallerInitializer : BaseInitializer<String>() {

    /** Records the attempt, then re-enters the runtime. */
    override fun create(context: Context): String {
        TestLog.record("nestedCaller")
        Startup.getInstance(context).initializeComponent(initializerKey<NestedRootInitializer>())
        return "nestedCaller"
    }
}

package io.github.kunal26das.startup

/**
 * Asks for [BackEdgeCalleeInitializer] from inside [create], which declares this class as
 * a dependency. The edge back is therefore invisible to the planner and can only be
 * caught while the component is in flight.
 */
class BackEdgeCallerInitializer : BaseInitializer<String>() {

    /** Records the attempt, then re-enters the runtime. */
    override fun create(context: Context): String {
        TestLog.record("backEdgeCaller")
        Startup.getInstance(context).initializeComponent(initializerKey<BackEdgeCalleeInitializer>())
        return "backEdgeCaller"
    }
}

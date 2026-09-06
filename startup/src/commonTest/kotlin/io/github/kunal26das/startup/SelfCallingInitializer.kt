package io.github.kunal26das.startup

/** Asks the runtime for itself from inside [create], which is a cycle discovered late. */
class SelfCallingInitializer : BaseInitializer<Unit>() {

    /** Re-enters the runtime asking for itself. */
    override fun create(context: Context) {
        Startup.getInstance(context).initializeComponent(initializerKey<SelfCallingInitializer>())
    }
}

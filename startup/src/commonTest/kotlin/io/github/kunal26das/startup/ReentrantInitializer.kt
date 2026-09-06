package io.github.kunal26das.startup

/**
 * Resolves another component from inside [create], which is the pattern AndroidX
 * documents for reading a dependency's value back.
 */
class ReentrantInitializer : BaseInitializer<String>() {

    /** Reads [AlphaInitializer] back through the runtime and records the creation. */
    override fun create(context: Context): String {
        val alpha = Startup.getInstance(context)
            .initializeComponent(initializerKey<AlphaInitializer>())
        TestLog.record("reentrant")
        return "reentrant:$alpha"
    }
}

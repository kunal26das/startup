package io.github.kunal26das.startup

/**
 * Stands in for an initializer a host application built itself and handed to Kotlin, so
 * its key can only be computed from the instance. Depends on [AlphaInitializer], because
 * joining the ordering is the whole point of registering it.
 */
class RuntimeKeyInitializer : Initializer<String> {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("runtimeKey")
        return "runtimeKey"
    }

    /** Requires [AlphaInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<AlphaInitializer>())
}

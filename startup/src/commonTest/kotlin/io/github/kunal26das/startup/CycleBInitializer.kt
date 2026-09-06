package io.github.kunal26das.startup

/** The other half of a two-node cycle with [CycleAInitializer]. */
class CycleBInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("cycleB")
    }

    /** Requires [CycleAInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<CycleAInitializer>())
}

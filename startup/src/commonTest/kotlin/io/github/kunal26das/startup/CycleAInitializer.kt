package io.github.kunal26das.startup

/** One half of a two-node cycle with [CycleBInitializer]. */
class CycleAInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("cycleA")
    }

    /** Requires [CycleBInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<CycleBInitializer>())
}

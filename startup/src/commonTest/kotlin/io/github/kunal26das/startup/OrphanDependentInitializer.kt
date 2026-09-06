package io.github.kunal26das.startup

/** Requires the unregistered [OrphanInitializer]. */
class OrphanDependentInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("orphanDependent")
    }

    /** Requires [OrphanInitializer], which no manifest registers. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<OrphanInitializer>())
}

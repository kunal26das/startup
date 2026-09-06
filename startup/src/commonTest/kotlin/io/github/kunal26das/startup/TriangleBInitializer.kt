package io.github.kunal26das.startup

/** The middle of a three-node cycle: requires [TriangleCInitializer]. */
class TriangleBInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("triangleB")
    }

    /** Requires [TriangleCInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<TriangleCInitializer>())
}

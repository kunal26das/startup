package io.github.kunal26das.startup

/** The entry of a three-node cycle: requires [TriangleBInitializer]. */
class TriangleAInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("triangleA")
    }

    /** Requires [TriangleBInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<TriangleBInitializer>())
}

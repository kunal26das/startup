package io.github.kunal26das.startup

/** Closes a three-node cycle back onto [TriangleAInitializer]. */
class TriangleCInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("triangleC")
    }

    /** Requires [TriangleAInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<TriangleAInitializer>())
}

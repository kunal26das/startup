package io.github.kunal26das.startup

/**
 * An acyclic approach to a cycle: it reaches [LoopHeadInitializer], which loops with
 * [LoopTailInitializer]. The reported cycle must start at the loop, not here.
 */
class EntryInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("entry")
    }

    /** Requires [LoopHeadInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoopHeadInitializer>())
}

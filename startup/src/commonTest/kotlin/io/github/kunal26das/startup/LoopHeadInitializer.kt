package io.github.kunal26das.startup

/** The node the walk re-enters, so the reported cycle starts here. */
class LoopHeadInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("loopHead")
    }

    /** Requires [LoopTailInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoopTailInitializer>())
}

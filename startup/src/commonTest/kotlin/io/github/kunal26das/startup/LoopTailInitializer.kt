package io.github.kunal26das.startup

/** Closes the loop back onto [LoopHeadInitializer]. */
class LoopTailInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("loopTail")
    }

    /** Requires [LoopHeadInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoopHeadInitializer>())
}

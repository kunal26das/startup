package io.github.kunal26das.startup

/** Depends on [FailingInitializer], so it must never be created. */
class FailingDependentInitializer : Initializer<Unit> {

    /** Must never run. */
    override fun create(context: Context) {
        TestLog.record("failingDependent")
    }

    /** Requires [FailingInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<FailingInitializer>())
}

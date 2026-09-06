package io.github.kunal26das.startup

/** Depends on itself, the shortest possible cycle. */
class SelfDependentInitializer : Initializer<Unit> {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("selfDependent")
    }

    /** Requires itself. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<SelfDependentInitializer>())
}

package io.github.kunal26das.startup

/** Throws from [dependencies] rather than from [create], which the planner must wrap too. */
class ThrowingDependenciesInitializer : Initializer<Unit> {

    /** Never runs, because the planner fails first. */
    override fun create(context: Context) {
        TestLog.record("throwingDependencies")
    }

    /** Fails while the graph is being read. */
    override fun dependencies(): List<AnyInitializerKey> =
        throw IllegalStateException("dependencies unavailable")
}

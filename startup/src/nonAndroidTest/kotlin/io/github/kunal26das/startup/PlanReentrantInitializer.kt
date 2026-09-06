package io.github.kunal26das.startup

/**
 * Re-enters the runtime from [dependencies] rather than from [create], which is the one
 * way a nested run begins while nothing is in flight yet. The engine's instance cache has
 * to survive that nested run, or everything the outer plan built so far is constructed a
 * second time.
 *
 * It lives here rather than in `commonTest` because naming [DefaultContext] is the only way
 * to reach the engine from [dependencies], which takes no [Context] of its own, and
 * [DefaultContext] is a non-Android type.
 */
class PlanReentrantInitializer : Initializer<String> {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("planReentrant")
        return "planReentrant"
    }

    /** Resolves [AlphaInitializer] while the outer plan is still being built. */
    override fun dependencies(): List<AnyInitializerKey> {
        Startup.getInstance(DefaultContext).initializeComponent(initializerKey<AlphaInitializer>())
        return emptyList()
    }
}

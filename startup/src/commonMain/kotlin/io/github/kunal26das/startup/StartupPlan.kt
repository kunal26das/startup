package io.github.kunal26das.startup

/**
 * The order in which a set of components must be created.
 *
 * @property order every component to create, dependencies first. This is what the runtime
 * walks, one component at a time, on the calling thread.
 * @property waves the same components grouped into Kahn levels: everything in a wave
 * depends only on earlier waves, and `waves.flatten()` is exactly [order]. That is what
 * makes a wave safe to run all at once, which is what `Startup.install(context, manifest,
 * runner)` does with a [WaveRunner]; without one the runtime walks [order] instead, one
 * component at a time on the calling thread.
 *
 * Reading it is also the way to run the graph entirely outside this library: construct
 * your own initializers off this grouping and hold the results yourself. Either way the
 * one rule is that a component running in a wave must not call back into
 * [AppInitializer.initializeComponent], which is serialized behind a lock the installing
 * thread still holds. Declare what it needs in [Initializer.dependencies] instead, which
 * is what puts it in a later wave.
 */
class StartupPlan internal constructor(
    val order: List<AnyInitializerKey>,
    val waves: List<List<AnyInitializerKey>>,
    internal val edges: Map<AnyInitializerKey, List<AnyInitializerKey>> = emptyMap(),
) {

    /** Whether there is nothing left to create. */
    val isEmpty: Boolean get() = order.isEmpty()

    override fun toString(): String = order.joinToString(" -> ") { componentName(it) }
}

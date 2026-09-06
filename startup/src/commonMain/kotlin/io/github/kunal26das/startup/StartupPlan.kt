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
 * your own initializers off this grouping and hold the results yourself. Handing the waves
 * to a [WaveRunner] instead carries one rule, and it is narrower than the flat prohibition
 * 2.x documented. A task may call back into [AppInitializer.initializeComponent] from the
 * thread that called `Startup.install`, for anything an earlier wave already created. It may
 * not from any other thread, where the lock it would wait for is held until the install ends,
 * and it may not for a component of the wave in flight, which is not written back until
 * [WaveRunner.run] returns. Declaring what a component needs in [Initializer.dependencies] is
 * what puts that dependency in an earlier wave, and that is what makes the call safe from
 * anywhere.
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

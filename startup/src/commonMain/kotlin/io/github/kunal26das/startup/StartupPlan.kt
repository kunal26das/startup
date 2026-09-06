package io.github.kunal26das.startup

/**
 * The order in which a set of components must be created.
 *
 * @property order every component to create, dependencies first. This is what the runtime
 * walks, one component at a time, on the calling thread.
 * @property waves the same components grouped into Kahn levels: everything in a wave
 * depends only on earlier waves, and `waves.flatten()` is exactly [order]. This is
 * **diagnostic data, not a scheduling hook.** Nothing in this library ever runs a wave on
 * another thread, and no consumer can make it: [AppInitializer.initializeComponent] and
 * `Startup.install` are both serialized behind one reentrant lock, so driving them from
 * several threads only queues them, and a `create` running on a worker thread that called
 * back into `initializeComponent` would block on a lock the calling thread still holds.
 * A consumer that genuinely wants concurrent startup has to construct and run its own
 * initializers off this grouping, outside [AppInitializer], and hold the results itself.
 */
class StartupPlan internal constructor(
    val order: List<AnyInitializerKey>,
    val waves: List<List<AnyInitializerKey>>,
) {

    /** Whether there is nothing left to create. */
    val isEmpty: Boolean get() = order.isEmpty()

    override fun toString(): String = order.joinToString(" -> ") { componentName(it) }
}

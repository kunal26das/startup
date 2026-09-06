package io.github.kunal26das.startup

/**
 * Runs one [StartupPlan] wave, however the host wants it run.
 *
 * Every task in a wave depends only on earlier waves, so they may run at the same time.
 * This is the seam a host uses to do that: [Startup.install] hands each wave over in
 * turn and waits for [run] to return before planning the next one, so the ordering,
 * cycle detection, deduplication and the created components all stay with the library
 * while the concurrency stays with the caller.
 *
 * ```
 * Startup.install(context, manifest) { wave ->
 *     runBlocking { coroutineScope { wave.map { async(Dispatchers.IO) { it() } }.awaitAll() } }
 * }
 * ```
 *
 * **The dispatcher in that snippet is the whole point.** A task is an ordinary blocking
 * call rather than a suspending one, so `async { }` without a dispatcher inherits
 * `runBlocking`'s single-threaded event loop and runs the wave one task after another on
 * the calling thread — exactly what `install(context, manifest)` already does.
 *
 * Each task names the component it will create, so a runner may route a wave rather than
 * merely run it; see [StartupTask].
 *
 * [run] **must** invoke every task exactly once, must let a task's failure out where the
 * language allows, and must not return until all of them have finished. A second invocation
 * of a task is refused by [StartupTask.invoke] itself; the other two are checked once [run]
 * returns. Either way the violation is a [StartupException] naming the components it applies
 * to. Swift cannot rethrow — the exported protocol method is not
 * `throws` — so a Swift runner catches and drops, and the engine re-raises what the task
 * recorded.
 *
 * A task may call back into [AppInitializer.initializeComponent] only from the thread that
 * called [Startup.install], because the engine holds its lock across the whole install.
 * From the thread the task's body runs on, that call fails immediately with a
 * [StartupException] rather than blocking on a lock the installing thread still owns.
 *
 * **Even on the installing thread it may not ask for a component of the wave it is in.**
 * Nothing a wave creates is written back until [run] returns, so a sibling is neither
 * created nor creatable there, and it is refused by name rather than reported as a cycle.
 * Only what an earlier wave already built can be read.
 *
 * **A task must not hand that call to a further thread.** The guard knows the thread that
 * entered the task and no other, so a task that spawns a thread, or a
 * [CoroutineInitializer] whose `createAsync` switches dispatchers, reaches the engine on a
 * thread that waits for a lock this install cannot release. Declare what a component needs
 * in [Initializer.dependencies] instead, which is what puts it in an earlier wave.
 *
 * On Android this is not used: `androidx.startup` creates each component itself, depth
 * first on the calling thread, and offers no seam to change that. A manifest installed
 * with a runner therefore runs concurrently on the other ten targets and sequentially on
 * Android. For ordinary initializers that makes a runner a performance decision and never a
 * correctness one; for a [CoroutineInitializer] it is a correctness one, because the runner
 * picks the thread `create` blocks and whether that thread's pool can still make progress.
 */
fun interface WaveRunner {

    /** Runs every task in [wave], returning only once all of them are done. */
    fun run(wave: List<StartupTask>)
}

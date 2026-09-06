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
 *     runBlocking { coroutineScope { wave.map { async { it() } }.awaitAll() } }
 * }
 * ```
 *
 * [run] **must** invoke every task exactly once and must not return until all of them
 * have finished; the library records the wave's components immediately afterwards, and a
 * task still running at that point would write a component nothing is waiting for.
 *
 * A task must not call back into [AppInitializer.initializeComponent]. The engine holds
 * its lock across the whole install, so a task running on another thread would block on a
 * lock the calling thread still owns. Declare what a component needs in
 * [Initializer.dependencies] instead, which is what puts it in an earlier wave.
 *
 * On Android this is not used: `androidx.startup` creates each component itself, depth
 * first on the calling thread, and offers no seam to change that. A manifest installed
 * with a runner therefore runs concurrently on the other ten targets and sequentially on
 * Android, so a runner is a performance decision and never a correctness one.
 */
fun interface WaveRunner {

    /** Runs every task in [wave], returning only once all of them are done. */
    fun run(wave: List<() -> Unit>)
}

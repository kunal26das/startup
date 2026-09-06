package io.github.kunal26das.startup

/**
 * One component's creation, handed to a [WaveRunner] to run.
 *
 * A task names the [component] it creates. That is what lets a host route a wave rather
 * than merely run it: a component that has to stay on the calling thread and one that may
 * move to a worker are told apart here instead of guessed at, and a slow or failed wave is
 * attributable to a component rather than to an index. It is also the only place a host
 * can hang instrumentation, because the engine has no hook of its own.
 *
 * ```
 * Startup.install(context, manifest) { wave ->
 *     runBlocking {
 *         coroutineScope {
 *             wave.map { task -> async(dispatcher) { trace(task.toString()) { task() } } }.awaitAll()
 *         }
 *     }
 * }
 * ```
 *
 * [invoke] runs the component's [Initializer.create] exactly once and records how that
 * ended. A runner that runs one task twice is refused here, at the second call; one that
 * skips a task or swallows a task's failure is caught by the engine once [WaveRunner.run]
 * returns. Either is reported by name, rather than surfacing later as a missing component.
 *
 * The constructor is public so that a host can exercise its own runner against a wave it
 * built. The engine never trusts a task it did not create — it only ever inspects the ones
 * it handed over — so a fabricated task cannot reach engine state.
 */
class StartupTask(
    /** The component this task will create. */
    val component: AnyInitializerKey,
    private val body: () -> Unit,
) {

    private val once = StartupOnce()

    internal var completed: Boolean = false
        private set

    internal var failure: Throwable? = null
        private set

    /**
     * Creates [component].
     *
     * Throws whatever [Initializer.create] threw, after recording it so the engine can
     * report a runner that swallowed it. Throws [StartupException] if this task has
     * already been claimed, whether by an earlier call or by one still running on another
     * thread.
     */
    @Throws(StartupException::class)
    operator fun invoke() {
        if (!once.claim()) {
            throw StartupException(
                "Cannot initialize ${componentName(component)}. Its WaveRunner ran the same " +
                    "task twice; run must invoke every task exactly once.",
                null,
                listOf(component),
            )
        }
        val wasRunning = StartupWaveThread.running
        StartupWaveThread.running = true
        try {
            body()
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            StartupWaveThread.running = wasRunning
        }
        completed = true
    }

    override fun toString(): String = componentName(component)
}

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
    private val repeated = StartupOnce()
    private var bodyFailure: Throwable? = null

    /** The wrapper raised for a body failure, so the engine can recover its original cause. */
    internal var wrappedFailure: StartupException? = null
        private set

    internal var completed: Boolean = false
        private set

    /**
     * The body failure, or a duplicate invocation the runner caught and dropped.
     *
     * Only the thread that claimed this task writes [bodyFailure]. Another invocation
     * records its refusal separately and atomically, so it cannot overwrite the body's
     * failure or have a successful body erase the contract violation.
     */
    internal val failure: Throwable?
        get() = bodyFailure ?: if (repeated.isClaimed) duplicateInvocation() else null

    /**
     * Creates [component].
     *
     * Records the original failure from [Initializer.create] and throws it as a
     * [StartupException], preserving an existing one or wrapping any other throwable as
     * its cause. Swift can therefore catch every body failure as an `NSError` and return
     * from its runner so the engine can report it. A second invocation also throws
     * [StartupException], whether the claimed body has finished or is still running.
     */
    @Throws(StartupException::class)
    operator fun invoke() {
        if (!once.claim()) {
            repeated.claim()
            throw duplicateInvocation()
        }
        val wasRunning = StartupWaveThread.running
        StartupWaveThread.running = true
        try {
            body()
        } catch (throwable: Throwable) {
            bodyFailure = throwable
            if (throwable is StartupException) throw throwable
            throw StartupException(
                "Cannot initialize ${componentName(component)}.",
                throwable,
                listOf(component),
            ).also { wrappedFailure = it }
        } finally {
            StartupWaveThread.running = wasRunning
        }
        completed = true
    }

    private fun duplicateInvocation(): StartupException = StartupException(
        "Cannot initialize ${componentName(component)}. Its WaveRunner ran the same " +
            "task twice; run must invoke every task exactly once.",
        null,
        listOf(component),
    )

    override fun toString(): String = componentName(component)
}

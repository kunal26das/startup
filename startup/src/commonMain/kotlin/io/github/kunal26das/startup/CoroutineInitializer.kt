package io.github.kunal26das.startup

/**
 * An [Initializer] whose work suspends.
 *
 * The graph's promise is that a dependency is *created* before the component that declares
 * it. For a component whose real work is a `suspend` call, a plain [Initializer] can only
 * keep half of it: `create` has nowhere to await, so the idiomatic escape is to launch the
 * work and return, and the graph then orders the launches rather than the completions.
 * That is the failure this type exists to remove — nearly every mobile SDK's initializer is
 * asynchronous, and a dependency edge that does not wait is not a dependency edge.
 *
 * ```
 * class FirebaseInitializer : CoroutineInitializer<Unit> {
 *     override suspend fun createAsync(context: StartupContext) = Firebase.start(context)
 * }
 * ```
 *
 * [create] is inherited and blocks the calling thread until [createAsync] returns, so a
 * component declaring this one in [Initializer.dependencies] starts after it has finished.
 * Four consequences follow, and every one of them is the caller's to accept:
 *
 * - **It blocks.** On Android that thread is whichever one `InitializationProvider` runs
 *   on, which is the main thread; elsewhere it is whoever called [Startup.install], unless
 *   a [WaveRunner] moved the wave. A component that must not block startup should still
 *   launch and return, and say so by staying an ordinary [Initializer].
 * - **It must not need the thread it is blocking.** A [createAsync] that dispatches to the
 *   main dispatcher, from the main thread, deadlocks — the ordinary `runBlocking` hazard.
 * - **It must not be dispatched onto the pool it will resume on.** Each blocked [create]
 *   holds a worker of whatever pool a [WaveRunner] sent it to, so a wave carrying as many
 *   such components as that pool has parallelism starves it and the install never returns.
 *   Give the runner a dispatcher the components themselves do not use.
 * - **It must not resolve another component from inside [createAsync].** A wave task that
 *   calls [AppInitializer.initializeComponent] is refused at once, but only on the thread
 *   the task body runs on; switching dispatchers moves that call to a thread the guard does
 *   not know about, where it waits for a lock the install cannot release. Declare the edge
 *   in [Initializer.dependencies].
 *
 * This is a Kotlin-side type. Kotlin interface default bodies do not become Objective-C
 * protocol defaults, so a Swift class conforming to it inherits neither [create] nor
 * [dependencies] and gains nothing; a Swift initializer that has to await should implement
 * [Initializer] directly and do its own waiting.
 *
 * Kotlin/JS and Kotlin/Wasm have no thread to block and no `runBlocking` to block it with,
 * so [create] reports that rather than pretending. Those two targets can still run every
 * ordinary [Initializer]; it is only the blocking bridge that has nowhere to stand.
 */
interface CoroutineInitializer<T : Any> : Initializer<T> {

    /** Creates this component, suspending for as long as the work takes. */
    suspend fun createAsync(context: Context): T

    /** Runs [createAsync] to completion, blocking the calling thread. */
    override fun create(context: Context): T = awaitBlocking { createAsync(context) }

    /** No dependencies, so an implementation that declares none overrides nothing. */
    override fun dependencies(): List<AnyInitializerKey> = emptyList()
}

internal const val COROUTINE_INITIALIZER_UNSUPPORTED: String =
    "Cannot create a CoroutineInitializer on this target. Kotlin/JS and Kotlin/Wasm have " +
        "one thread and no way to park it, so there is no way to run createAsync to " +
        "completion from create. Implement Initializer directly and launch the work, or " +
        "keep this component off these two targets."

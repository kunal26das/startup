package io.github.kunal26das.startup

/**
 * Whether the current thread is inside a [StartupTask].
 *
 * The engine holds its lock across a whole install, so a wave task that calls back into
 * the engine from a thread other than the installing one can never be served: the lock it
 * waits for is released only once the runner returns, and the runner returns only once the
 * task it is waiting on finishes. That wait is not slow, it is unserviceable, and this flag
 * is how the lock tells it apart from ordinary contention. A thread that merely happens to
 * ask for a component while an install runs is waiting for something that really will
 * arrive, and still waits.
 */
internal expect object StartupWaveThread {

    /** True while this thread is running a task handed to a [WaveRunner]. */
    var running: Boolean
}

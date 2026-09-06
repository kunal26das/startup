package io.github.kunal26das.startup

/**
 * The one lock every engine entry point runs under, which is what AndroidX gets from
 * `synchronized (sLock)`.
 *
 * It is reentrant: the thread holding it may take it again, so an [Initializer.create]
 * that resolves another component does not deadlock against itself. A thread that does
 * not hold it waits.
 *
 * A lock constructed with [guardsWaveTasks] refuses one caller rather than making it wait:
 * a thread inside a [StartupTask], whose wait can never end, because the install holds this
 * lock until [WaveRunner.run] returns and `run` cannot return until that task does. Only the
 * engine's lock is held that long, so only the engine's lock guards; see [StartupWaveThread]
 * for what the guard does and does not cover.
 */
internal expect class StartupLock(guardsWaveTasks: Boolean) {

    /**
     * Runs [block] under the lock.
     *
     * Throws [StartupException] when this lock guards wave tasks and the caller is inside a
     * [StartupTask] on a thread that does not already hold the lock.
     */
    fun <T> withLock(block: () -> T): T
}

internal fun startupLockBarrier(): StartupException = StartupException(
    "Cannot initialize the startup graph from this thread. It is running a WaveRunner task, " +
        "and the engine is held for the whole install, so the lock it would wait for is not " +
        "released until that task returns. Declare what a component needs in dependencies() " +
        "instead, which is what puts it in an earlier wave, or install without a runner.",
)

package io.github.kunal26das.startup

internal actual class StartupLock actual constructor(
    private val guardsWaveTasks: Boolean,
) {

    private val mutex = StartupMutex()

    actual fun <T> withLock(block: () -> T): T {
        if (!mutex.tryLock()) {
            if (guardsWaveTasks && StartupWaveThread.running) throw startupLockBarrier()
            mutex.lock()
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}

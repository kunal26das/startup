package io.github.kunal26das.startup

import java.util.concurrent.locks.ReentrantLock

internal actual class StartupLock actual constructor(
    private val guardsWaveTasks: Boolean,
) {

    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T {
        if (!lock.tryLock()) {
            if (guardsWaveTasks && StartupWaveThread.running) throw startupLockBarrier()
            lock.lock()
        }
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

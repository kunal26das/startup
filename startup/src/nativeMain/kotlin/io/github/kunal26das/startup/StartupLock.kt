package io.github.kunal26das.startup

import kotlin.concurrent.AtomicReference

internal actual class StartupLock actual constructor(
    private val guardsWaveTasks: Boolean,
) {

    private val owner = AtomicReference<Any?>(null)

    actual fun <T> withLock(block: () -> T): T {
        val identity = StartupLockOwner.identity
        if (owner.value === identity) return block()
        while (!owner.compareAndSet(null, identity)) {
            if (guardsWaveTasks && StartupWaveThread.running) throw startupLockBarrier()
            startupYield()
        }
        try {
            return block()
        } finally {
            owner.value = null
        }
    }
}

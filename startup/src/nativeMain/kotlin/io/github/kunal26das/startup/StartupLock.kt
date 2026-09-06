package io.github.kunal26das.startup

import kotlin.concurrent.AtomicReference

/**
 * Kotlin/Native has threads and no lock in its standard library, so this is a reentrant
 * spin lock over one atomic reference. Startup work is short and contention is rare, so
 * spinning costs less than a parking primitive would.
 */
internal actual class StartupLock actual constructor() {

    private val owner = AtomicReference<Any?>(null)

    /** Runs [block] once this thread owns the lock, re-entering without reacquiring it. */
    actual fun <T> withLock(block: () -> T): T {
        val identity = StartupLockOwner.identity
        if (owner.value === identity) return block()
        while (!owner.compareAndSet(null, identity)) {
            continue
        }
        try {
            return block()
        } finally {
            owner.value = null
        }
    }
}

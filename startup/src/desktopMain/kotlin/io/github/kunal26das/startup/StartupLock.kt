package io.github.kunal26das.startup

import java.util.concurrent.locks.ReentrantLock

/** On the JVM the platform already ships the exact lock this needs. */
internal actual class StartupLock actual constructor() {

    private val lock = ReentrantLock()

    /** Runs [block] under [ReentrantLock], which the owning thread may re-enter. */
    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

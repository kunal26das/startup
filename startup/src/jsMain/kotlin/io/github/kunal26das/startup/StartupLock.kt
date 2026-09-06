package io.github.kunal26das.startup

/** Kotlin/JS runs on one thread, so there is nothing to exclude. */
internal actual class StartupLock actual constructor() {

    /** Calls [block] directly. */
    actual fun <T> withLock(block: () -> T): T = block()
}

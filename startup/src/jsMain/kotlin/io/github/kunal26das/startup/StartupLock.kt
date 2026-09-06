package io.github.kunal26das.startup

internal actual class StartupLock actual constructor(
    @Suppress("UNUSED_PARAMETER") guardsWaveTasks: Boolean,
) {

    actual fun <T> withLock(block: () -> T): T = block()
}

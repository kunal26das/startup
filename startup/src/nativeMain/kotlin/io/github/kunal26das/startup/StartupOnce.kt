package io.github.kunal26das.startup

import kotlin.concurrent.AtomicInt

internal actual class StartupOnce actual constructor() {

    private val claimed = AtomicInt(0)

    actual fun claim(): Boolean = claimed.compareAndSet(0, 1)
}

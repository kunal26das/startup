package io.github.kunal26das.startup

import java.util.concurrent.atomic.AtomicBoolean

internal actual class StartupOnce actual constructor() {

    private val claimed = AtomicBoolean(false)

    actual fun claim(): Boolean = claimed.compareAndSet(false, true)
}

package io.github.kunal26das.startup

internal actual class StartupOnce actual constructor() {

    private var claimed = false

    actual fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }
}

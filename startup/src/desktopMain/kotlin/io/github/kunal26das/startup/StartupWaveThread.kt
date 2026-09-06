package io.github.kunal26das.startup

internal actual object StartupWaveThread {

    private val state = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    actual var running: Boolean
        get() = state.get() == true
        set(value) {
            state.set(value)
        }
}

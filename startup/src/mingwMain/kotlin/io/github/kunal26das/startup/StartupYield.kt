package io.github.kunal26das.startup

import platform.windows.SwitchToThread

internal actual fun startupYield() {
    SwitchToThread()
}

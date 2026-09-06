package io.github.kunal26das.startup

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual object StartupWaveThread {

    actual var running: Boolean = false
}

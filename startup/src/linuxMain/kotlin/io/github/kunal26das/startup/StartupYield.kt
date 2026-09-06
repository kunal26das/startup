package io.github.kunal26das.startup

import platform.posix.sched_yield

internal actual fun startupYield() {
    sched_yield()
}

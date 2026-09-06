package io.github.kunal26das.startup

import kotlin.native.concurrent.ThreadLocal

/**
 * The token that identifies the calling thread to [StartupLock]. Kotlin/Native has no
 * portable thread identifier, so each thread gets its own instance of this object and its
 * [identity] stands in for one.
 */
@ThreadLocal
internal object StartupLockOwner {

    /** An object unique to the thread that reads it. */
    val identity: Any = Any()
}

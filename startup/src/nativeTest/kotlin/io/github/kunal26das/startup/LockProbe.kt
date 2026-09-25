package io.github.kunal26das.startup

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference

/** What a native lock test shares, across threads, with the initializers it installs. */
object LockProbe {

    /** The runtime a [LockReentrantInitializer] calls back into. */
    var appInitializer: AppInitializer? = null

    /** Set once a [LockHoldingInitializer] is inside `create`, holding the engine lock. */
    val holding = AtomicInt(0)

    /** Set once the waiting thread is about to ask the engine for a component. */
    val waiting = AtomicInt(0)

    /** How long a [LockHoldingInitializer] keeps the lock once the waiting thread has asked. */
    var holdMillis: Long = DEFAULT_HOLD_MILLIS

    /** What a [LockReentrantInitializer]'s call back into the engine came to. */
    val outcome = AtomicReference<String?>(null)

    /** Forgets the previous test's runtime, signals and outcome. */
    fun reset() {
        appInitializer = null
        holding.value = 0
        waiting.value = 0
        holdMillis = DEFAULT_HOLD_MILLIS
        outcome.value = null
    }

    private const val DEFAULT_HOLD_MILLIS = 50L
}

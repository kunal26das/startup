package io.github.kunal26das.startup

import java.util.concurrent.atomic.AtomicInteger

/** Counts creations across every thread, which a plain list could not do safely. */
object ConcurrentCounter {

    private val creations = AtomicInteger()

    /** How many components have been created since the last [reset]. */
    val created: Int get() = creations.get()

    /** Records one creation. */
    fun record() {
        creations.incrementAndGet()
    }

    /** Forgets everything recorded so far. */
    fun reset() {
        creations.set(0)
    }
}

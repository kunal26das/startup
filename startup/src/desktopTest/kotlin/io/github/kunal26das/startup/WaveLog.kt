package io.github.kunal26das.startup

import java.util.concurrent.ConcurrentLinkedQueue

/** Records what each component did, in the order it happened, across every thread. */
object WaveLog {

    private val events = ConcurrentLinkedQueue<String>()

    /** Everything recorded since the last [reset]. */
    val recorded: List<String> get() = events.toList()

    /** Records one event. */
    fun record(event: String) {
        events.add(event)
    }

    /** Forgets everything recorded so far. */
    fun reset() {
        events.clear()
    }
}

package io.github.kunal26das.startup

/**
 * A one-shot claim, taken atomically.
 *
 * [StartupTask] takes one before it runs, to detect a [WaveRunner] that invokes the same
 * task twice. A plain read-then-write flag would only catch the sequential shape, which is
 * the harmless one: two threads entering `invoke` before either finished would both pass
 * it, and the component would be created twice with nothing left to report it.
 */
internal expect class StartupOnce() {

    /** Takes the claim, returning true only for the caller that took it first. */
    fun claim(): Boolean
}

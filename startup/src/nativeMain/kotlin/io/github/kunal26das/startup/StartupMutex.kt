package io.github.kunal26das.startup

/**
 * A recursive mutex that parks the threads waiting for it.
 *
 * [StartupLock] on Kotlin/Native is built on this the way the JVM one is built on
 * `java.util.concurrent.locks.ReentrantLock`: [tryLock] first, so that a thread inside a
 * [StartupTask] can be refused rather than left waiting, and [lock] only for a thread whose
 * wait will end. A waiting thread sleeps in the kernel instead of spinning a core for as long
 * as the install it waits for runs.
 *
 * Each native family supplies its own actual over a recursive pthread mutex, because the
 * cinterop types are not the same shape on every family: the mutex is a struct on Apple and
 * Linux and an integer handle on mingw, and the mutex-type constant is an `Int` on Apple and
 * mingw and has to be converted on Linux.
 */
internal expect class StartupMutex() {

    /** Takes the mutex without waiting, returning false when another thread holds it. */
    fun tryLock(): Boolean

    /** Takes the mutex, parking this thread until another thread releases it. */
    fun lock()

    /** Releases one hold of the mutex, which this thread must own. */
    fun unlock()
}

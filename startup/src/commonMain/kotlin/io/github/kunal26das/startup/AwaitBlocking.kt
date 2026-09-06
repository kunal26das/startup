package io.github.kunal26das.startup

/**
 * Runs [block] to completion on the calling thread.
 *
 * `runBlocking` where a target has one, and a [StartupException] where it does not.
 * Kotlin/JS and Kotlin/Wasm have one thread and no way to park it, which is the same
 * reason the lock every engine entry point runs under is a no-op on those two targets.
 */
internal expect fun <T> awaitBlocking(block: suspend () -> T): T

package io.github.kunal26das.startup

/**
 * The mutual exclusion that [AppInitializer] holds while it plans and creates components.
 *
 * AndroidX serializes every initialization inside one lock, so two threads asking for the
 * same component receive the same instance and `create` runs exactly once. Shared code
 * cannot behave differently off Android, which is why this exists.
 *
 * The lock is reentrant: [Initializer.create] is allowed to call back into
 * [AppInitializer.initializeComponent], and that nested call must not deadlock. Kotlin/JS
 * and Kotlin/Wasm are single-threaded, so there the whole thing collapses to a direct
 * call.
 */
internal expect class StartupLock() {

    /** Runs [block] with the lock held, releasing it however [block] finishes. */
    fun <T> withLock(block: () -> T): T
}

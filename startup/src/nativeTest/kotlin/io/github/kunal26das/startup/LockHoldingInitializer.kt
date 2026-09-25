package io.github.kunal26das.startup

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Keeps the engine lock until another thread has asked the engine for something, so that
 * thread's wait happens on the lock rather than before or after it.
 */
class LockHoldingInitializer : BaseInitializer<Unit>() {

    /** Signals that it holds the lock, waits for the other thread to ask, then holds on. */
    override fun create(context: Context) {
        TestLog.record(NAME)
        LockProbe.holding.value = 1
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (LockProbe.waiting.value == 0) delay(1)
            }
            delay(LockProbe.holdMillis)
        }
    }

    /** What this component records. */
    companion object {

        /** What this component records, so a test can assert on it without a literal. */
        const val NAME = "holding"

        private const val TIMEOUT_MILLIS = 10_000L
    }
}

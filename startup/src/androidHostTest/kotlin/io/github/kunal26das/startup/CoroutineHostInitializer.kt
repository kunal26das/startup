package io.github.kunal26das.startup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A [CoroutineInitializer] whose work really suspends and really finishes on another
 * thread, so a `create` that returned before [createAsync] completed could not pass for
 * one that ran it.
 */
class CoroutineHostInitializer : CoroutineInitializer<String> {

    /** Suspends, resumes elsewhere, and names the thread that finished the work. */
    override suspend fun createAsync(context: Context): String {
        val worker = withContext(Dispatchers.Default) {
            delay(DELAY_MILLIS)
            Thread.currentThread().name
        }
        return "$PRODUCT:$worker"
    }

    /** What this component produces, and how long it suspends for. */
    companion object {

        /** The prefix of the component this initializer produces. */
        const val PRODUCT = "created"

        private const val DELAY_MILLIS = 50L
    }
}

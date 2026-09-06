package io.github.kunal26das.startup

import kotlinx.coroutines.delay

/** Suspends in the middle of its work, which is what a real SDK's initializer does. */
class CoroutineSlowInitializer : CoroutineInitializer<Any> {

    /** Records both ends of the work, so a test can see whether anything ran between them. */
    override suspend fun createAsync(context: Context): Any {
        WaveLog.record(STARTED)
        delay(DELAY_MILLIS)
        WaveLog.record(FINISHED)
        return Any()
    }

    /** Both ends of the suspending work, and how long it takes. */
    companion object {

        /** Recorded before this component suspends. */
        const val STARTED = "slow:started"

        /** Recorded after this component resumes. */
        const val FINISHED = "slow:finished"

        private const val DELAY_MILLIS = 50L
    }
}

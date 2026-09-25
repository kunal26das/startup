package io.github.kunal26das.startup

/**
 * Throws a [StartupException] that names no component, as the Kotlin/JS and Kotlin/Wasm
 * refusal of a [CoroutineInitializer] does.
 */
class BareFailureInitializer : BaseInitializer<Unit>() {

    /** Records the attempt, then throws. */
    override fun create(context: Context) {
        TestLog.record("bareFailure")
        throw StartupException(MESSAGE)
    }

    /** What the thrown exception says. */
    companion object {

        /** The message of the exception [create] throws. */
        const val MESSAGE = "bare failure"
    }
}

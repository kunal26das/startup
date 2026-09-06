package io.github.kunal26das.startup

/** Throws from [create], to prove failures are wrapped and stop the run. */
class FailingInitializer : BaseInitializer<Unit>() {

    /** Records the attempt, then throws. */
    override fun create(context: Context) {
        TestLog.record("failing")
        throw IllegalStateException("failing initializer")
    }
}

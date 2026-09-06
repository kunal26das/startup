package io.github.kunal26das.startup

/**
 * Creates slowly on purpose, so that a second thread reaches the runtime while this one is
 * still inside [create]. That is the window an unsynchronized runtime loses a component
 * through.
 */
class ConcurrentBInitializer : BaseInitializer<Any>() {

    /** Records the creation, dawdles, and returns an instance only equal to itself. */
    override fun create(context: Context): Any {
        ConcurrentCounter.record()
        Thread.sleep(1)
        return Any()
    }
}

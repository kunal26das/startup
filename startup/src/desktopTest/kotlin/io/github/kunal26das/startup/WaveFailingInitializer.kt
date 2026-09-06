package io.github.kunal26das.startup

/** Fails inside the wave, taking the install down with it. */
class WaveFailingInitializer : BaseInitializer<Any>() {

    /** Records the attempt and throws. */
    override fun create(context: Context): Any {
        WaveLog.record(NAME)
        throw IllegalStateException(NAME)
    }

    /** What this component records, and the message it throws. */
    companion object {

        /** What this component records, and the message it throws. */
        const val NAME = "failing"
    }
}

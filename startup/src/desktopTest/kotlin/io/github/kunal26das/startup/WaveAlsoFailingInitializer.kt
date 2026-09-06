package io.github.kunal26das.startup

/** Fails in the same wave as [WaveFailingInitializer], so that a wave can have two failures. */
class WaveAlsoFailingInitializer : BaseInitializer<Any>() {

    /** Records the attempt and throws. */
    override fun create(context: Context): Any {
        WaveLog.record(NAME)
        throw IllegalStateException(NAME)
    }

    /** What this component records. */
    companion object {

        /** What this component records, and the message it throws. */
        const val NAME = "alsoFailing"
    }
}

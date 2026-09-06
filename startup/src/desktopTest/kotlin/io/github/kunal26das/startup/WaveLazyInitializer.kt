package io.github.kunal26das.startup

/** Registered lazily, so only a component that asks for it by hand brings it into being. */
class WaveLazyInitializer : BaseInitializer<Any>() {

    /** Records one creation and returns an instance only equal to itself. */
    override fun create(context: Context): Any {
        WaveLog.record(NAME)
        return Any()
    }

    /** What this component records. */
    companion object {

        /** What this component records. */
        const val NAME = "lazy"
    }
}

package io.github.kunal26das.startup

/** Succeeds, and records that it did. The component beside it in the wave is the one that fails. */
class WaveOkInitializer : BaseInitializer<Any>() {

    /** Records one creation and returns an instance only equal to itself. */
    override fun create(context: Context): Any {
        WaveLog.record(NAME)
        return Any()
    }

    /** What this component records. */
    companion object {

        /** What this component records, so a test can assert on it without a literal. */
        const val NAME = "ok"
    }
}

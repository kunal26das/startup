package io.github.kunal26das.startup

/** A leaf with no dependencies, written against [BaseInitializer]. */
class AlphaInitializer : BaseInitializer<String>() {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("alpha")
        return "alpha"
    }
}

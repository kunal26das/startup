package io.github.kunal26das.startup

/**
 * A leaf that depends on nothing and that nothing depends on, so its position in a plan
 * can only come from where it was declared.
 */
class IndependentBInitializer : BaseInitializer<String>() {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("independentB")
        return "independentB"
    }
}

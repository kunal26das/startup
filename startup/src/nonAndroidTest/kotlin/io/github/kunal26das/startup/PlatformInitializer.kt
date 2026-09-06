package io.github.kunal26das.startup

/** The non-Android half of the shape, shared by all ten non-Android targets. */
actual class PlatformInitializer actual constructor() : BaseInitializer<String>() {

    /** Records the creation and returns the component. */
    actual override fun create(context: Context): String {
        TestLog.record("platform")
        return "nonAndroid"
    }
}

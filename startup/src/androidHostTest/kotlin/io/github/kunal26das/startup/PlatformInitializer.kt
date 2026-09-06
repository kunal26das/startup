package io.github.kunal26das.startup

/** The Android half of the shape, which AndroidX has to be able to reflect on. */
actual class PlatformInitializer actual constructor() : BaseInitializer<String>() {

    /** Records the creation and returns the component. */
    actual override fun create(context: Context): String {
        TestLog.record("platform")
        return "android"
    }
}

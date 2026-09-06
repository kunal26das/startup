package io.github.kunal26das.startup

/** The Android half of the memberless shape, which AndroidX has to be able to reflect on. */
actual class MemberlessInitializer actual constructor() : BaseInitializer<String>() {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("memberless")
        return "android"
    }
}

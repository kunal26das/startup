package io.github.kunal26das.startup

/** The non-Android half of the memberless shape, shared by all ten non-Android targets. */
actual class MemberlessInitializer actual constructor() : BaseInitializer<String>() {

    /** Records the creation and returns the component. */
    override fun create(context: Context): String {
        TestLog.record("memberless")
        return "nonAndroid"
    }
}

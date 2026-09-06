package io.github.kunal26das.startup

/**
 * Reports which factory built it, so a test can tell a component a later install
 * re-registered apart from an instance an earlier failed run left behind.
 */
class ReplaceableInitializer(private val tag: String = "default") : BaseInitializer<String>() {

    /** Records the tag its factory chose and returns it. */
    override fun create(context: Context): String {
        TestLog.record("replaceable:$tag")
        return tag
    }
}

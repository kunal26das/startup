package io.github.kunal26das.startup

/** Deliberately never registered, so that a dependency on it fails to resolve. */
class OrphanInitializer : BaseInitializer<Unit>() {

    /** Never runs. */
    override fun create(context: Context) {
        TestLog.record("orphan")
    }
}

package io.github.kunal26das.startup

/** Records what the runtime created, in order, so a test can assert on it. */
object TestLog {

    private val entries = mutableListOf<String>()

    /** Everything recorded since the last [clear], oldest first. */
    val created: List<String> get() = entries.toList()

    /** Appends [name] to the log. */
    fun record(name: String) {
        entries.add(name)
    }

    /** Empties the log. */
    fun clear() {
        entries.clear()
    }
}

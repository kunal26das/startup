package io.github.kunal26das.startup.sample

/** A trivial logger, the component everything else in the sample depends on. */
class Logger {

    private val entries = mutableListOf<String>()

    private val created = mutableListOf<String>()

    /** Everything logged so far, oldest first. */
    val messages: List<String> get() = entries.toList()

    /**
     * What [ready] recorded, in the order the components were actually created.
     *
     * This is the report's initialization order. It is a list appended to once per
     * component, from inside that component's `create`, rather than something inferred
     * from the shape of the messages, so an unrelated log line cannot join it and a
     * component that stops logging cannot silently leave it.
     */
    val initialized: List<String> get() = created.toList()

    /** Appends [message] to the log. */
    fun log(message: String) {
        entries.add(message)
    }

    /** Logs that [component] finished initializing and records it as the next one created. */
    fun ready(component: String) {
        val message = "$component ready"
        entries.add(message)
        created.add(message)
    }
}

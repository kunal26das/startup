package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/**
 * Creates the [Logger]. It has no dependencies, so it extends
 * [io.github.kunal26das.startup.BaseInitializer] and inherits an empty dependency list.
 */
class LoggerInitializer : BaseInitializer<Logger>() {

    /** Builds the logger. */
    override fun create(context: StartupContext): Logger = Logger().apply { ready("logger") }
}

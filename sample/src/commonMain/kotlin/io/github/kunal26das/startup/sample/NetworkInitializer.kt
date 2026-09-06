package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.initializerKey

/**
 * Creates the [Network]. Reading a dependency back through
 * [io.github.kunal26das.startup.AppInitializer.initializeComponent] from inside
 * [create] is the pattern AndroidX documents, and it works here unchanged.
 */
class NetworkInitializer : Initializer<Network> {

    /** Builds the network stack on top of the logger. */
    override fun create(context: StartupContext): Network {
        val logger = Startup.getInstance(context)
            .initializeComponent(initializerKey<LoggerInitializer>())
        return Network(logger).also { logger.ready("network") }
    }

    /** Requires [LoggerInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<LoggerInitializer>())
}

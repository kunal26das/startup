package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.initializerKey

/** Creates the [Analytics] client once the logger and the network stack exist. */
class AnalyticsInitializer : Initializer<Analytics> {

    /** Builds the analytics client from its two dependencies. */
    override fun create(context: StartupContext): Analytics {
        val appInitializer = Startup.getInstance(context)
        val logger = appInitializer.initializeComponent(initializerKey<LoggerInitializer>())
        val network = appInitializer.initializeComponent(initializerKey<NetworkInitializer>())
        return Analytics(logger, network).also { logger.ready("analytics") }
    }

    /** Requires [LoggerInitializer] and [NetworkInitializer]. */
    override fun dependencies(): List<AnyInitializerKey> = listOf(
        initializerKey<LoggerInitializer>(),
        initializerKey<NetworkInitializer>(),
    )
}

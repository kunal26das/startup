package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.initializerKey

/**
 * Starts crash reporting on the platforms that have no `android.content.Context`. The SDK
 * itself is chosen by [Platform], which is resolved per target rather than per family.
 */
actual class CrashReportingInitializer actual constructor() : Initializer<CrashReporting> {

    /** Starts the platform SDK and wires it to the shared logger. */
    actual override fun create(context: StartupContext): CrashReporting {
        val logger = Startup.getInstance(context).initializeComponent(initializerKey<LoggerInitializer>())
        return CrashReporting(Platform.crashReportingSdk, logger)
            .also { logger.ready("crash reporting") }
    }

    /** Requires [LoggerInitializer]. */
    actual override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoggerInitializer>())
}

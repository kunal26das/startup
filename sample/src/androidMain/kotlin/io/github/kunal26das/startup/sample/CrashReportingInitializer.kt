package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.initializerKey

/**
 * Starts crash reporting on Android, where [StartupContext] is a real
 * `android.content.Context` and the platform SDK can be configured from it.
 */
actual class CrashReportingInitializer actual constructor() : Initializer<CrashReporting> {

    /** Starts the Android SDK and wires it to the shared logger. */
    actual override fun create(context: StartupContext): CrashReporting {
        val logger = Startup.getInstance(context).initializeComponent(initializerKey<LoggerInitializer>())
        return CrashReporting("AndroidCrashReporter(${context.packageName})", logger)
            .also { logger.ready("crash reporting") }
    }

    /** Requires [LoggerInitializer]. */
    actual override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoggerInitializer>())
}

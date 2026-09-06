package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.initializerKey

/**
 * Renders what the shared startup graph actually did, as lines a human can read.
 *
 * Every platform's entry point prints or displays exactly these lines, so the eleven
 * builds can be held side by side and compared. Nothing here is platform specific: the
 * only thing that changes between targets is the crash-reporting SDK the report names,
 * which is the point it exists to show.
 */
object SampleReport {

    /**
     * The report for the graph reachable from [context].
     *
     * The components must already be registered before this is called: off Android by
     * [SampleStartup.bootstrap], on Android by `androidx.startup.InitializationProvider`
     * reading the merged AndroidManifest. Resolving the analytics client is what forces
     * the rest of the graph into existence if a lazy consumer got here first.
     *
     * The initialization order is [Logger.initialized], which each component appends to
     * from inside its own `create`. It is therefore the order the components were built
     * in, not an order recovered afterwards by reading the log.
     */
    fun lines(context: StartupContext): List<String> {
        val analytics = SampleStartup.analytics(context)
        val crashReporting = SampleStartup.crashReporting(context)
        val runtimeInfo = SampleStartup.runtimeInfo(context)
        val logger = analytics.logger
        analytics.track(EVENT)
        return buildList {
            add(TITLE)
            add("")
            add("registered components")
            for ((name, component) in COMPONENTS) {
                val eagerness = if (SampleStartup.manifest.isEager(component)) "eager" else "lazy"
                add("  " + name.padEnd(COLUMN) + eagerness)
            }
            add("")
            add("initialization order")
            logger.initialized.forEachIndexed { index, message -> add("  ${index + 1}  $message") }
            add("")
            add("crash reporting sdk")
            add("  ${crashReporting.sdk}")
            add("")
            add("startup runtime")
            add("  ${runtimeInfo.name}")
            add("")
            add("logger after analytics.track(\"$EVENT\")")
            for (message in logger.messages) add("  $message")
        }
    }

    private const val TITLE = "kotlin multiplatform app startup sample"

    private const val EVENT = "launch"

    private const val COLUMN = 28

    private val COMPONENTS: List<Pair<String, AnyInitializerKey>> = listOf(
        "LoggerInitializer" to initializerKey<LoggerInitializer>(),
        "NetworkInitializer" to initializerKey<NetworkInitializer>(),
        "AnalyticsInitializer" to initializerKey<AnalyticsInitializer>(),
        "CrashReportingInitializer" to initializerKey<CrashReportingInitializer>(),
        "RuntimeInfoInitializer" to initializerKey<RuntimeInfoInitializer>(),
    )
}

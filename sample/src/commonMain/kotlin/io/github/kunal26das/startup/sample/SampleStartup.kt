package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AppInitializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.StartupManifest
import io.github.kunal26das.startup.initializerKey

/**
 * The whole bootstrap of the sample application, written once in shared code and compiled
 * for Android and for the other ten targets without a single platform branch.
 */
object SampleStartup {

    /**
     * What the application initializes and how eagerly. [AnalyticsInitializer] is eager,
     * so it and everything it needs are created at startup; the other two are pulled in
     * as dependencies and are also reachable on their own.
     *
     * Every factory here constructs its initializer with no arguments. That is not a
     * style choice: on Android AndroidX ignores these factories and reflects on the class
     * instead, so a factory that passed constructor arguments would work on the other ten
     * targets and throw on Android.
     */
    val manifest: StartupManifest = StartupManifest {
        metaData<AnalyticsInitializer> { AnalyticsInitializer() }
        metaData<CrashReportingInitializer> { CrashReportingInitializer() }
        metaData<RuntimeInfoInitializer> { RuntimeInfoInitializer() }
        lazyInitializer<NetworkInitializer> { NetworkInitializer() }
        lazyInitializer<LoggerInitializer> { LoggerInitializer() }
    }

    /**
     * Installs [manifest] and eagerly creates everything it marks as eager. On Android
     * this is the programmatic alternative to listing the components in the
     * AndroidManifest; off Android it is the only way to register them.
     */
    fun bootstrap(context: StartupContext): AppInitializer = Startup.install(context, manifest)

    /** The analytics client, creating it and its dependencies if that has not happened. */
    fun analytics(context: StartupContext): Analytics =
        Startup.getInstance(context).initializeComponent(initializerKey<AnalyticsInitializer>())

    /** The crash reporter, whose implementation is chosen per platform. */
    fun crashReporting(context: StartupContext): CrashReporting =
        Startup.getInstance(context).initializeComponent(initializerKey<CrashReportingInitializer>())

    /** Which runtime planned and created the graph, per platform. */
    fun runtimeInfo(context: StartupContext): RuntimeInfo =
        Startup.getInstance(context).initializeComponent(initializerKey<RuntimeInfoInitializer>())
}

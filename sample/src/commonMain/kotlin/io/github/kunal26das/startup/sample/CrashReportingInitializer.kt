package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.StartupContext

/**
 * Starts the platform crash-reporting SDK.
 *
 * This is the second shape an initializer can take. [AnalyticsInitializer] is written once
 * in shared code because nothing it does is platform specific; this one is `expect`,
 * because starting Crashlytics, a Cocoa reporter and a browser reporter are genuinely
 * different calls. Both shapes register in the same [SampleStartup.manifest] and, on
 * Android, both compile to `implements androidx.startup.Initializer`.
 *
 * The name is one fully qualified name on every target, so a single AndroidManifest
 * `<meta-data>` entry addresses it no matter which `actual` is compiled in.
 *
 * Every `actual` must be a class with a public no-argument constructor. AndroidX ignores
 * the factory in the manifest object and reflects with
 * `getDeclaredConstructor().newInstance()`, so an `actual object` would work on the other
 * ten targets and throw on Android.
 */
expect class CrashReportingInitializer() : Initializer<CrashReporting> {

    /** Starts the platform SDK and returns the shared component wrapping it. */
    override fun create(context: StartupContext): CrashReporting

    /** Requires [LoggerInitializer] on every platform. */
    override fun dependencies(): List<AnyInitializerKey>
}

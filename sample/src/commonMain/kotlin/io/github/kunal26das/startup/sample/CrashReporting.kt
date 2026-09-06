package io.github.kunal26das.startup.sample

/**
 * A crash reporter backed by whichever native SDK the current platform ships.
 *
 * The component is shared; only the SDK behind it differs, which is why
 * [CrashReportingInitializer] is declared `expect` rather than written once.
 */
class CrashReporting(
    /** Identifies the platform SDK that was started, for example `AppleCrashReporter`. */
    val sdk: String,
    private val logger: Logger,
) {

    /** Reports [error] through the platform SDK. */
    fun report(error: String) {
        logger.log("crash $error via $sdk")
    }
}

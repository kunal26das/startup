package io.github.kunal26das.startup.sample

/**
 * The platform an initializer is running on.
 *
 * Declared in `nonAndroidMain` rather than `commonMain`: Android needs no entry here
 * because its `actual` initializer reads everything it wants from the real context. An
 * `expect` may live in an intermediate source set, and this is what that is for.
 */
expect object Platform {

    /** The crash-reporting SDK this target ships. */
    val crashReportingSdk: String
}

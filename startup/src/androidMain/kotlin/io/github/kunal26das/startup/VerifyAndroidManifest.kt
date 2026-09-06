package io.github.kunal26das.startup

/**
  * Deprecated, and removed in 2.0.0: androidx.startup has no counterpart for this, and this
  * library's contract is to mirror it. See the annotation for what to do instead.
  *
 * Throws unless the AndroidManifest of [context]'s package declares exactly the components
 * this manifest marks eager, as reported by [androidManifestDrift].
 *
 * This is the runtime form of the parity test a consumer would otherwise have to write,
 * and it exists because the failure it catches is silent and Android only: on the other
 * ten targets the [StartupManifest] is the whole registry, while on Android AndroidX reads
 * the XML and ignores these factories entirely. Call it from a debug build and a
 * `<meta-data>` line somebody forgot becomes a launch-time failure with the missing names
 * in the message rather than a component that quietly never runs.
 *
 * @throws StartupException listing every disagreement, one per line.
 */
@Deprecated(
    message = "androidx.startup has no counterpart for this, and this library's contract is to " +
        "mirror androidx.startup. Removed in 2.0.0. Keep the AndroidManifest as the single " +
        "source of truth on Android and write its <meta-data> entries by hand. The failure it " +
        "catches is real, so a consumer that wants the two registries held in step keeps a test " +
        "of its own.",
    level = DeprecationLevel.WARNING,
)
@Suppress("DEPRECATION")
fun StartupManifest.verifyAndroidManifest(context: Context) {
    val drift = androidManifestDrift(context)
    if (drift.isEmpty()) return
    throw StartupException(
        drift.joinToString(
            separator = "\n",
            prefix = "The StartupManifest and the AndroidManifest of ${context.packageName} " +
                "do not agree.\n",
        ),
    )
}

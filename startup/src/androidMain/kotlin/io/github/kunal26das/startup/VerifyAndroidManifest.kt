package io.github.kunal26das.startup

/**
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

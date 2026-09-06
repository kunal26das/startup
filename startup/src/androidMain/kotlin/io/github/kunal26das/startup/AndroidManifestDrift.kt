package io.github.kunal26das.startup

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.startup.InitializationProvider

/**
 * Every disagreement between this manifest and the `<meta-data>` entries
 * `androidx.startup.InitializationProvider` actually declares in the merged AndroidManifest
 * of [context]'s package, one line each, empty when the two agree.
 *
 * This reads the same registry AndroidX itself reads at process start, through
 * `PackageManager.getProviderInfo(..., GET_META_DATA)`, so it reports what the running
 * application really does rather than what a source file says. Names AndroidX would
 * discover but this manifest has never heard of are not reported: an application may
 * declare initializers written directly against `androidx.startup` beside these.
 *
 * The check costs one `PackageManager` lookup and creates nothing. Run it from a
 * debug-only initializer, or from an instrumentation test, and a component added to
 * [StartupManifest] but forgotten in the AndroidManifest stops being an omission nobody
 * notices. Use [verifyAndroidManifest] to turn the same answer into a failure.
 */
fun StartupManifest.androidManifestDrift(context: Context): List<String> {
    val provider = ComponentName(context.packageName, InitializationProvider::class.java.name)
    val info = try {
        @Suppress("DEPRECATION")
        context.packageManager.getProviderInfo(provider, PackageManager.GET_META_DATA)
    } catch (exception: PackageManager.NameNotFoundException) {
        throw StartupException(
            "Cannot read ${provider.className} from the AndroidManifest of ${context.packageName}. " +
                "androidx.startup contributes that provider through its own manifest, so a package " +
                "without it either removed it or never merged the androidx.startup AAR, and no " +
                "component is created at process start.",
            exception,
        )
    }
    val metaData = info.metaData ?: return androidManifestDrift(emptySet())
    val declared = metaData.keySet()
        .filter { metaData.getString(it) == "androidx.startup" }
        .toSet()
    return androidManifestDrift(declared)
}

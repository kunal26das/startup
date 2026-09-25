package io.github.kunal26das.startup

/**
 * Installs a manifest of its own from inside [create], the way a feature module registers the
 * graph it brings with it.
 */
class NestedInstallInitializer : BaseInitializer<String>() {

    /** Installs [AlphaInitializer] as an eager component, then records its own creation. */
    override fun create(context: Context): String {
        Startup.install(
            context,
            StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } },
        )
        TestLog.record("nestedInstall")
        return "nestedInstall"
    }
}

package io.github.kunal26das.startup

/** Bridges shared code to `androidx.startup.AppInitializer`'s static factory. */
actual object Startup {

    /**
     * Eagerly creates everything [manifest] marks as eager, which is the programmatic
     * equivalent of listing those components in the AndroidManifest. The factories in
     * [manifest] are not called: AndroidX instantiates each class reflectively through
     * its public no-argument constructor.
     *
     * AndroidX answers [AppInitializer.isEagerlyInitialized] from what
     * `InitializationProvider` discovered in the AndroidManifest, so a component started
     * from here alone still reports `false`. Paste
     * [StartupManifest.androidManifestMetadata] into the manifest to make the two agree.
     */
    actual fun install(context: Context, manifest: StartupManifest): AppInitializer {
        val instance = AppInitializer.getInstance(context)
        for (component in manifest.eagerComponents) {
            @Suppress("UNCHECKED_CAST")
            val typed = component as InitializerKey<out Initializer<Any>>
            instance.initializeComponent(typed)
        }
        return instance
    }

    /** AndroidX's process-wide singleton, unchanged. */
    actual fun getInstance(context: Context): AppInitializer = AppInitializer.getInstance(context)
}

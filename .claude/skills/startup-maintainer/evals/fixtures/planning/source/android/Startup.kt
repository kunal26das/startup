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
     * from here alone still reports `false`. Declare it as a `<meta-data>` entry in the
     * AndroidManifest to make the two agree.
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

    /**
     * Ignores [runner] and installs exactly as the other overload does.
     *
     * `androidx.startup` owns creation on Android and runs each component depth first on
     * the calling thread, with no seam to hand a wave anywhere else. Shared code may pass
     * a runner unconditionally: it takes effect on the other ten targets and changes
     * nothing here.
     */
    actual fun install(
        context: Context,
        manifest: StartupManifest,
        runner: WaveRunner,
    ): AppInitializer = install(context, manifest)

    /** AndroidX's process-wide singleton, unchanged. */
    actual fun getInstance(context: Context): AppInitializer = AppInitializer.getInstance(context)
}

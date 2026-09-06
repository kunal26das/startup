package io.github.kunal26das.startup

/** Owns the process-wide [AppInitializer] on the platforms that have no AndroidX. */
actual object Startup {

    private val lock = StartupLock(guardsWaveTasks = false)

    private var instance: AppInitializer? = null

    /**
     * Composes [manifest] into whatever is already installed, later entries winning, and
     * eagerly creates every component it marks as eager. This is the stand-in for
     * `InitializationProvider.onCreate`, which is what performs the same step on Android.
     */
    @Throws(StartupException::class)
    actual fun install(context: Context, manifest: StartupManifest): AppInitializer {
        val appInitializer = getInstance(context)
        appInitializer.engine.install(manifest)
        return appInitializer
    }

    /** Composes [manifest] in, handing each wave to [runner] rather than to this thread. */
    @Throws(StartupException::class)
    actual fun install(
        context: Context,
        manifest: StartupManifest,
        runner: WaveRunner,
    ): AppInitializer {
        val appInitializer = getInstance(context)
        appInitializer.engine.install(manifest, runner)
        return appInitializer
    }

    /** The [AppInitializer] for this process, created on first use. */
    @Throws(StartupException::class)
    actual fun getInstance(context: Context): AppInitializer = lock.withLock {
        instance ?: AppInitializer(context).also { instance = it }
    }

    internal fun reset(): Unit = lock.withLock {
        instance = null
    }
}

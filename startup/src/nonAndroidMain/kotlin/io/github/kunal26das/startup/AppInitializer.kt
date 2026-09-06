package io.github.kunal26das.startup

/**
 * Off Android this is the library's own runtime. It answers the same two questions
 * `androidx.startup.AppInitializer` answers, over a plan computed with Kahn's algorithm
 * instead of AndroidX's recursive walk.
 *
 * Obtain one from [Startup]. Every entry point is serialized by one reentrant lock, so a
 * component is created exactly once however many threads ask for it, exactly as AndroidX
 * guarantees on Android.
 */
actual class AppInitializer internal constructor(context: Context) {

    internal val engine = StartupEngine(context)

    /** Creates [component] and everything it depends on, or returns what was created before. */
    actual fun <T : Any> initializeComponent(component: InitializerKey<out Initializer<T>>): T =
        engine.initializeComponent(component)

    /** Whether the installed manifest marks [component] as eager. */
    actual fun isEagerlyInitialized(component: AnyInitializerKey): Boolean =
        engine.isEager(component)

    /**
     * Deprecated, and removed in 2.0.0: androidx.startup has no counterpart for this, and this
     * library's contract is to mirror it. See the annotation for what to do instead.
     *
     * Whether [component] has already been created.
     */
    @Deprecated(
        message = "androidx.startup has no counterpart for this, so it exists on ten targets and " +
            "not on Android, which is the asymmetry this library exists to avoid. Removed in " +
            "2.0.0. Record what you need from inside your own Initializer.create, which is what " +
            "the sample's SampleReport does.",
        level = DeprecationLevel.WARNING,
    )
    fun isInitialized(component: AnyInitializerKey): Boolean = engine.isInitialized(component)

    /**
      * Deprecated, and removed in 2.0.0: androidx.startup has no counterpart for this, and this
      * library's contract is to mirror it. See the annotation for what to do instead.
      *
     * The components created so far, in the order they were created. Deprecated; see the
     * annotation.
     */
    @Deprecated(
        message = "androidx.startup has no counterpart for this, so it exists on ten targets and " +
            "not on Android, which is the asymmetry this library exists to avoid. Removed in " +
            "2.0.0. Record the order from inside your own Initializer.create, which is what the " +
            "sample's SampleReport does.",
        level = DeprecationLevel.WARNING,
    )
    fun initializationOrder(): List<AnyInitializerKey> = engine.initializationOrder()

    /**
     * Deprecated, and removed in 2.0.0: androidx.startup has no counterpart for this, and this
     * library's contract is to mirror it. See the annotation for what to do instead.
     *
     * The manifest installed so far.
     */
    @Deprecated(
        message = "androidx.startup has no counterpart for this, so it exists on ten targets and " +
            "not on Android, which is the asymmetry this library exists to avoid. Removed in " +
            "2.0.0. Hold on to the StartupManifest you passed to Startup.install, or record what " +
            "you need from inside your own Initializer.create, which is what the sample's " +
            "SampleReport does.",
        level = DeprecationLevel.WARNING,
    )
    fun manifest(): StartupManifest = engine.manifest()
}

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

    /** Whether [component] has already been created. */
    fun isInitialized(component: AnyInitializerKey): Boolean = engine.isInitialized(component)

    /** The components created so far, in the order they were created. */
    fun initializationOrder(): List<AnyInitializerKey> = engine.initializationOrder()

    /** The manifest installed so far. */
    fun manifest(): StartupManifest = engine.manifest()
}

package io.github.kunal26das.startup

/**
 * The entry point to [AppInitializer].
 *
 * `androidx.startup.AppInitializer.getInstance` is a Java static, and a Java static has
 * no member for an `expect companion object` to match, so this object is the bridge that
 * lets shared code reach it. Inside an Android-only source set the verbatim AndroidX
 * spelling `AppInitializer.getInstance(context)` keeps working unchanged.
 */
expect object Startup {

    /**
     * Composes [manifest] into whatever is already installed, later entries winning, and
     * eagerly creates every component it marks as eager.
     *
     * On Android the AndroidManifest is still the registry AndroidX itself reads, and
     * every component is instantiated reflectively; the factories in [manifest] are only
     * used off Android. See [StartupManifest] for what that implies.
     *
     * Both runtimes always create a dependency before the component that declares it, but
     * they choose different valid orders for components that are independent of each
     * other: AndroidX walks each eager root depth first, while [StartupPlanner] emits
     * Kahn levels. Anything that must run before something else has to say so in
     * [Initializer.dependencies]; an order that only happens to hold on one platform will
     * not hold on the others.
     */
    fun install(context: Context, manifest: StartupManifest): AppInitializer

    /**
     * The [AppInitializer] for this process, creating it on first use. The context of the
     * first caller is the one handed to every [Initializer.create].
     */
    fun getInstance(context: Context): AppInitializer
}

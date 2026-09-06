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
     * Composes [manifest] in and eagerly creates every component it marks as eager,
     * handing each [StartupPlan] wave to [runner] instead of creating it on this thread.
     *
     * Everything the other overload guarantees still holds: a dependency is created
     * before the component that declares it, each component is created once, and the
     * results are readable through [AppInitializer.initializeComponent]. The only
     * difference is that the components of one wave, which by construction depend only on
     * earlier waves, are handed over together so [runner] may run them at the same time.
     *
     * On Android [runner] is ignored: `androidx.startup` creates each component itself,
     * depth first on the calling thread, and offers no seam to change that. A runner is
     * therefore a performance decision on the other ten targets and never a correctness
     * one, so an initializer that must run before another still has to say so in
     * [Initializer.dependencies]. See [WaveRunner] for what a task may not do.
     */
    fun install(
        context: Context,
        manifest: StartupManifest,
        runner: WaveRunner,
    ): AppInitializer

    /**
     * The [AppInitializer] for this process, creating it on first use. The context of the
     * first caller is the one handed to every [Initializer.create].
     */
    fun getInstance(context: Context): AppInitializer
}

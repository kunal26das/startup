package io.github.kunal26das.startup

/**
 * Creates components on demand, in dependency order.
 *
 * On Android this **is** `androidx.startup.AppInitializer`, so a shared call site and an
 * Android-only call site drive the very same object. Obtain one through [Startup],
 * because a static factory on a Java class cannot satisfy an `expect companion object`.
 *
 * The two members below are the whole of this type, on every target, because they are the
 * whole of what AndroidX exposes. Neither runtime reports what it has already created or
 * in what order: `androidx.startup.AppInitializer` has no equivalent and no accessible
 * state to derive one from, so answering it off Android alone was the one place this API
 * differed by platform. Record what you need from inside your own [Initializer.create],
 * and hold on to the [StartupManifest] you passed to [Startup.install].
 */
expect class AppInitializer {

    /**
     * Returns the component built by [component], creating it and everything it depends
     * on first if that has not happened yet. Results are cached: a second call for the
     * same key returns the same instance without running [Initializer.create] again, and
     * that holds however many threads call it at once, because both runtimes serialize
     * initialization behind a lock.
     *
     * The two runtimes disagree about a component that no [StartupManifest] registers.
     * AndroidX resolves any class reflectively and never consults a manifest, so on
     * Android this succeeds for an unregistered component and for one registered with
     * `remove`. Off Android the manifest is the only registry there is, so both throw
     * [StartupException]. Register every component you intend to resolve, or shared code
     * that passes on Android will fail everywhere else.
     */
    fun <T : Any> initializeComponent(component: InitializerKey<out Initializer<T>>): T

    /**
     * Whether [component] is initialized eagerly at startup, that is, whether it is
     * listed in the manifest rather than resolved on first use.
     */
    fun isEagerlyInitialized(component: AnyInitializerKey): Boolean
}

/**
 * [AppInitializer.initializeComponent] for a key whose component type is not known, and
 * for a component whose product may be absent.
 *
 * [AnyInitializerKey] is the element type of [Initializer.dependencies] and the type
 * [initializerKey] returns for an instance, so it is the only key a host that discovered
 * an initializer at run time — or wrote one in Swift — can build. It is not assignable to
 * the `InitializerKey<out Initializer<T>>` the typed read wants, which left that path able
 * to register a component and never to read it back.
 *
 * The return type is nullable for the same reason. A `create` may return null, which the
 * Objective-C export makes the natural thing for a Swift initializer to do, and the typed
 * read cannot represent it.
 */
expect fun AppInitializer.initializeComponentOrNull(component: AnyInitializerKey): Any?

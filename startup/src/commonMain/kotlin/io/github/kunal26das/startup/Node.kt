package io.github.kunal26das.startup

/**
 * One entry of a [StartupManifest], named after the AndroidManifest vocabulary it stands
 * in for so that the correspondence is self-documenting.
 */
sealed class Node {

    /**
     * A component that is initialized eagerly, the equivalent of
     * `<meta-data android:name="FQCN" android:value="androidx.startup" />` inside the
     * `androidx.startup.InitializationProvider` block.
     *
     * @property factory builds the component's [Initializer]. [Startup.install] calls it at
     * most once per run, and only off Android, where AndroidX would instead instantiate the
     * class reflectively. [StartupPlanner] calls it on every target including Android,
     * because reading `dependencies()` needs an instance.
     */
    data class Merge(val factory: () -> Initializer<*>) : Node()

    /**
     * A component that is known but not listed in the manifest, so it is created only
     * when something asks for it. This is the state of any initializer an Android app
     * leaves out of its `InitializationProvider` block and resolves through
     * `AppInitializer.initializeComponent` instead.
     *
     * @property factory builds the component's [Initializer]. Called under the same rules
     * as [Merge.factory].
     */
    data class Lazy(val factory: () -> Initializer<*>) : Node()

    /**
     * A tombstone, the equivalent of `tools:node="remove"`. It hides an entry contributed
     * by an included manifest, so off Android the component is neither eager nor resolvable.
     * On Android it is only dropped from what [Startup.install] starts: AndroidX reads
     * `dependencies()` reflectively and never consults a [StartupManifest], so a tombstoned
     * component an eager component still depends on is created there anyway, while off
     * Android the same manifest fails to plan.
     */
    data object Remove : Node()
}

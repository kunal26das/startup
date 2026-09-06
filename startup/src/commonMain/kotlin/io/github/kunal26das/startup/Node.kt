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
     * @property factory builds the component's [Initializer]. Called at most once per
     * manifest, and only off Android: AndroidX instantiates the class reflectively.
     */
    data class Merge(val factory: () -> Initializer<*>) : Node()

    /**
     * A component that is known but not listed in the manifest, so it is created only
     * when something asks for it. This is the state of any initializer an Android app
     * leaves out of its `InitializationProvider` block and resolves through
     * `AppInitializer.initializeComponent` instead.
     *
     * @property factory builds the component's [Initializer]. Called at most once per
     * manifest, and only off Android.
     */
    data class Lazy(val factory: () -> Initializer<*>) : Node()

    /**
     * A tombstone, the equivalent of `tools:node="remove"`. It hides an entry contributed
     * by an included manifest, so the component is neither eager nor resolvable.
     */
    data object Remove : Node()
}

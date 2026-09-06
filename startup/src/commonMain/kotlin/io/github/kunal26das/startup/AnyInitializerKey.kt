package io.github.kunal26das.startup

/**
 * An [InitializerKey] for some unknown [Initializer] implementation.
 *
 * This is the element type of [Initializer.dependencies] and the key type of a
 * [StartupManifest]. The projection lives at the use site because [InitializerKey] has
 * to keep the exact arity and bound of `java.lang.Class` to stay aliasable on Android.
 */
typealias AnyInitializerKey = InitializerKey<out Initializer<*>>

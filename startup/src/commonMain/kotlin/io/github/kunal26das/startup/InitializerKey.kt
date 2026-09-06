package io.github.kunal26das.startup

import kotlin.reflect.KClass

/**
 * A class token identifying the [Initializer] implementation [T].
 *
 * On Android this **is** `java.lang.Class`, so a key produced here is exactly the token
 * `androidx.startup.AppInitializer` and `InitializationProvider` already expect. On the
 * other ten targets it is a small value-like wrapper around a [KClass] with equality and
 * a hash code, so it can be used as a map key.
 *
 * Build one with [initializerKey].
 */
expect class InitializerKey<T : Any>

/**
 * The [InitializerKey] for [T].
 *
 * Prefer this overload. Being `inline` and `reified`, it compiles down to a bare class
 * constant on Android, so a dependency list costs nothing at runtime.
 *
 * A `reified` type argument cannot cross the Objective-C boundary, so this overload is
 * hidden from the generated framework header rather than exported with [T] erased to its
 * bound. Swift and Objective-C callers use the [initializerKey] overload that takes an
 * [Initializer] instead.
 */
expect inline fun <reified T : Initializer<*>> initializerKey(): InitializerKey<T>

/**
 * The [InitializerKey] for [kClass], for the rare call site that only holds a [KClass].
 */
expect fun <T : Initializer<*>> initializerKey(kClass: KClass<T>): InitializerKey<T>

/**
 * The [InitializerKey] for the class of [initializer].
 *
 * This is the overload for a key that only exists at run time: an initializer a host
 * application passed in, or one written in Swift or Objective-C, where `::class` is the
 * only class token available and the `reified` overload cannot be called at all. Pair it
 * with [StartupManifestBuilder.metaData] or [StartupManifestBuilder.lazyInitializer] to
 * put such an initializer in the graph:
 *
 * ```
 * val manifest = StartupManifest {
 *     metaData(initializerKey(instance)) { instance }
 * }
 * ```
 *
 * The key it returns is the same key [initializerKey] would reify for the instance's
 * class, so a dependency declared against the class and an entry registered from an
 * instance name the same component.
 */
expect fun initializerKey(initializer: Initializer<*>): AnyInitializerKey

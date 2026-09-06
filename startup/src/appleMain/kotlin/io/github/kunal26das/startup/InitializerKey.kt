@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package io.github.kunal26das.startup

import kotlin.reflect.KClass
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCClass
import kotlinx.cinterop.getOriginalKotlinClass
import platform.Foundation.NSStringFromClass

/**
 * The [InitializerKey] for the Kotlin class behind the Objective-C class [objCClass].
 *
 * This is the overload that lets Swift name a Kotlin initializer without building one.
 * `KotlinKClass` is not obtainable from Swift, so the alternative is
 * `initializerKey(initializer: SomeInitializer())`, which runs the initializer's
 * constructor purely to read its class off the instance — and a constructor with a side
 * effect then fires just from naming the component.
 *
 * ```swift
 * func dependencies() -> [InitializerKey<Initializer>] {
 *     [InitializerKeyKt.initializerKey(objCClass: KoinInitializer.self)]
 * }
 * ```
 *
 * The key is the same one [initializerKey] would have reified for that class, so a
 * dependency declared from Swift and an entry registered from Kotlin name the same
 * component. Only a class the Kotlin framework exports has one, so a class declared in
 * Objective-C or Swift is rejected here rather than turned into a key that silently
 * matches nothing. A Kotlin class that is not an [Initializer] cannot be rejected —
 * Kotlin/Native exposes no supertypes on a [KClass] — and fails later, where every other
 * unregistered component fails, with the message naming it.
 *
 * @throws StartupException if [objCClass] is not a Kotlin class exported to Objective-C.
 */
fun initializerKey(objCClass: ObjCClass): AnyInitializerKey {
    val kClass = getOriginalKotlinClass(objCClass) ?: throw StartupException(
        "${NSStringFromClass(objCClass)} is not a Kotlin class, so it has no InitializerKey. " +
            "Only a class the Kotlin framework exports to Objective-C has one. An initializer " +
            "written in Swift is named with initializerKey(initializer:) from an instance the " +
            "host already holds.",
    )
    @Suppress("UNCHECKED_CAST")
    return InitializerKey(kClass as KClass<Initializer<*>>)
}

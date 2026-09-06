@file:OptIn(ExperimentalObjCRefinement::class)

package io.github.kunal26das.startup

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.reflect.KClass

/**
 * Off Android a key wraps a [KClass] and supplies the equality, hash code and name that
 * `java.lang.Class` provides on Android, so it can be used as a map key and can name
 * itself in a diagnostic.
 */
actual class InitializerKey<T : Any> @PublishedApi internal constructor(
    @PublishedApi internal val kClass: KClass<T>,
) {

    /** Two keys are equal when they name the same class. */
    override fun equals(other: Any?): Boolean = other is InitializerKey<*> && other.kClass == kClass

    /** Hashes by the wrapped class, so keys work in a `LinkedHashMap`. */
    override fun hashCode(): Int = kClass.hashCode()

    /**
     * The simple name of the wrapped class. Never `qualifiedName`: reading it is a
     * compile error on Kotlin/JS, and having the name here is what saves this library a
     * per-platform source set.
     */
    override fun toString(): String = kClass.simpleName ?: kClass.toString()
}

/**
 * Wraps the reified class in a key. Hidden from the generated Objective-C header, where
 * the type argument would be erased to [Initializer] and every call site would name the
 * same component.
 */
@HiddenFromObjC
actual inline fun <reified T : Initializer<*>> initializerKey(): InitializerKey<T> =
    InitializerKey(T::class)

/** Wraps [kClass] in a key. */
actual fun <T : Initializer<*>> initializerKey(kClass: KClass<T>): InitializerKey<T> =
    InitializerKey(kClass)

/** Wraps the runtime class of [initializer] in a key. */
actual fun initializerKey(initializer: Initializer<*>): AnyInitializerKey =
    InitializerKey(initializer::class)

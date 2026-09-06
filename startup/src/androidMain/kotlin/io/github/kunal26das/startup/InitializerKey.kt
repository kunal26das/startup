package io.github.kunal26das.startup

import kotlin.reflect.KClass

/** On Android a key is the very class token `androidx.startup` already works with. */
actual typealias InitializerKey<T> = java.lang.Class<T>

/** Inlines to a bare class constant, so a dependency list costs nothing at runtime. */
actual inline fun <reified T : Initializer<*>> initializerKey(): InitializerKey<T> = T::class.java

/** Unwraps the Kotlin class to the `java.lang.Class` token behind it. */
actual fun <T : Initializer<*>> initializerKey(kClass: KClass<T>): InitializerKey<T> = kClass.java

/** Reads the class token off the instance, which is what `::class.java` already is. */
actual fun initializerKey(initializer: Initializer<*>): AnyInitializerKey = initializer::class.java

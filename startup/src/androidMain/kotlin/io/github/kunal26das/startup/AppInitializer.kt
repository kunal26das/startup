package io.github.kunal26das.startup

/** On Android this is AndroidX's own runtime, reached through [Startup]. */
actual typealias AppInitializer = androidx.startup.AppInitializer

/**
 * Delegates straight to AndroidX, which resolves any class reflectively and returns what
 * `create` produced. The key's type argument is erased on Android, where an [InitializerKey]
 * **is** a `java.lang.Class`, so the cast reaches no further than the compiler.
 */
@Suppress("UNCHECKED_CAST")
actual fun AppInitializer.initializeComponentOrNull(component: AnyInitializerKey): Any? =
    initializeComponent(component as InitializerKey<out Initializer<Any>>)

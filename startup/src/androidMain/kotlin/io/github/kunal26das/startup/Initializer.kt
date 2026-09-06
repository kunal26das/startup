package io.github.kunal26das.startup

/**
 * On Android an initializer written in shared code **is** an `androidx.startup`
 * initializer: it compiles to `implements androidx.startup.Initializer`, so
 * `InitializationProvider` discovers and instantiates it with no adapter in between.
 */
actual typealias Initializer<T> = androidx.startup.Initializer<T>

package io.github.kunal26das.startup

/**
 * Creates and configures one component of an application at startup.
 *
 * On Android this **is** `androidx.startup.Initializer`: an implementation written in
 * `commonMain` compiles to `implements androidx.startup.Initializer`, so AndroidX
 * discovers it through `InitializationProvider` and instantiates it reflectively with
 * no adapter in between. On the other ten targets the library supplies its own runtime
 * that orders the same graph with Kahn's algorithm.
 *
 * Neither member can carry a default body, because an `expect` member may not have one
 * and an `actual` member may not add one. Extend [BaseInitializer] instead of
 * implementing this interface directly to inherit an empty [dependencies].
 */
expect interface Initializer<T> {

    /**
     * Builds the component. Every key returned from [dependencies] has already been
     * created by the time this runs, and can be read back with
     * `Startup.getInstance(context).initializeComponent(key)`.
     */
    fun create(context: Context): T

    /**
     * The components that must exist before [create] runs. Return [emptyList] when there
     * are none.
     */
    fun dependencies(): List<AnyInitializerKey>
}

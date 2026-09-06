package io.github.kunal26das.startup

/**
 * Off Android this is an ordinary interface with the same two members AndroidX declares,
 * so one implementation written in shared code satisfies both runtimes. Neither member
 * may carry a default body, because an `actual` member cannot add one where the `expect`
 * has none; extend [BaseInitializer] to inherit an empty [dependencies].
 */
actual interface Initializer<T> {

    /** Builds the component. */
    actual fun create(context: Context): T

    /** The components that must exist first. */
    actual fun dependencies(): List<AnyInitializerKey>
}

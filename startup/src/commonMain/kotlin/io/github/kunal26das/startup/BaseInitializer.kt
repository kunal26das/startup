package io.github.kunal26das.startup

/**
 * An [Initializer] with no dependencies.
 *
 * [Initializer] is an `expect interface`, and neither half of an `expect`/`actual` pair
 * is allowed to give a member a default body, so this class exists to supply the one
 * that every leaf initializer would otherwise have to repeat. It compiles to a plain
 * `implements androidx.startup.Initializer` on Android, exactly like a direct
 * implementation would.
 */
abstract class BaseInitializer<T> : Initializer<T> {

    /** No dependencies. Override to declare some. */
    override fun dependencies(): List<AnyInitializerKey> = emptyList()
}

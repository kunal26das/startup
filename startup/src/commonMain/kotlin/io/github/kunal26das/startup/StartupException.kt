package io.github.kunal26das.startup

/**
 * Thrown when a component cannot be created: a cycle in the dependency graph, a missing
 * registration, or a failure inside [Initializer.create].
 *
 * This is deliberately not an alias of `androidx.startup.StartupException`, which is
 * annotated `@RestrictTo(LIBRARY)` in the shipped artifact and would make every
 * consumer's `catch` clause fail lint's error-severity `RestrictedApi` check. Failures
 * raised by AndroidX itself still arrive as AndroidX's own type on Android, exactly as
 * they do in an app that uses `androidx.startup` directly.
 *
 * @property components the components the failure concerns, as data rather than as
 * text. For a cycle this is the cycle path, first element repeated last, so a caller can
 * inspect it instead of parsing [message].
 */
class StartupException(
    message: String,
    cause: Throwable? = null,
    val components: List<AnyInitializerKey> = emptyList(),
) : RuntimeException(message, cause)

package io.github.kunal26das.startup

/**
 * The [Context] to pass on platforms that have none of their own. It carries no state and
 * exists only so that [Initializer.create] can keep the signature Android needs.
 */
object DefaultContext : Context()

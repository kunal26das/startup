package io.github.kunal26das.startup

/**
 * An unambiguous spelling of [Context].
 *
 * `Context` collides with `android.content.Context` in any file that imports both, so
 * shared code and Android code alike can name the parameter type as `StartupContext`
 * and never hit "Conflicting import: imported name 'Context' is ambiguous".
 */
typealias StartupContext = Context

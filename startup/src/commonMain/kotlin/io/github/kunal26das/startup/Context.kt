package io.github.kunal26das.startup

/**
 * The platform handle passed to every [Initializer.create].
 *
 * On Android this **is** `android.content.Context`, which is what makes an initializer
 * written once in `commonMain` byte-compatible with `androidx.startup`. Because of that,
 * Android code should import `android.content.Context` and never this name: importing
 * both into a single file is a "Conflicting import" compile error. Shared code that has
 * to spell the type out can use the [StartupContext] alias instead.
 *
 * On the other ten targets this is an empty abstract class, and [DefaultContext] is the
 * instance the library hands to initializers that need nothing from the platform.
 */
expect abstract class Context()

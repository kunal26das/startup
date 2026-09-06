package io.github.kunal26das.startup

/** On Android the startup context is the platform context, with no wrapper in between. */
actual typealias Context = android.content.Context

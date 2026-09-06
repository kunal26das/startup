package io.github.kunal26das.startup

internal actual fun <T> awaitBlocking(block: suspend () -> T): T =
    throw StartupException(COROUTINE_INITIALIZER_UNSUPPORTED)

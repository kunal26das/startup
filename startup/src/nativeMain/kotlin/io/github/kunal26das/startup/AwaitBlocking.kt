package io.github.kunal26das.startup

import kotlinx.coroutines.runBlocking

internal actual fun <T> awaitBlocking(block: suspend () -> T): T = runBlocking { block() }

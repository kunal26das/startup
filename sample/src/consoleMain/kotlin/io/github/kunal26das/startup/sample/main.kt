package io.github.kunal26das.startup.sample

/**
 * The entry point of the sample application on every target whose report is read on a
 * console: the JVM desktop build, the five non-iOS Kotlin/Native builds, Kotlin/JS and
 * Kotlin/Wasm. It boots the shared startup graph and prints what it did.
 *
 * On JS and Wasm `println` reaches `console.log`, which the browser page mirrors into the
 * document, so the same function serves Node and the browser unchanged. iOS is the one
 * target that needs something else, because an app there is a UIKit process rather than a
 * program that prints and exits; its entry point is the `main` in `iosMain`.
 */
fun main() {
    for (line in SampleLauncher.report()) println(line)
}

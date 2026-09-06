package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/**
 * On Android the graph is planned and created by AndroidX itself, because every type in
 * this library is a `typealias` onto it there.
 *
 * The override is spelled `actual override`, because the `expect` redeclares `create` and
 * an expected member has to be actualised. Dropping `actual` here fails with *Declaration
 * must be marked with 'actual'*, and dropping the redeclaration from the `expect` instead
 * fails the metadata compilation; both halves have to stay as they are.
 */
actual class RuntimeInfoInitializer actual constructor() : BaseInitializer<RuntimeInfo>() {

    /** Names AndroidX, which is what actually ran. */
    actual override fun create(context: StartupContext): RuntimeInfo =
        RuntimeInfo("androidx.startup")
}

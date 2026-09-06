package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/**
 * On the other ten targets the graph is planned by this library's own `StartupPlanner`
 * and created by its own engine.
 *
 * One `actual` covers all ten, because nothing here is per target. The override is
 * spelled `actual override` for the reason the Android half explains.
 */
actual class RuntimeInfoInitializer actual constructor() : BaseInitializer<RuntimeInfo>() {

    /** Names this library, which is what actually ran. */
    actual override fun create(context: StartupContext): RuntimeInfo =
        RuntimeInfo("io.github.kunal26das.startup")
}

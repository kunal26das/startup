package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/**
 * Reports which runtime is executing the graph, and is the third shape an initializer can
 * take: `expect` over [BaseInitializer], so only `create` has to be redeclared.
 *
 * [AnalyticsInitializer] is shared because nothing it does is platform specific.
 * [CrashReportingInitializer] is `expect` over
 * [io.github.kunal26das.startup.Initializer] because it declares a dependency, so it has
 * to redeclare both members. This one declares no dependencies, so it inherits
 * `dependencies()` from [BaseInitializer] with a body and only names `create`.
 *
 * **The `override fun create` below is not optional, and dropping it is a compile error a
 * platform compilation cannot see.** An `expect class` with no body at all links and runs
 * on all eleven targets and is rejected by `compileCommonMainKotlinMetadata` with
 * *Class 'RuntimeInfoInitializer' is not abstract and does not implement abstract member*,
 * so this file is where that stays honest: `sample` is metadata-compiled by
 * `./gradlew build`, and `startup/src/commonTest` is not.
 *
 * It logs nothing, because a component with no dependencies has no [Logger] to log to and
 * resolving one anyway would be an undeclared edge. [SampleReport] gives it its own
 * section instead of a line in the initialization order.
 */
expect class RuntimeInfoInitializer() : BaseInitializer<RuntimeInfo> {

    /** Names the runtime this target's `actual` was compiled for. */
    override fun create(context: StartupContext): RuntimeInfo
}

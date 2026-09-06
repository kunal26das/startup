package io.github.kunal26das.startup

/**
 * The platform-specific initializer shape a consumer writes for a platform SDK: an
 * `expect class` extending [BaseInitializer], which already carries a concrete
 * `dependencies`, so only `create` has to be redeclared on both sides.
 *
 * This is the portable one, and the one README.md recommends by default. It is the
 * shortest `expect` that survives a metadata compilation as well as the eleven platform
 * compilations; [MemberlessInitializer] is shorter still and survives only the latter.
 * Redeclaring `create` here is what makes it an expected member, so both `actual`s spell
 * their override `actual override`.
 */
expect class PlatformInitializer() : BaseInitializer<String> {

    /** Starts the platform SDK and returns the component naming it. */
    override fun create(context: Context): String
}

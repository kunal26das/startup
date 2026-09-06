package io.github.kunal26das.startup

/**
 * Produces nothing.
 *
 * Off Android [Initializer] has an unbounded type argument, and the Objective-C export
 * gives a Swift author `Any?` to return, so an initializer that hands back nothing is both
 * expressible and idiomatic. It is what a component installed only for its side effect —
 * swizzling, a lifecycle callback, a logger — naturally does.
 */
class NullProductInitializer : BaseInitializer<Any?>() {

    /** Does its work, whatever that would be, and produces nothing. */
    override fun create(context: Context): Any? = null
}

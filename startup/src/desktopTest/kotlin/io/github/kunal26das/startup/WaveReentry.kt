package io.github.kunal26das.startup

/** The engine a re-entrant initializer should call back into, and what came of it. */
object WaveReentry {

    /** The runtime under test, set before an install and read from inside a `create`. */
    var appInitializer: AppInitializer? = null

    /** What the re-entrant call threw, if anything. */
    var failure: Throwable? = null

    /** Forgets the previous test's runtime and outcome. */
    fun reset() {
        appInitializer = null
        failure = null
    }
}

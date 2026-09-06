package io.github.kunal26das.startup.sample

/** The component at the tip of the sample graph. */
class Analytics(
    /** The logger this analytics client writes to. */
    val logger: Logger,
    private val network: Network,
) {

    /** Records [event] locally and ships it. */
    fun track(event: String) {
        logger.log("track $event")
        network.get("/events/$event")
    }
}

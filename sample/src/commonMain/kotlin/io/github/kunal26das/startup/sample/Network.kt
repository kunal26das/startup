package io.github.kunal26das.startup.sample

/** A stand-in network stack that needs a [Logger] to exist first. */
class Network(private val logger: Logger) {

    /** Pretends to fetch [path] and returns the response. */
    fun get(path: String): String {
        logger.log("GET $path")
        return "response:$path"
    }
}

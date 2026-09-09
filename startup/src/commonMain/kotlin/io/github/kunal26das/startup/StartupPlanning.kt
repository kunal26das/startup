package io.github.kunal26das.startup

/**
 * Tracks factory and dependency callbacks across nested plans for one engine. Paths are
 * recovered only on failure, so walking a deep acyclic graph remains linear. Creation
 * frames are captured between callbacks to retain every hop when planning and creation
 * re-enter each other.
 */
internal class StartupPlanning(
    private val creationPaths: () -> List<() -> List<AnyInitializerKey>> = { emptyList() },
    private val waveRunning: () -> Boolean = { false },
) {

    private val active = LinkedHashMap<AnyInitializerKey, Frame>()

    fun <T> read(
        component: AnyInitializerKey,
        path: () -> List<AnyInitializerKey>,
        block: () -> T,
    ): T {
        if (component in active) throw cycle(component, path())
        active[component] = Frame(path, creationPaths())
        try {
            return block()
        } finally {
            active.remove(component)
        }
    }

    fun cycle(component: AnyInitializerKey, requestedPath: List<AnyInitializerKey>): StartupException {
        if (waveRunning()) {
            return StartupException(
                "Cannot initialize ${componentName(component)}. It is already initializing " +
                    "in an enclosing startup operation that is waiting for this WaveRunner. " +
                    "A task cannot resolve it before that operation finishes.",
                null,
                listOf(component),
            )
        }
        val path = ArrayList<AnyInitializerKey>()
        var created = 0
        for (frame in active.values) {
            for (index in created until frame.creations.size) path.addAll(frame.creations[index]())
            created = frame.creations.size
            path.addAll(frame.path())
        }
        val creations = creationPaths()
        for (index in created until creations.size) path.addAll(creations[index]())
        path.addAll(requestedPath)
        val cycle = path.drop(path.indexOf(component))
        val rendered = cycle.joinToString(" -> ") { componentName(it) }
        return StartupException(
            "Cannot initialize ${componentName(component)}. Cycle detected: $rendered",
            null,
            cycle,
        )
    }

    private class Frame(
        val path: () -> List<AnyInitializerKey>,
        val creations: List<() -> List<AnyInitializerKey>>,
    )
}

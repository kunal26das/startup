package io.github.kunal26das.startup

/**
 * Turns a [StartupManifest] into a [StartupPlan] with Kahn's algorithm.
 *
 * This lives in `commonMain` and is the single copy for all eleven targets: Android runs
 * `androidx.startup` at runtime, but the planner still compiles and unit-tests there, so
 * a regression in the ordering rules cannot hide on one platform.
 *
 * The algorithm is iterative throughout. A recursive walk would be simpler to read and
 * would overflow the JavaScript stack on a deep enough graph.
 */
object StartupPlanner {

    /**
     * The order in which [roots] and everything they depend on must be created, skipping
     * whatever [satisfied] already holds.
     *
     * `satisfied` is threaded through all three phases of the algorithm: the pending set,
     * the in-degree counts, and the dependents map. An edge into an already-created
     * component simply vanishes, which is what makes a lazy
     * [AppInitializer.initializeComponent] call cost only the components it actually
     * still needs.
     *
     * Every map and set here is insertion ordered and the ready queue is FIFO, so the
     * emitted order is a pure function of declaration order and dependency order: the
     * same manifest always plans the same way.
     *
     * Reading a component's dependencies means having the component, so this constructs
     * every initializer it reaches by calling its factory, on every target including
     * Android. It never calls [Initializer.create].
     *
     * @throws StartupException if a component is not registered, if it is registered but
     * hidden by [StartupManifestBuilder.remove], if its factory builds a class other than
     * the one its key names, or if the graph has a cycle. A cycle is reported as a path
     * starting at the component the walk re-entered, both in the message and in
     * [StartupException.components].
     */
    @Throws(StartupException::class)
    fun plan(
        manifest: StartupManifest,
        roots: List<AnyInitializerKey>,
        satisfied: Set<AnyInitializerKey>,
    ): StartupPlan = plan(manifest, roots, satisfied, LinkedHashMap())

    internal fun plan(
        manifest: StartupManifest,
        roots: List<AnyInitializerKey>,
        satisfied: Set<AnyInitializerKey>,
        instances: MutableMap<AnyInitializerKey, Initializer<*>>,
    ): StartupPlan {
        val pending = LinkedHashSet<AnyInitializerKey>()
        val edges = LinkedHashMap<AnyInitializerKey, List<AnyInitializerKey>>()
        val queue = ArrayDeque<AnyInitializerKey>()
        for (root in roots) {
            if (root in satisfied) continue
            manifest.requireRegistered(root, null)
            if (pending.add(root)) queue.addLast(root)
        }
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            val dependencies = manifest.dependenciesOf(component, instances).distinct()
            edges[component] = dependencies
            for (dependency in dependencies) {
                if (dependency in satisfied) continue
                manifest.requireRegistered(dependency, component)
                if (pending.add(dependency)) queue.addLast(dependency)
            }
        }

        val inDegree = LinkedHashMap<AnyInitializerKey, Int>()
        val dependents = LinkedHashMap<AnyInitializerKey, MutableList<AnyInitializerKey>>()
        for (component in pending) {
            inDegree[component] = 0
            dependents[component] = ArrayList()
        }
        for (component in pending) {
            var unmet = 0
            for (dependency in edges.getValue(component)) {
                if (dependency in satisfied) continue
                unmet++
                dependents.getValue(dependency).add(component)
            }
            inDegree[component] = unmet
        }

        val ready = ArrayDeque<AnyInitializerKey>()
        for (component in pending) {
            if (inDegree.getValue(component) == 0) ready.addLast(component)
        }
        val order = ArrayList<AnyInitializerKey>(pending.size)
        val waves = ArrayList<List<AnyInitializerKey>>()
        while (ready.isNotEmpty()) {
            val wave = ready.toList()
            ready.clear()
            waves.add(wave)
            for (component in wave) {
                order.add(component)
                for (dependent in dependents.getValue(component)) {
                    val remaining = inDegree.getValue(dependent) - 1
                    inDegree[dependent] = remaining
                    if (remaining == 0) ready.addLast(dependent)
                }
            }
        }

        if (order.size != pending.size) {
            val residual = LinkedHashSet<AnyInitializerKey>()
            for (component in pending) {
                if (inDegree.getValue(component) > 0) residual.add(component)
            }
            val cycle = findCycle(residual, edges)
            throw StartupException(cycleMessage(cycle), null, cycle)
        }
        return StartupPlan(order, waves, edges)
    }

    /**
     * Checks that every component in [manifest] resolves and that the whole graph is
     * acyclic, without calling [Initializer.create]. Useful as a test or as a debug-build
     * assertion.
     *
     * It does construct every registered initializer, because reading `dependencies()`
     * needs one, so a factory with side effects runs here — on Android too, where
     * [Startup.install] would never have called it. That is what makes this the way to
     * reach these diagnostics on all eleven targets.
     *
     * @throws StartupException with the same diagnostics [plan] would raise.
     */
    @Throws(StartupException::class)
    fun validate(manifest: StartupManifest) {
        plan(manifest, manifest.components, emptySet())
    }

    private fun findCycle(
        residual: Set<AnyInitializerKey>,
        edges: Map<AnyInitializerKey, List<AnyInitializerKey>>,
    ): List<AnyInitializerKey> {
        val residualEdges = LinkedHashMap<AnyInitializerKey, List<AnyInitializerKey>>()
        for (component in residual) {
            residualEdges[component] = edges.getValue(component).filter { it in residual }
        }
        val finished = LinkedHashSet<AnyInitializerKey>()
        val cursor = LinkedHashMap<AnyInitializerKey, Int>()
        val stack = ArrayList<AnyInitializerKey>()
        for (root in residual) {
            if (root in finished) continue
            stack.add(root)
            cursor[root] = 0
            while (stack.isNotEmpty()) {
                val component = stack[stack.lastIndex]
                val at = cursor.getValue(component)
                val next = residualEdges.getValue(component).getOrNull(at)
                if (next == null) {
                    finished.add(component)
                    stack.removeAt(stack.lastIndex)
                    continue
                }
                cursor[component] = at + 1
                when (next) {
                    in stack -> return stack.subList(stack.indexOf(next), stack.size)
                        .toList()
                        .plus(next)

                    !in finished -> {
                        stack.add(next)
                        cursor[next] = 0
                    }

                    else -> Unit
                }
            }
        }
        throw StartupException("Cannot initialize startup graph. Cycle detected.")
    }

    private fun cycleMessage(cycle: List<AnyInitializerKey>): String {
        val names = cycle.map { componentName(it) }
        val path = if (names.size <= MAX_RENDERED_CYCLE) {
            names.joinToString(" -> ")
        } else {
            val head = names.take(MAX_RENDERED_CYCLE / 2)
            val tail = names.takeLast(MAX_RENDERED_CYCLE / 2)
            val hidden = names.size - MAX_RENDERED_CYCLE
            (head + "... ($hidden more) ..." + tail).joinToString(" -> ")
        }
        return "Cannot initialize ${names.first()}. Cycle detected: $path"
    }

    private const val MAX_RENDERED_CYCLE = 12
}

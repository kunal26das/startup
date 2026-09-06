package io.github.kunal26das.startup

/**
 * The non-Android runtime: it owns the installed [StartupManifest], the components it has
 * already created, and nothing else. There are no file-scoped mutable globals here, so
 * two engines never interfere and a test can drive an isolated graph.
 *
 * Every entry point runs under one reentrant [StartupLock], which is what AndroidX gets
 * from `synchronized (sLock)`: two threads asking for the same component receive the same
 * instance and [Initializer.create] runs exactly once. There is no coroutine anywhere in
 * this library, because `runBlocking` does not exist on Kotlin/JS or Kotlin/Wasm and those
 * targets are first-class here.
 *
 * By default the work inside the lock is sequential and happens on the calling thread.
 * [install] also takes a [WaveRunner], and then each [StartupPlan.waves] level is handed
 * over whole for the host to run however it likes; the planning, ordering, cycle detection
 * and the created components stay here either way.
 *
 * The lock is **held across the whole install** in both modes, which is what stops a second
 * `install` interleaving, and it is why a task inside a runner may not call back into
 * [AppInitializer.initializeComponent]: it would block on a lock the installing thread
 * still owns. That is a real limit rather than a detail — it is the pattern AndroidX
 * documents, and the one `sample`'s own initializers use — so a manifest whose components
 * resolve each other imperatively has to be installed without a runner. Declaring the edge
 * in [Initializer.dependencies] is what makes it safe, because that puts the dependency in
 * an earlier wave.
 */
internal class StartupEngine(private val context: Context) {

    private val lock = StartupLock()
    private val instances = LinkedHashMap<AnyInitializerKey, Initializer<*>>()
    private val initialized = LinkedHashMap<AnyInitializerKey, Any?>()
    private val creating = LinkedHashMap<AnyInitializerKey, Frame>()
    private var installed: StartupManifest = StartupManifest.Empty
    private var depth = 0

    fun install(manifest: StartupManifest, runner: WaveRunner? = null): Unit = lock.withLock {
        installed += manifest
        val roots = installed.eagerComponents
        withInstances { execute(planFor(roots), roots, runner) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> initializeComponent(component: InitializerKey<out Initializer<T>>): T =
        lock.withLock {
            val key: AnyInitializerKey = component
            if (initialized.containsKey(key)) return@withLock initialized[key] as T
            if (key in creating) throw reentrantCycle(key, null)
            val roots = listOf(key)
            withInstances { execute(planFor(roots), roots) }
            initialized[key] as T
        }

    fun isEager(component: AnyInitializerKey): Boolean = lock.withLock { installed.isEager(component) }

    private fun planFor(roots: List<AnyInitializerKey>): StartupPlan =
        StartupPlanner.plan(installed, roots, initialized.keys.toSet(), instances)

    /**
     * Runs [block], emptying [instances] once the outermost run unwinds however it ends.
     *
     * An initializer built to read [Initializer.dependencies] while planning has to
     * survive until [Initializer.create] runs, and a nested [initializeComponent] has to
     * see that same instance rather than build a second one, so the map outlives a single
     * plan. It must not outlive the run that filled it: [StartupManifest.plus] lets a
     * later install replace a component's factory, and an instance stranded by a failed
     * run would shadow the replacement. The depth counter is what distinguishes a nested
     * run from an outermost one; [creating] cannot, because it is still empty while the
     * outermost run is planning.
     */
    private fun withInstances(block: () -> Unit) {
        depth++
        try {
            block()
        } finally {
            depth--
            if (depth == 0) instances.clear()
        }
    }

    private fun execute(
        plan: StartupPlan,
        roots: List<AnyInitializerKey>,
        runner: WaveRunner? = null,
    ) {
        if (runner != null) {
            executeInWaves(plan, roots, runner)
            return
        }
        val frame = Frame(roots, plan.edges)
        for (component in plan.order) {
            if (initialized.containsKey(component)) continue
            if (component in creating) throw reentrantCycle(component, frame)
            val initializer = installed.initializerOf(component, instances)
            creating[component] = frame
            try {
                initialized[component] = initializer.create(context)
            } catch (exception: StartupException) {
                throw exception
            } catch (throwable: Throwable) {
                throw StartupException(
                    "Cannot initialize ${componentName(component)}.",
                    throwable,
                    listOf(component),
                )
            } finally {
                creating.remove(component)
            }
        }
    }

    /**
     * Creates [plan] a wave at a time, handing each wave to [runner].
     *
     * The components of a wave depend only on earlier waves, so they are safe to run
     * together. Everything else stays on this thread and under the lock: the initializers
     * are built here, the wave is handed over, and the results are recorded only once
     * [WaveRunner.run] has returned, which is what orders the writes against the tasks
     * that produced them. A task that fails takes the whole install down, as a failure in
     * the sequential path does.
     */
    private fun executeInWaves(
        plan: StartupPlan,
        roots: List<AnyInitializerKey>,
        runner: WaveRunner,
    ) {
        val frame = Frame(roots, plan.edges)
        for (wave in plan.waves) {
            val pending = ArrayList<AnyInitializerKey>(wave.size)
            for (component in wave) {
                if (initialized.containsKey(component)) continue
                if (component in creating) throw reentrantCycle(component, frame)
                pending.add(component)
            }
            if (pending.isEmpty()) continue
            val results = arrayOfNulls<Any>(pending.size)
            val tasks = ArrayList<() -> Unit>(pending.size)
            for (index in pending.indices) {
                val component = pending[index]
                val initializer = installed.initializerOf(component, instances)
                creating[component] = frame
                tasks.add { results[index] = initializer.create(context) }
            }
            try {
                runner.run(tasks)
            } catch (exception: StartupException) {
                throw exception
            } catch (throwable: Throwable) {
                throw StartupException(waveFailureMessage(pending), throwable, pending.toList())
            } finally {
                for (component in pending) creating.remove(component)
            }
            for (index in pending.indices) {
                initialized[pending[index]] = results[index]
            }
        }
    }

    private fun waveFailureMessage(wave: List<AnyInitializerKey>): String =
        "Cannot initialize " + wave.joinToString(", ") { componentName(it) } +
            ". A component of this wave failed inside the WaveRunner."

    /**
     * The cycle a re-entrant [Initializer.create] closed, rendered as a real path.
     *
     * [creating] holds the components still in flight, but it is a nesting stack rather
     * than a chain: neighbours in it need share no edge, because the outer one asked for
     * something else that merely happened to need the inner one first. Each in-flight
     * component therefore carries the [Frame] it was created by, and the walk fills in the
     * hops between one frame at a time, breadth first from the nearest root so the
     * shortest such path is the one reported. [frame] is the frame the re-entry was found
     * in, and is null when [initializeComponent] caught it before planning, where the last
     * step is the imperative call itself.
     */
    private fun reentrantCycle(component: AnyInitializerKey, frame: Frame?): StartupException {
        val stack = creating.keys.toList()
        val path = ArrayList<AnyInitializerKey>()
        for (index in stack.indexOf(component)..stack.lastIndex) {
            val inFlight = stack[index]
            if (path.isNotEmpty()) {
                val entered = creating.getValue(inFlight)
                path.addAll(shortestChain(entered.edges, entered.roots, inFlight))
            }
            path.add(inFlight)
        }
        if (frame != null) path.addAll(shortestChain(frame.edges, frame.roots, component))
        path.add(component)
        val rendered = path.joinToString(" -> ") { componentName(it) }
        return StartupException(
            "Cannot initialize ${componentName(component)}. Cycle detected: $rendered",
            null,
            path,
        )
    }

    private fun shortestChain(
        edges: Map<AnyInitializerKey, List<AnyInitializerKey>>,
        roots: List<AnyInitializerKey>,
        to: AnyInitializerKey,
    ): List<AnyInitializerKey> {
        val parents = LinkedHashMap<AnyInitializerKey, AnyInitializerKey>()
        val queue = ArrayDeque<AnyInitializerKey>()
        for (root in roots) {
            if (root == to) return emptyList()
            if (parents.put(root, root) == null) queue.addLast(root)
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in edges[current].orEmpty()) {
                if (next == to) {
                    val chain = ArrayList<AnyInitializerKey>()
                    var cursor = current
                    while (parents.getValue(cursor) != cursor) {
                        chain.add(cursor)
                        cursor = parents.getValue(cursor)
                    }
                    chain.add(cursor)
                    return chain.reversed()
                }
                if (next !in parents) {
                    parents[next] = current
                    queue.addLast(next)
                }
            }
        }
        return emptyList()
    }

    /**
     * What one [execute] call was asked for: the components it started from and the edges
     * its plan discovered. Held for each component while that component is in flight, so
     * [reentrantCycle] can name the path a nested `create` actually took.
     */
    private class Frame(
        val roots: List<AnyInitializerKey>,
        val edges: Map<AnyInitializerKey, List<AnyInitializerKey>>,
    )
}

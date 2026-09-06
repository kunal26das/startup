package io.github.kunal26das.startup

/**
 * The non-Android runtime: it owns the installed [StartupManifest], the components it has
 * already created, and nothing else. There are no file-scoped mutable globals here, so
 * two engines never interfere and a test can drive an isolated graph.
 *
 * Every entry point runs under one reentrant [StartupLock], which is what AndroidX gets
 * from `synchronized (sLock)`: two threads asking for the same component receive the same
 * instance and [Initializer.create] runs exactly once. The engine itself is free of
 * coroutines, because `runBlocking` does not exist on Kotlin/JS or Kotlin/Wasm and those
 * targets are first-class here; a [CoroutineInitializer] is where a component's own work
 * gets to suspend.
 *
 * By default the work inside the lock is sequential and happens on the calling thread.
 * [install] also takes a [WaveRunner], and then each [StartupPlan.waves] level is handed
 * over whole for the host to run however it likes; the planning, ordering, cycle detection
 * and the created components stay here either way.
 *
 * The lock is **held across the whole install** in both modes, which is what stops a second
 * `install` interleaving, and it is why a task inside a runner may only call back into
 * [AppInitializer.initializeComponent] from the thread that called [install]. On the thread
 * a task's body runs on, that call can never be served — the lock is released when the
 * runner returns, and the runner returns when that very task does — so it fails immediately
 * with a [StartupException] rather than waiting for something that will not arrive.
 *
 * On the installing thread the lock is reentrant and the call is served, with one exception:
 * a component of the wave being run right now. Nothing is written back until [WaveRunner.run]
 * returns, so a sibling is neither created nor creatable here, whether its task has already
 * finished or has not started. That is refused too, by name — see [inFlight].
 *
 * The guard reaches exactly that far. A task that hands the engine call to a *further*
 * thread, which is what [CoroutineInitializer.createAsync] does when it switches
 * dispatchers, is not covered and still waits forever; so does an ordinary `create` that
 * blocks on a thread it spawned, with or without a runner. Declaring the edge in
 * [Initializer.dependencies] is what makes the call safe from any thread, because that puts
 * the dependency in an earlier wave. A thread that is running no task at all waits as it
 * always did, because its wait does end when the install does.
 */
internal class StartupEngine(private val context: Context) {

    private val lock = StartupLock(guardsWaveTasks = true)
    private val instances = LinkedHashMap<AnyInitializerKey, Initializer<*>>()
    private val initialized = LinkedHashMap<AnyInitializerKey, Any?>()
    private val creating = LinkedHashMap<AnyInitializerKey, Frame>()
    private val waveMembers = LinkedHashSet<AnyInitializerKey>()
    private var installed: StartupManifest = StartupManifest.Empty
    private var depth = 0

    fun install(manifest: StartupManifest, runner: WaveRunner? = null): Unit = lock.withLock {
        installed += manifest
        val roots = installed.eagerComponents
        withInstances { execute(planFor(roots), roots, runner) }
    }

    fun <T : Any> initializeComponent(component: InitializerKey<out Initializer<T>>): T =
        lock.withLock {
            val key: AnyInitializerKey = component
            if (initialized.containsKey(key)) return@withLock product(key)
            if (key in creating) throw inFlight(key, null)
            val roots = listOf(key)
            withInstances { execute(planFor(roots), roots) }
            product(key)
        }

    fun initializeComponentOrNull(component: AnyInitializerKey): Any? = lock.withLock {
        if (initialized.containsKey(component)) return@withLock initialized[component]
        if (component in creating) throw inFlight(component, null)
        val roots = listOf(component)
        withInstances { execute(planFor(roots), roots) }
        initialized[component]
    }

    fun isEager(component: AnyInitializerKey): Boolean = lock.withLock { installed.isEager(component) }

    private fun planFor(roots: List<AnyInitializerKey>): StartupPlan =
        StartupPlanner.plan(installed, roots, initialized.keys.toSet(), instances)

    /**
     * The component filed under [key], for a caller that cannot represent its absence.
     *
     * [AppInitializer.initializeComponent] returns a non-null `T`, so a component whose
     * `create` returned null has nothing to hand back. AndroidX would return that null on
     * Android, where the type is a Java one and nullability is unenforced, which makes this
     * the one read the two runtimes answer differently; naming it is worth more than
     * failing with the cast's own message, and
     * [AppInitializer.initializeComponentOrNull] is the read that can represent it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> product(key: AnyInitializerKey): T {
        val product = initialized[key] ?: throw StartupException(
            "Cannot initialize ${componentName(key)}. Its create returned null, and " +
                "initializeComponent is declared to return a non-null component. Return a " +
                "value, Unit if the component has no product, or read it with " +
                "initializeComponentOrNull.",
            null,
            listOf(key),
        )
        return product as T
    }

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
            if (component in creating) throw inFlight(component, frame)
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
     * are built here, the wave is handed over, and every task that finished is recorded —
     * including when a sibling failed, which is what makes a failed wave leave the same
     * state behind as a failed step of the sequential path.
     *
     * [WaveRunner.run] returning is not taken as proof that it ran anything. A task it
     * skipped, or one whose failure it swallowed, is reported here by name rather than
     * filed as a component that is not there.
     *
     * Every initializer is built before any of them is marked in flight, so that nothing is
     * left in [creating] or [waveMembers] by a component that could not be built at all. The
     * planner has already constructed all of them into [instances], so the loop is a cache
     * read and the ordering costs nothing.
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
                if (component in creating) throw inFlight(component, frame)
                pending.add(component)
            }
            if (pending.isEmpty()) continue
            val initializers = pending.map { installed.initializerOf(it, instances) }
            val results = arrayOfNulls<Any>(pending.size)
            val tasks = ArrayList<StartupTask>(pending.size)
            for (index in pending.indices) {
                val component = pending[index]
                val initializer = initializers[index]
                creating[component] = frame
                waveMembers.add(component)
                tasks.add(StartupTask(component) { results[index] = initializer.create(context) })
            }
            try {
                runner.run(tasks)
            } catch (exception: StartupException) {
                throw named(exception, tasks, pending)
            } catch (throwable: Throwable) {
                throw StartupException(
                    waveFailureMessage(tasks, pending),
                    throwable,
                    blamedComponents(tasks, pending),
                )
            } finally {
                for (index in pending.indices) {
                    if (tasks[index].completed) initialized[pending[index]] = results[index]
                }
                for (component in pending) {
                    creating.remove(component)
                    waveMembers.remove(component)
                }
            }
            requireWaveRan(tasks, pending)
        }
    }

    /**
     * Fails a wave whose runner returned without honouring [WaveRunner.run]'s contract.
     *
     * A swallowed failure comes first, because it is the one the host already saw and
     * decided not to raise. Its cause is the first such failure — an exception carries one —
     * while the components blamed are all of them, so the message and
     * [StartupException.components] still agree. An unfinished task is either one the runner
     * never invoked or one it left running, and neither is distinguishable from here.
     */
    private fun requireWaveRan(tasks: List<StartupTask>, pending: List<AnyInitializerKey>) {
        val swallowed = tasks.firstOrNull { it.failure != null }
        if (swallowed != null) {
            throw StartupException(
                waveFailureMessage(tasks, pending),
                swallowed.failure,
                blamedComponents(tasks, pending),
            )
        }
        val unfinished = tasks.filterNot { it.completed }.map { it.component }
        if (unfinished.isNotEmpty()) {
            throw StartupException(
                "Cannot initialize " + unfinished.joinToString(", ") { componentName(it) } +
                    ". Its WaveRunner returned before the task finished; run must invoke " +
                    "every task exactly once and must not return until all of them have " +
                    "completed.",
                null,
                unfinished,
            )
        }
    }

    /**
     * [exception] with the wave's failing components attached, when it arrived without any.
     *
     * A [StartupException] a task raised usually names what it was about — a cycle, an
     * unregistered dependency — and is rethrown as it is. The one raised for a task that
     * called back into the engine from another thread cannot name anything, because the
     * lock rejects the call before it knows what was asked for, and letting that reach the
     * caller unattributed made the same failure diagnosable through a runner that swallows
     * it and undiagnosable through one that does not.
     */
    private fun named(
        exception: StartupException,
        tasks: List<StartupTask>,
        pending: List<AnyInitializerKey>,
    ): StartupException {
        if (exception.components.isNotEmpty()) return exception
        val failed = tasks.filter { it.failure != null }.map { it.component }
        if (failed.isEmpty()) return exception
        return StartupException(waveFailureMessage(tasks, pending), exception, failed)
    }

    private fun waveFailureMessage(
        tasks: List<StartupTask>,
        wave: List<AnyInitializerKey>,
    ): String = "Cannot initialize " +
        blamedComponents(tasks, wave).joinToString(", ") { componentName(it) } +
        ". A component of this wave failed inside the WaveRunner."

    /**
     * The components a failed wave is reported against: the ones that actually threw, or the
     * whole wave when the runner failed without any of them recording a failure. The message
     * and [StartupException.components] are built from this one list, so they cannot name
     * different components for the same failure.
     */
    private fun blamedComponents(
        tasks: List<StartupTask>,
        wave: List<AnyInitializerKey>,
    ): List<AnyInitializerKey> {
        val failed = tasks.filter { it.failure != null }.map { it.component }
        return failed.ifEmpty { wave }
    }

    /**
     * Why [component] cannot be created here, given that something already has it in flight.
     *
     * [creating] holds two kinds of entry and they fail for different reasons. A component
     * an enclosing [Initializer.create] is waiting on is a real cycle, and [reentrantCycle]
     * renders the path that closed it. A component of the wave [executeInWaves] is running
     * is not: two components land in one wave precisely because neither declares the other,
     * so there is no cycle to draw, and rendering one printed an edge that exists in no
     * `dependencies()` — the same fabricated path [reentrantCycle] exists to avoid.
     */
    private fun inFlight(component: AnyInitializerKey, frame: Frame?): StartupException =
        if (component in waveMembers) waveMemberBarrier(component) else reentrantCycle(component, frame)

    /**
     * The refusal for a component of the wave currently being run.
     *
     * Its task has either not been invoked yet or has finished without being recorded —
     * nothing is written back until [WaveRunner.run] returns — and either way it cannot be
     * created a second time here. Only a caller on the installing thread reaches this;
     * another thread is refused earlier, by [StartupLock].
     *
     * It covers a component asking for *itself* too, which is a genuine cycle, and says so
     * rather than deferring to [reentrantCycle]. The path that walk renders comes from
     * [creating], and under a runner [creating] holds the whole wave rather than a nesting
     * stack, so a self-call in a wave of two printed `Self -> Sibling -> Self` — an edge
     * between components that share a wave precisely because neither declares the other.
     */
    private fun waveMemberBarrier(component: AnyInitializerKey): StartupException = StartupException(
        "Cannot initialize ${componentName(component)}. It is a member of the wave currently " +
            "being run, so it is not created yet and cannot be created here — which holds for " +
            "a sibling task and for this component's own create asking for itself. Declare " +
            "what a component needs in dependencies(), which is what puts it in an earlier wave.",
        null,
        listOf(component),
    )

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

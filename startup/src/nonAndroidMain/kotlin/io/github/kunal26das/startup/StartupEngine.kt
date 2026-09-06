package io.github.kunal26das.startup

/**
 * The non-Android runtime: it owns the installed [StartupManifest], the components it has
 * already created, and nothing else. There are no file-scoped mutable globals here, so
 * two engines never interfere and a test can drive an isolated graph.
 *
 * Every entry point runs under one reentrant [StartupLock], which is what AndroidX gets
 * from `synchronized (sLock)`: two threads asking for the same component receive the same
 * instance and [Initializer.create] runs exactly once. Within the lock the work is
 * strictly sequential and happens on the calling thread. There is no coroutine anywhere in
 * this library, because `runBlocking` does not exist on Kotlin/JS or Kotlin/Wasm and those
 * targets are first-class here.
 *
 * There is no scheduling seam and there is not going to be one. Running a
 * [StartupPlan.waves] level on worker threads while this lock is held deadlocks the first
 * [Initializer.create] that reads a dependency back through
 * [AppInitializer.initializeComponent], which is the pattern AndroidX documents and the
 * one `sample`'s own initializers use; releasing the lock for the duration of a wave
 * instead would let a second `install` interleave, which is what the lock exists to stop.
 * [StartupPlan.waves] is therefore diagnostic data about the graph, and concurrent startup
 * means owning the initializers outside this engine.
 */
internal class StartupEngine(private val context: Context) {

    private val lock = StartupLock()
    private val initialized = LinkedHashMap<AnyInitializerKey, Any?>()
    private val creating = LinkedHashSet<AnyInitializerKey>()
    private var installed: StartupManifest = StartupManifest.Empty

    fun install(manifest: StartupManifest): Unit = lock.withLock {
        installed += manifest
        execute(StartupPlanner.plan(installed, installed.eagerComponents, initialized.keys.toSet()))
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> initializeComponent(component: InitializerKey<out Initializer<T>>): T =
        lock.withLock {
            val key: AnyInitializerKey = component
            if (initialized.containsKey(key)) return@withLock initialized[key] as T
            if (key in creating) throw reentrantCycle(key)
            execute(StartupPlanner.plan(installed, listOf(key), initialized.keys.toSet()))
            initialized[key] as T
        }

    fun isEager(component: AnyInitializerKey): Boolean = lock.withLock { installed.isEager(component) }

    private fun execute(plan: StartupPlan) {
        for (component in plan.order) {
            if (initialized.containsKey(component)) continue
            if (component in creating) throw reentrantCycle(component)
            val initializer = installed.initializerOf(component)
            creating.add(component)
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

    private fun reentrantCycle(component: AnyInitializerKey): StartupException {
        val path = creating.dropWhile { it != component } + component
        val rendered = path.joinToString(" -> ") { componentName(it) }
        return StartupException(
            "Cannot initialize ${componentName(component)}. Cycle detected: $rendered",
            null,
            path,
        )
    }
}

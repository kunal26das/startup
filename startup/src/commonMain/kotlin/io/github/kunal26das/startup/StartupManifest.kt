package io.github.kunal26das.startup

/**
 * The portable registry of initializers: what an app would otherwise express as
 * `<meta-data>` entries inside `androidx.startup.InitializationProvider`.
 *
 * Off Android there is no manifest merger and no reflection, so this object is the only
 * source of truth. On Android the AndroidManifest is still what AndroidX reads, and it
 * stays the source of truth there: write its `<meta-data>` entries by hand, exactly as a
 * plain `androidx.startup` application does.
 *
 * Components are registered as **factories**, never as instances. Registering an instance
 * would double-construct every initializer on Android, where AndroidX builds its own
 * through `getDeclaredConstructor().newInstance()`, and would allocate eagerly off
 * Android. Because AndroidX ignores the factory, a factory that passes constructor
 * arguments works on all ten non-Android targets and throws
 * `StartupException(NoSuchMethodException)` on Android: an initializer must always have a
 * public no-argument constructor.
 *
 * Build one with the [invoke] operator:
 *
 * ```
 * val manifest = StartupManifest {
 *     metaData<LoggerInitializer> { LoggerInitializer() }
 *     lazyInitializer<HeavyInitializer> { HeavyInitializer() }
 * }
 * ```
 */
class StartupManifest internal constructor(
    internal val nodes: Map<AnyInitializerKey, Node>,
) {

    /** Every registered component, in declaration order, tombstones excluded. */
    val components: List<AnyInitializerKey>
        get() = nodes.entries.filter { it.value != Node.Remove }.map { it.key }

    /** The components initialized eagerly, in declaration order. */
    val eagerComponents: List<AnyInitializerKey>
        get() = nodes.entries.filter { it.value is Node.Merge }.map { it.key }

    /** Whether [component] is registered and initialized eagerly. */
    fun isEager(component: AnyInitializerKey): Boolean = nodes[component] is Node.Merge

    /** Whether [component] is registered at all, eagerly or lazily. */
    operator fun contains(component: AnyInitializerKey): Boolean = factoryOf(component) != null

    /**
     * A manifest holding this one's entries overlaid with [other]'s, so a later entry for
     * the same component wins. This is how an application overrides or removes something
     * a library shipped.
     */
    operator fun plus(other: StartupManifest): StartupManifest {
        val merged = LinkedHashMap<AnyInitializerKey, Node>(nodes)
        merged.putAll(other.nodes)
        return StartupManifest(merged)
    }

    internal fun factoryOf(component: AnyInitializerKey): (() -> Initializer<*>)? =
        when (val node = nodes[component]) {
            is Node.Merge -> node.factory
            is Node.Lazy -> node.factory
            Node.Remove, null -> null
        }

    internal fun requireRegistered(component: AnyInitializerKey, requiredBy: AnyInitializerKey?) {
        val message = when (nodes[component]) {
            is Node.Merge, is Node.Lazy -> return
            Node.Remove -> removedComponentMessage(component, requiredBy)
            null -> missingComponentMessage(component, requiredBy)
        }
        throw StartupException(message, null, listOfNotNull(component, requiredBy))
    }

    internal fun initializerOf(
        component: AnyInitializerKey,
        instances: MutableMap<AnyInitializerKey, Initializer<*>>,
    ): Initializer<*> {
        instances[component]?.let { return it }
        val factory = factoryOf(component)
            ?: throw StartupException(
                missingComponentMessage(component, null),
                null,
                listOf(component),
            )
        val initializer = try {
            factory()
        } catch (throwable: Throwable) {
            throw StartupException(
                "Cannot initialize ${componentName(component)}.",
                throwable,
                listOf(component),
            )
        }
        val produced = initializerKey(initializer)
        if (produced != component) {
            throw StartupException(
                mismatchedFactoryMessage(component, produced),
                null,
                listOf(component, produced),
            )
        }
        instances[component] = initializer
        return initializer
    }

    internal fun dependenciesOf(
        component: AnyInitializerKey,
        instances: MutableMap<AnyInitializerKey, Initializer<*>>,
    ): List<AnyInitializerKey> {
        val initializer = initializerOf(component, instances)
        return try {
            initializer.dependencies()
        } catch (exception: StartupException) {
            throw exception
        } catch (throwable: Throwable) {
            throw StartupException(
                "Cannot initialize ${componentName(component)}.",
                throwable,
                listOf(component),
            )
        }
    }

    private fun removedComponentMessage(
        component: AnyInitializerKey,
        requiredBy: AnyInitializerKey?,
    ): String = buildString {
        append("Cannot initialize ").append(componentName(component)).append(". ")
        append("A remove() entry hides it")
        if (requiredBy != null) {
            append(", and ").append(componentName(requiredBy))
            append(" still declares it as a dependency. ")
            append("Drop that dependencies() entry, or stop removing the component. ")
            append("Startup.install on Android reads dependencies() reflectively without ")
            append("consulting a StartupManifest, so it creates it there anyway.")
        } else {
            append(". Stop removing it to make it resolvable again. ")
            append("Startup.install on Android reflects the class it is asked for without ")
            append("consulting a StartupManifest, so it creates it there anyway.")
        }
    }

    private fun mismatchedFactoryMessage(
        component: AnyInitializerKey,
        produced: AnyInitializerKey,
    ): String = buildString {
        append("Cannot initialize ").append(componentName(component)).append(". ")
        append("Its factory produced a ").append(componentName(produced))
        append(" instead. A factory has to build the class its key names: the product ")
        append("would be filed under the registered key here, while Startup.install on ")
        append("Android ignores the factory and reflects the key, so one manifest would ")
        append("build two different graphs. Register it under its own key.")
    }

    private fun missingComponentMessage(
        component: AnyInitializerKey,
        requiredBy: AnyInitializerKey?,
    ): String = buildString {
        append("Cannot initialize ").append(componentName(component)).append(". ")
        append("No initializer is registered for it")
        if (requiredBy != null) append(", required by ").append(componentName(requiredBy))
        append(". Register it in a StartupManifest with metaData or lazyInitializer, ")
        append("then install that manifest with Startup.install(context, manifest).")
    }

    /** Factories for [StartupManifest]. */
    companion object {

        /** A manifest with no entries. */
        val Empty: StartupManifest = StartupManifest(emptyMap())

        /** Builds a manifest from a [StartupManifestBuilder] block. */
        operator fun invoke(block: StartupManifestBuilder.() -> Unit): StartupManifest =
            StartupManifestBuilder().apply(block).build()
    }
}

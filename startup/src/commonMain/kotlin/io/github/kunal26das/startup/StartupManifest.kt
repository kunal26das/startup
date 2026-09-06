package io.github.kunal26das.startup

/**
 * The portable registry of initializers: what an app would otherwise express as
 * `<meta-data>` entries inside `androidx.startup.InitializationProvider`.
 *
 * Off Android there is no manifest merger and no reflection, so this object is the only
 * source of truth. On Android the AndroidManifest is still what AndroidX reads, which
 * makes [androidManifestMetadata] the way to keep the two in step, and makes it possible
 * for a test to assert that they agree.
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

    private val instances = LinkedHashMap<AnyInitializerKey, Initializer<*>>()

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

    /**
     * The `<meta-data>` lines this manifest corresponds to, ready to paste into the
     * `androidx.startup.InitializationProvider` block of an AndroidManifest. Eager
     * entries become `android:value="androidx.startup"`, tombstones become
     * `tools:node="remove"`, and lazily registered components emit nothing because they
     * are absent from an Android manifest by definition.
     *
     * The result is those lines and nothing else: one line per entry, newline separated,
     * with no `<manifest>`, `<application>` or `<provider>` element around them. Those
     * surrounding elements are the destination, not part of the output.
     *
     * **Generate this on Android.** `InitializationProvider` resolves each name with
     * `Class.forName`, so every one has to be fully qualified, and only the Android key
     * can supply that: off Android a key names its class simply, because reading
     * `KClass.qualifiedName` does not compile on Kotlin/JS. What the other ten targets
     * emit is a preview of the shape, not something to paste.
     *
     * A tombstone line uses the `tools` namespace, so the `<manifest>` element it lands
     * in needs `xmlns:tools="http://schemas.android.com/tools"` or the manifest merger
     * fails with an unbound prefix.
     */
    fun androidManifestMetadata(): String = nodes.entries.mapNotNull { (component, node) ->
        val name = componentName(component)
        when (node) {
            is Node.Merge -> "<meta-data android:name=\"$name\" android:value=\"androidx.startup\" />"
            is Node.Lazy -> null
            Node.Remove -> "<meta-data android:name=\"$name\" tools:node=\"remove\" />"
        }
    }.joinToString("\n")

    /**
     * Every disagreement between this manifest and an AndroidManifest whose
     * `androidx.startup.InitializationProvider` block declares [declared], named the way
     * [androidManifestMetadata] names components.
     *
     * The two registries are genuinely separate on Android. AndroidX reads only the XML,
     * so a component this manifest marks eager and the XML omits runs on the other ten
     * targets and silently never runs on Android, and one the XML declares while this
     * manifest keeps it lazy or removed runs eagerly on Android alone. Both are reported
     * here, one line each, and the list is empty when the two agree.
     *
     * A name in [declared] that this manifest has never heard of is not drift. An
     * application is free to declare initializers written directly against
     * `androidx.startup` beside these, and reporting those would make the check useless
     * in exactly the applications that need it most.
     *
     * Call this on Android, where a key names its class fully. On the other ten targets a
     * key can only name its class simply, so nothing will match. The overload taking a
     * `Context` reads [declared] from the running application's own merged manifest.
     */
    fun androidManifestDrift(declared: Set<String>): List<String> =
        nodes.entries.mapNotNull { (component, node) ->
            val name = componentName(component)
            when (node) {
                is Node.Merge -> if (name in declared) null else {
                    "$name is eager in the StartupManifest and is not declared in the " +
                        "AndroidManifest, so it never runs on Android."
                }

                is Node.Lazy -> if (name !in declared) null else {
                    "$name is lazy in the StartupManifest and is declared in the " +
                        "AndroidManifest, so it runs eagerly on Android alone."
                }

                Node.Remove -> if (name !in declared) null else {
                    "$name is removed from the StartupManifest and is declared in the " +
                        "AndroidManifest, so it still runs on Android."
                }
            }
        }

    internal fun factoryOf(component: AnyInitializerKey): (() -> Initializer<*>)? =
        when (val node = nodes[component]) {
            is Node.Merge -> node.factory
            is Node.Lazy -> node.factory
            Node.Remove, null -> null
        }

    internal fun requireRegistered(component: AnyInitializerKey, requiredBy: AnyInitializerKey?) {
        if (factoryOf(component) != null) return
        throw StartupException(
            missingComponentMessage(component, requiredBy),
            null,
            listOfNotNull(component, requiredBy),
        )
    }

    internal fun initializerOf(component: AnyInitializerKey): Initializer<*> {
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
        instances[component] = initializer
        return initializer
    }

    internal fun dependenciesOf(component: AnyInitializerKey): List<AnyInitializerKey> {
        val initializer = initializerOf(component)
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

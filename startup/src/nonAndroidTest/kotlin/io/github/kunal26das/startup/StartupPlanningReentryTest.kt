package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Pins cycles and cleanup when factories, dependency callbacks and creation re-enter planning. */
class StartupPlanningReentryTest {

    /** A callback cycle is refused before its dependency method is invoked a second time. */
    @Test
    fun rejectsARepeatedDependencyCallback() {
        val engine = StartupEngine(DefaultContext)
        var reads = 0
        val manifest = StartupManifest {
            metaData<A> {
                A({
                    check(++reads < 20) { "dependency callback repeated" }
                    engine.initializeComponent(initializerKey<B>())
                    listOf(initializerKey<B>())
                })
            }
            lazyInitializer<B> { B({ listOf(initializerKey<A>()) }) }
        }
        assertCycle(
            assertFailsWith<StartupException> { engine.install(manifest) },
            initializerKey<A>(), initializerKey<B>(), initializerKey<A>(),
        )
        assertEquals(1, reads)
    }

    /** A factory is guarded before construction, and its cycle keeps the complete diagnostic. */
    @Test
    fun rejectsARepeatedFactory() {
        val engine = StartupEngine(DefaultContext)
        var constructions = 0
        val manifest = StartupManifest {
            metaData<A> {
                check(++constructions < 20) { "factory repeated" }
                engine.initializeComponent(initializerKey<B>())
                A()
            }
            lazyInitializer<B> { B({ listOf(initializerKey<A>()) }) }
        }
        assertCycle(
            assertFailsWith<StartupException> { engine.install(manifest) },
            initializerKey<A>(), initializerKey<B>(), initializerKey<A>(),
        )
        assertEquals(1, constructions)
        engine.install(StartupManifest { metaData<A> { A() } })
        assertEquals("created", engine.initializeComponent(initializerKey<A>()))
        assertEquals("created", engine.initializeComponent(initializerKey<B>()))
    }

    /** The declaration that connects a nested root to a re-entered callback remains in the path. */
    @Test
    fun preservesDeclaredHopsBetweenCallbacks() {
        val engine = StartupEngine(DefaultContext)
        var reads = 0
        val manifest = StartupManifest {
            metaData<A> {
                A({
                    check(++reads < 20) { "dependency callback repeated" }
                    engine.initializeComponent(initializerKey<B>())
                    emptyList()
                })
            }
            lazyInitializer<B> { B({ listOf(initializerKey<C>()) }) }
            lazyInitializer<C> { C({ listOf(initializerKey<A>()) }) }
        }
        assertCycle(
            assertFailsWith<StartupException> { engine.install(manifest) },
            initializerKey<A>(), initializerKey<B>(), initializerKey<C>(), initializerKey<A>(),
        )
        assertEquals(1, reads)
    }

    /** Separate nested dependency callbacks contribute their imperative edges to the cycle. */
    @Test
    fun preservesNestedDependencyCallbacks() {
        val engine = StartupEngine(DefaultContext)
        var reads = 0
        val manifest = StartupManifest {
            metaData<A> {
                A({
                    check(++reads < 20) { "dependency callback repeated" }
                    engine.initializeComponent(initializerKey<B>())
                    emptyList()
                })
            }
            lazyInitializer<B> {
                B({
                    engine.initializeComponent(initializerKey<C>())
                    emptyList()
                })
            }
            lazyInitializer<C> { C({ listOf(initializerKey<A>()) }) }
        }
        assertCycle(
            assertFailsWith<StartupException> { engine.install(manifest) },
            initializerKey<A>(), initializerKey<B>(), initializerKey<C>(), initializerKey<A>(),
        )
        assertEquals(1, reads)
    }

    /** A creation entered from dependencies is retained when it calls back into planning. */
    @Test
    fun preservesCreationBetweenDependencyCallbacks() {
        val engine = StartupEngine(DefaultContext)
        var reads = 0
        val manifest = StartupManifest {
            metaData<A> {
                A({
                    check(++reads < 20) { "dependency callback repeated" }
                    engine.initializeComponent(initializerKey<B>())
                    emptyList()
                })
            }
            lazyInitializer<B> {
                B(onCreate = { engine.initializeComponent(initializerKey<C>()) })
            }
            lazyInitializer<C> { C({ listOf(initializerKey<A>()) }) }
        }
        assertCycle(
            assertFailsWith<StartupException> { engine.install(manifest) },
            initializerKey<A>(), initializerKey<B>(), initializerKey<C>(), initializerKey<A>(),
        )
        assertEquals(1, reads)
    }

    /** A dependency callback between two creations is retained when the outer creation is re-entered. */
    @Test
    fun preservesDependencyCallbacksBetweenCreations() {
        val engine = StartupEngine(DefaultContext)
        val manifest = StartupManifest {
            metaData<A> {
                A(onCreate = { engine.initializeComponent(initializerKey<B>()) })
            }
            lazyInitializer<B> {
                B({
                    engine.initializeComponent(initializerKey<C>())
                    emptyList()
                })
            }
            lazyInitializer<C> {
                C(onCreate = { engine.initializeComponent(initializerKey<A>()) })
            }
        }
        assertCycle(
            assertFailsWith<StartupException> { engine.install(manifest) },
            initializerKey<A>(), initializerKey<B>(), initializerKey<C>(), initializerKey<A>(),
        )
    }

    /** A nested wave cannot resolve its enclosing callback or blame its unrelated sibling. */
    @Test
    fun refusesAnEnclosingCallbackFromANestedWave() {
        val engine = StartupEngine(DefaultContext)
        var reads = 0
        val nested = StartupManifest {
            metaData<B> {
                B(onCreate = { engine.initializeComponent(initializerKey<A>()) })
            }
            metaData<C> { C() }
        }
        engine.install(StartupManifest {
            lazyInitializer<A> {
                A({
                    check(++reads < 20) { "dependency callback repeated" }
                    engine.install(nested, WaveRunner { wave -> wave.forEach { it() } })
                    emptyList()
                })
            }
        })
        val failure = assertFailsWith<StartupException> {
            engine.initializeComponent(initializerKey<A>())
        }
        assertEquals(listOf<AnyInitializerKey>(initializerKey<A>()), failure.components)
        assertEquals(
            "Cannot initialize ${componentName(initializerKey<A>())}. It is already initializing " +
                "in an enclosing startup operation that is waiting for this WaveRunner. " +
                "A task cannot resolve it before that operation finishes.",
            failure.message,
        )
        assertEquals(1, reads)
    }

    /** A failed callback releases both its planning guard and its cached initializer for retry. */
    @Test
    fun clearsCallbacksAfterFailure() {
        val engine = StartupEngine(DefaultContext)
        var fail = true
        var constructions = 0
        val manifest = StartupManifest {
            metaData<A> {
                constructions++
                A({
                    check(!fail) { "dependencies unavailable" }
                    emptyList()
                })
            }
        }
        val failure = assertFailsWith<StartupException> { engine.install(manifest) }
        assertEquals("dependencies unavailable", failure.cause?.message)
        fail = false
        engine.install(manifest)
        assertEquals("created", engine.initializeComponent(initializerKey<A>()))
        assertEquals(2, constructions)
    }

    /** Valid nested planning shares constructed initializers and never creates a product twice. */
    @Test
    fun preservesAcyclicReentryAndInstanceCaching() {
        val engine = StartupEngine(DefaultContext)
        var aConstructions = 0
        var bConstructions = 0
        val created = ArrayList<String>()
        val manifest = StartupManifest {
            metaData<A> {
                aConstructions++
                A({
                    engine.initializeComponent(initializerKey<B>())
                    listOf(initializerKey<B>())
                }, {
                    created.add("a")
                    "a"
                })
            }
            lazyInitializer<B> {
                bConstructions++
                B(onCreate = {
                    created.add("b")
                    "b"
                })
            }
        }
        engine.install(manifest)
        assertEquals("a", engine.initializeComponent(initializerKey<A>()))
        assertEquals("b", engine.initializeComponent(initializerKey<B>()))
        assertEquals(listOf("b", "a"), created)
        assertEquals(1, aConstructions)
        assertEquals(1, bConstructions)
    }

    private fun assertCycle(failure: StartupException, vararg expected: AnyInitializerKey) {
        assertEquals(expected.toList(), failure.components)
        assertEquals(null, failure.cause)
        val path = expected.joinToString(" -> ") { componentName(it) }
        assertEquals("Cannot initialize ${componentName(expected.first())}. Cycle detected: $path", failure.message)
    }

    private abstract class CallbackInitializer(
        private val onDependencies: () -> List<AnyInitializerKey>,
        private val onCreate: () -> String,
    ) : Initializer<String> {
        override fun dependencies(): List<AnyInitializerKey> = onDependencies()
        override fun create(context: Context): String = onCreate()
    }

    private class A(
        onDependencies: () -> List<AnyInitializerKey> = { emptyList() },
        onCreate: () -> String = { "created" },
    ) : CallbackInitializer(onDependencies, onCreate)

    private class B(
        onDependencies: () -> List<AnyInitializerKey> = { emptyList() },
        onCreate: () -> String = { "created" },
    ) : CallbackInitializer(onDependencies, onCreate)

    private class C(
        onDependencies: () -> List<AnyInitializerKey> = { emptyList() },
        onCreate: () -> String = { "created" },
    ) : CallbackInitializer(onDependencies, onCreate)
}

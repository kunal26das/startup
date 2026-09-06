package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises Kahn's algorithm: ordering, waves, incremental planning and diagnostics. */
class StartupPlannerTest {

    private val diamond = StartupManifest {
        metaData<DeltaInitializer> { DeltaInitializer() }
        lazyInitializer<BetaInitializer> { BetaInitializer() }
        lazyInitializer<GammaInitializer> { GammaInitializer() }
        lazyInitializer<AlphaInitializer> { AlphaInitializer() }
    }

    /** A dependency is always planned before the component that declares it. */
    @Test
    fun ordersDependenciesBeforeDependents() {
        val plan = StartupPlanner.plan(diamond, diamond.eagerComponents, emptySet())
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
            initializerKey<GammaInitializer>(),
            initializerKey<DeltaInitializer>(),
        )
        assertEquals(expected, plan.order)
    }

    /** The same declarations always plan the same way, however often they are planned. */
    @Test
    fun isDeterministic() {
        val first = StartupPlanner.plan(diamond, diamond.eagerComponents, emptySet()).order
        val second = StartupPlanner.plan(diamond, diamond.eagerComponents, emptySet()).order
        val rebuilt = StartupManifest {
            metaData<DeltaInitializer> { DeltaInitializer() }
            lazyInitializer<BetaInitializer> { BetaInitializer() }
            lazyInitializer<GammaInitializer> { GammaInitializer() }
            lazyInitializer<AlphaInitializer> { AlphaInitializer() }
        }
        val third = StartupPlanner.plan(rebuilt, rebuilt.eagerComponents, emptySet()).order
        assertEquals(first, second)
        assertEquals(first, third)
    }

    /**
     * The ready queue is seeded in declaration order, so reordering independent
     * components reorders the plan. Determinism alone would also be satisfied by an
     * implementation that seeded from a hash set, which would then differ between
     * Kotlin/JVM, Kotlin/Native and Kotlin/JS.
     */
    @Test
    fun seedsTheReadyQueueInDeclarationOrder() {
        val declared = StartupManifest {
            metaData<IndependentAInitializer> { IndependentAInitializer() }
            metaData<IndependentBInitializer> { IndependentBInitializer() }
            metaData<IndependentCInitializer> { IndependentCInitializer() }
        }
        val reversed = StartupManifest {
            metaData<IndependentCInitializer> { IndependentCInitializer() }
            metaData<IndependentBInitializer> { IndependentBInitializer() }
            metaData<IndependentAInitializer> { IndependentAInitializer() }
        }
        val forwards: List<AnyInitializerKey> = listOf(
            initializerKey<IndependentAInitializer>(),
            initializerKey<IndependentBInitializer>(),
            initializerKey<IndependentCInitializer>(),
        )
        assertEquals(
            forwards,
            StartupPlanner.plan(declared, declared.eagerComponents, emptySet()).order,
        )
        assertEquals(
            forwards.reversed(),
            StartupPlanner.plan(reversed, reversed.eagerComponents, emptySet()).order,
        )
        assertEquals(
            listOf(forwards),
            StartupPlanner.plan(declared, declared.eagerComponents, emptySet()).waves,
        )
    }

    /** A shared dependency of two branches is planned exactly once. */
    @Test
    fun createsADiamondApexOnlyOnce() {
        val plan = StartupPlanner.plan(diamond, diamond.eagerComponents, emptySet())
        assertEquals(1, plan.order.count { it == initializerKey<AlphaInitializer>() })
    }

    /** The waves are the Kahn levels: each one depends only on the waves before it. */
    @Test
    fun groupsADiamondIntoThreeWaves() {
        val plan = StartupPlanner.plan(diamond, diamond.eagerComponents, emptySet())
        val expected: List<List<AnyInitializerKey>> = listOf(
            listOf(initializerKey<AlphaInitializer>()),
            listOf(initializerKey<BetaInitializer>(), initializerKey<GammaInitializer>()),
            listOf(initializerKey<DeltaInitializer>()),
        )
        assertEquals(expected, plan.waves)
    }

    /**
     * `waves.flatten()` is exactly `order`, on a full plan and on an incremental one.
     *
     * This is the whole contract [StartupPlan.waves] carries. It is diagnostic data about
     * the levels of the graph, never a scheduling hook: the engine is serialized behind
     * one reentrant lock, so a consumer reading the waves is inspecting a plan it cannot
     * make this library execute any differently, and the only guarantee worth pinning is
     * that the grouping and the order describe the same walk.
     */
    @Test
    fun groupsExactlyTheComponentsItOrders() {
        val full = StartupPlanner.plan(diamond, diamond.eagerComponents, emptySet())
        assertEquals(full.order, full.waves.flatten())
        val incremental = StartupPlanner.plan(
            diamond,
            listOf(initializerKey<DeltaInitializer>()),
            setOf(initializerKey<AlphaInitializer>(), initializerKey<BetaInitializer>()),
        )
        assertEquals(incremental.order, incremental.waves.flatten())
        assertEquals(
            listOf(
                listOf<AnyInitializerKey>(initializerKey<GammaInitializer>()),
                listOf<AnyInitializerKey>(initializerKey<DeltaInitializer>()),
            ),
            incremental.waves,
        )
    }

    /** Declaring the same dependency twice is one edge, not a phantom cycle. */
    @Test
    fun collapsesDuplicateEdges() {
        val manifest = StartupManifest {
            metaData<DuplicateEdgeInitializer> { DuplicateEdgeInitializer() }
            lazyInitializer<AlphaInitializer> { AlphaInitializer() }
        }
        val plan = StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<DuplicateEdgeInitializer>(),
        )
        assertEquals(expected, plan.order)
    }

    /** Components already created are dropped from the plan along with the edges into them. */
    @Test
    fun skipsSatisfiedComponents() {
        val satisfied: Set<AnyInitializerKey> = setOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
        )
        val roots: List<AnyInitializerKey> = listOf(initializerKey<DeltaInitializer>())
        val plan = StartupPlanner.plan(diamond, roots, satisfied)
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<GammaInitializer>(),
            initializerKey<DeltaInitializer>(),
        )
        assertEquals(expected, plan.order)
    }

    /** Nothing left to do is an empty plan, not a failure. */
    @Test
    fun plansNothingWhenEverythingIsSatisfied() {
        val satisfied = diamond.components.toSet()
        val plan = StartupPlanner.plan(diamond, diamond.eagerComponents, satisfied)
        assertEquals(emptyList(), plan.order)
        assertEquals(emptyList(), plan.waves)
        assertEquals(true, plan.isEmpty)
    }

    /** A component that depends on itself is a one-node cycle. */
    @Test
    fun reportsSelfDependencyAsACycle() {
        val manifest = StartupManifest {
            metaData<SelfDependentInitializer> { SelfDependentInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<SelfDependentInitializer>(),
            initializerKey<SelfDependentInitializer>(),
        )
        assertEquals(expected, exception.components)
        val name = componentName(initializerKey<SelfDependentInitializer>())
        assertEquals("Cannot initialize $name. Cycle detected: $name -> $name", exception.message)
    }

    /** Two components that require each other are reported as a two-node cycle. */
    @Test
    fun reportsATwoNodeCycle() {
        val manifest = StartupManifest {
            metaData<CycleAInitializer> { CycleAInitializer() }
            lazyInitializer<CycleBInitializer> { CycleBInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<CycleAInitializer>(),
            initializerKey<CycleBInitializer>(),
            initializerKey<CycleAInitializer>(),
        )
        assertEquals(expected, exception.components)
    }

    /**
     * A cycle three components long is reported as all three plus the re-entry, which is
     * the shortest path on which an off-by-one in the trim would be visible: at length two
     * the trimmed and untrimmed paths coincide.
     */
    @Test
    fun reportsAThreeNodeCycle() {
        val manifest = StartupManifest {
            metaData<TriangleAInitializer> { TriangleAInitializer() }
            lazyInitializer<TriangleBInitializer> { TriangleBInitializer() }
            lazyInitializer<TriangleCInitializer> { TriangleCInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<TriangleAInitializer>(),
            initializerKey<TriangleBInitializer>(),
            initializerKey<TriangleCInitializer>(),
            initializerKey<TriangleAInitializer>(),
        )
        assertEquals(expected, exception.components)
        val names = expected.joinToString(" -> ") { componentName(it) }
        val head = componentName(initializerKey<TriangleAInitializer>())
        assertEquals("Cannot initialize $head. Cycle detected: $names", exception.message)
    }

    /**
     * A cycle reached through an acyclic approach is reported from the node the walk
     * re-entered, so the leading tail is trimmed off the path.
     */
    @Test
    fun reportsACycleFromThePointOfReEntry() {
        val manifest = StartupManifest {
            metaData<EntryInitializer> { EntryInitializer() }
            lazyInitializer<LoopHeadInitializer> { LoopHeadInitializer() }
            lazyInitializer<LoopTailInitializer> { LoopTailInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<LoopHeadInitializer>(),
            initializerKey<LoopTailInitializer>(),
            initializerKey<LoopHeadInitializer>(),
        )
        assertEquals(expected, exception.components)
        val head = componentName(initializerKey<LoopHeadInitializer>())
        val tail = componentName(initializerKey<LoopTailInitializer>())
        val message = exception.message.orEmpty()
        assertEquals("Cannot initialize $head. Cycle detected: $head -> $tail -> $head", message)
    }

    /** An unregistered dependency names itself, the component that needs it, and the remedy. */
    @Test
    fun reportsAMissingRegistration() {
        val manifest = StartupManifest {
            metaData<OrphanDependentInitializer> { OrphanDependentInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        }
        val orphan = componentName(initializerKey<OrphanInitializer>())
        val dependent = componentName(initializerKey<OrphanDependentInitializer>())
        assertEquals(
            "Cannot initialize $orphan. No initializer is registered for it, " +
                "required by $dependent. Register it in a StartupManifest with metaData " +
                "or lazyInitializer, then install that manifest with " +
                "Startup.install(context, manifest).",
            exception.message,
        )
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<OrphanInitializer>(),
            initializerKey<OrphanDependentInitializer>(),
        )
        assertEquals(expected, exception.components)
    }

    /**
     * A failure while the graph is being read is wrapped exactly as a failure inside
     * [Initializer.create] is, so a caller never has to catch an arbitrary throwable.
     * AndroidX reads `dependencies()` inside the same guard, and the two runtimes have to
     * agree.
     */
    @Test
    fun wrapsAFailureInsideDependencies() {
        val manifest = StartupManifest {
            metaData<ThrowingDependenciesInitializer> { ThrowingDependenciesInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            StartupPlanner.plan(manifest, manifest.eagerComponents, emptySet())
        }
        assertEquals("dependencies unavailable", exception.cause?.message)
        val expected: List<AnyInitializerKey> =
            listOf(initializerKey<ThrowingDependenciesInitializer>())
        assertEquals(expected, exception.components)
    }

    /** Validation walks every registered component, eager or not. */
    @Test
    fun validateAcceptsAnAcyclicManifest() {
        StartupPlanner.validate(diamond)
    }

    /** Validation reports a cycle that no eager component reaches. */
    @Test
    fun validateRejectsACyclicManifest() {
        val manifest = StartupManifest {
            lazyInitializer<CycleAInitializer> { CycleAInitializer() }
            lazyInitializer<CycleBInitializer> { CycleBInitializer() }
        }
        assertFailsWith<StartupException> { StartupPlanner.validate(manifest) }
    }
}

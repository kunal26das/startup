package io.github.kunal26das.startup

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** Exercises the non-Android runtime end to end, through the public entry point. */
class StartupRuntimeTest {

    /** Starts every test from an empty process and an empty log. */
    @BeforeTest
    fun reset() {
        Startup.reset()
        TestLog.clear()
    }

    /** Installing a manifest creates its eager components, dependencies first. */
    @Test
    fun createsEagerComponentsInDependencyOrder() {
        Startup.install(DefaultContext, diamond())
        assertEquals(listOf("alpha", "beta", "gamma", "delta"), TestLog.created)
    }

    /**
     * Everything the install created it keeps: asking for any of the four afterwards hands
     * back what was built without running a single [Initializer.create] again, which is the
     * whole observable consequence of a component being initialized.
     */
    @Test
    fun keepsEveryComponentItCreated() {
        val appInitializer = Startup.install(DefaultContext, diamond())
        val diamond = listOf("alpha", "beta", "gamma", "delta")
        assertEquals(diamond, TestLog.created)
        assertEquals("alpha", appInitializer.initializeComponent(initializerKey<AlphaInitializer>()))
        assertEquals("beta", appInitializer.initializeComponent(initializerKey<BetaInitializer>()))
        assertEquals("gamma", appInitializer.initializeComponent(initializerKey<GammaInitializer>()))
        assertEquals("delta", appInitializer.initializeComponent(initializerKey<DeltaInitializer>()))
        assertEquals(diamond, TestLog.created)
    }

    /** A lazily registered component waits until something asks for it. */
    @Test
    fun doesNotCreateLazyComponentsUntilAsked() {
        val manifest = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            lazyInitializer<BetaInitializer> { BetaInitializer() }
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("alpha"), TestLog.created)
        assertEquals("beta", appInitializer.initializeComponent(initializerKey<BetaInitializer>()))
        assertEquals(listOf("alpha", "beta"), TestLog.created)
    }

    /** Asking twice returns the very same component and never creates it again. */
    @Test
    fun reusesComponentsInsteadOfCreatingThemTwice() {
        val manifest = StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } }
        val appInitializer = Startup.install(DefaultContext, manifest)
        val first = appInitializer.initializeComponent(initializerKey<AlphaInitializer>())
        val second = appInitializer.initializeComponent(initializerKey<AlphaInitializer>())
        assertSame(first, second)
        assertEquals(listOf("alpha"), TestLog.created)
    }

    /** Only a merged entry reports as eagerly initialized. */
    @Test
    fun reportsWhichComponentsAreEager() {
        val manifest = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            lazyInitializer<BetaInitializer> { BetaInitializer() }
            remove<GammaInitializer>()
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(true, appInitializer.isEagerlyInitialized(initializerKey<AlphaInitializer>()))
        assertEquals(false, appInitializer.isEagerlyInitialized(initializerKey<BetaInitializer>()))
        assertEquals(false, appInitializer.isEagerlyInitialized(initializerKey<GammaInitializer>()))
    }

    /** A failure inside create is wrapped, and the components waiting on it never run. */
    @Test
    fun wrapsAFailureAndStopsTheRun() {
        val manifest = StartupManifest {
            metaData<FailingDependentInitializer> { FailingDependentInitializer() }
            lazyInitializer<FailingInitializer> { FailingInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        assertEquals("failing initializer", exception.cause?.message)
        val expected: List<AnyInitializerKey> = listOf(initializerKey<FailingInitializer>())
        assertEquals(expected, exception.components)
        assertEquals(listOf("failing"), TestLog.created)
    }

    /** A component may resolve another one from inside create, exactly as on Android. */
    @Test
    fun allowsReentrantResolutionFromInsideCreate() {
        val manifest = StartupManifest {
            metaData<ReentrantInitializer> { ReentrantInitializer() }
            lazyInitializer<AlphaInitializer> { AlphaInitializer() }
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("alpha", "reentrant"), TestLog.created)
        assertEquals(
            "reentrant:alpha",
            appInitializer.initializeComponent(initializerKey<ReentrantInitializer>()),
        )
        assertEquals(listOf("alpha", "reentrant"), TestLog.created)
    }

    /**
     * A component created out of turn from inside another component's create is skipped
     * when the execution loop reaches it, rather than created a second time. Both
     * components are eager and independent, so the loop really does still have the second
     * one ahead of it.
     */
    @Test
    fun skipsAComponentCreatedFromInsideAnotherCreate() {
        val manifest = StartupManifest {
            metaData<ForwardCallerInitializer> { ForwardCallerInitializer() }
            metaData<AlphaInitializer> { AlphaInitializer() }
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("alpha", "forwardCaller"), TestLog.created)
        assertEquals("alpha", appInitializer.initializeComponent(initializerKey<AlphaInitializer>()))
        assertEquals(listOf("alpha", "forwardCaller"), TestLog.created)
    }

    /**
     * An edge back into a component that is still being created is a cycle, even though
     * the planner cannot see it: the component asks for another one whose declared
     * dependency is the component itself. Without the in-flight check this recurses until
     * the stack dies.
     */
    @Test
    fun rejectsAnEdgeBackIntoAComponentBeingCreated() {
        val manifest = StartupManifest {
            metaData<BackEdgeCallerInitializer> { BackEdgeCallerInitializer() }
            lazyInitializer<BackEdgeCalleeInitializer> { BackEdgeCalleeInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<BackEdgeCallerInitializer>(),
            initializerKey<BackEdgeCalleeInitializer>(),
            initializerKey<BackEdgeCallerInitializer>(),
        )
        assertEquals(expected, exception.components)
        val caller = componentName(initializerKey<BackEdgeCallerInitializer>())
        val callee = componentName(initializerKey<BackEdgeCalleeInitializer>())
        assertEquals(
            "Cannot initialize $caller. Cycle detected: $caller -> $callee -> $caller",
            exception.message,
        )
        assertEquals(listOf("backEdgeCaller"), TestLog.created)
    }

    /** A component that asks the runtime for itself is a cycle, caught at the re-entry. */
    @Test
    fun rejectsAComponentThatAsksForItself() {
        val manifest = StartupManifest {
            metaData<SelfCallingInitializer> { SelfCallingInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<SelfCallingInitializer>(),
            initializerKey<SelfCallingInitializer>(),
        )
        assertEquals(expected, exception.components)
        val self = componentName(initializerKey<SelfCallingInitializer>())
        assertEquals("Cannot initialize $self. Cycle detected: $self -> $self", exception.message)
    }

    /** Installing a second manifest adds to the first rather than replacing it. */
    @Test
    fun composesSuccessiveInstalls() {
        Startup.install(
            DefaultContext,
            StartupManifest { metaData<AlphaInitializer> { AlphaInitializer() } },
        )
        val appInitializer = Startup.install(
            DefaultContext,
            StartupManifest { metaData<BetaInitializer> { BetaInitializer() } },
        )
        assertEquals(listOf("alpha", "beta"), TestLog.created)
        assertEquals(true, appInitializer.isEagerlyInitialized(initializerKey<AlphaInitializer>()))
        assertEquals(true, appInitializer.isEagerlyInitialized(initializerKey<BetaInitializer>()))
    }

    /**
     * A component registered under a key computed at run time joins the graph like any
     * other: it is ordered behind its dependency, created once, and reachable afterwards
     * by the key a reified call site would produce. This is the shape a host application
     * uses when it hands Kotlin the initializer instances it built itself, which is
     * otherwise the one thing that has to run outside the graph.
     */
    @Test
    fun registersAnInstanceUnderARuntimeKey() {
        val supplied: Initializer<*> = RuntimeKeyInitializer()
        val manifest = StartupManifest {
            metaData(initializerKey(supplied)) { supplied }
            lazyInitializer<AlphaInitializer> { AlphaInitializer() }
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("alpha", "runtimeKey"), TestLog.created)
        assertEquals(
            "runtimeKey",
            appInitializer.initializeComponent(initializerKey<RuntimeKeyInitializer>()),
        )
        assertEquals(listOf("alpha", "runtimeKey"), TestLog.created)
        assertEquals(true, appInitializer.isEagerlyInitialized(initializerKey<RuntimeKeyInitializer>()))
    }

    /** The expect/actual shape that extends BaseInitializer runs like any other component. */
    @Test
    fun createsAPlatformSpecificComponent() {
        val manifest = StartupManifest { metaData<PlatformInitializer> { PlatformInitializer() } }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("platform"), TestLog.created)
        assertEquals(
            "nonAndroid",
            appInitializer.initializeComponent(initializerKey<PlatformInitializer>()),
        )
    }

    /** So does the memberless one, whose create is inherited rather than expected. */
    @Test
    fun createsAMemberlessPlatformSpecificComponent() {
        val manifest = StartupManifest { metaData<MemberlessInitializer> { MemberlessInitializer() } }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("memberless"), TestLog.created)
        assertEquals(
            "nonAndroid",
            appInitializer.initializeComponent(initializerKey<MemberlessInitializer>()),
        )
    }

    /** An unregistered dependency fails before anything is created. */
    @Test
    fun rejectsAnUnregisteredDependency() {
        val manifest = StartupManifest {
            metaData<OrphanDependentInitializer> { OrphanDependentInitializer() }
        }
        assertFailsWith<StartupException> { Startup.install(DefaultContext, manifest) }
        assertEquals(emptyList(), TestLog.created)
    }

    /**
     * A tombstone something still depends on is reported as a removal, so the diagnostic
     * does not prescribe re-registering the component the application took out.
     */
    @Test
    fun reportsATombstonedDependencyAsRemovedRatherThanMissing() {
        val manifest = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            metaData<BetaInitializer> { BetaInitializer() }
            remove<AlphaInitializer>()
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        val alpha = componentName(initializerKey<AlphaInitializer>())
        val beta = componentName(initializerKey<BetaInitializer>())
        assertEquals(
            "Cannot initialize $alpha. A remove() entry hides it, and $beta still declares " +
                "it as a dependency. Drop that dependencies() entry, or stop removing the " +
                "component. Startup.install on Android reads dependencies() reflectively " +
                "without consulting a StartupManifest, so it creates it there anyway.",
            exception.message,
        )
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
        )
        assertEquals(expected, exception.components)
        assertEquals(emptyList(), TestLog.created)
    }

    /**
     * A factory registered under a key it does not build fails at the registration. Android
     * would quietly do the other thing here, because AndroidX ignores the factory and
     * reflects the key, so accepting it lets one manifest build two different graphs.
     */
    @Test
    fun rejectsAFactoryThatProducesADifferentComponent() {
        val manifest = StartupManifest {
            metaData(initializerKey<AlphaInitializer>()) { BetaInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        val alpha = componentName(initializerKey<AlphaInitializer>())
        val beta = componentName(initializerKey<BetaInitializer>())
        assertEquals(
            "Cannot initialize $alpha. Its factory produced a $beta instead. A factory has " +
                "to build the class its key names: the product would be filed under the " +
                "registered key here, while Startup.install on Android ignores the factory " +
                "and reflects the key, so one manifest would build two different graphs. " +
                "Register it under its own key.",
            exception.message,
        )
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
        )
        assertEquals(expected, exception.components)
        assertEquals(emptyList(), TestLog.created)
    }

    /**
     * A cycle that closes several hops below the component asked for names every hop. The
     * components in flight carry only the run-up, so the rest is read from the nested plan.
     */
    @Test
    fun namesEveryHopOfACycleThatClosesBelowTheComponentAskedFor() {
        val manifest = StartupManifest {
            metaData<NestedCallerInitializer> { NestedCallerInitializer() }
            lazyInitializer<NestedMiddleInitializer> { NestedMiddleInitializer() }
            lazyInitializer<NestedRootInitializer> { NestedRootInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        val caller = componentName(initializerKey<NestedCallerInitializer>())
        val middle = componentName(initializerKey<NestedMiddleInitializer>())
        val root = componentName(initializerKey<NestedRootInitializer>())
        assertEquals(
            "Cannot initialize $caller. Cycle detected: $caller -> $root -> $middle -> $caller",
            exception.message,
        )
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<NestedCallerInitializer>(),
            initializerKey<NestedRootInitializer>(),
            initializerKey<NestedMiddleInitializer>(),
            initializerKey<NestedCallerInitializer>(),
        )
        assertEquals(expected, exception.components)
        assertEquals(listOf("nestedCaller"), TestLog.created)
    }

    /**
     * Asking outright for a component a remove() entry hides is reported as a removal too,
     * and without the advice that only applies when something else declared it: there is no
     * dependencies() entry to drop on this path.
     */
    @Test
    fun reportsATombstoneAskedForDirectlyWithoutBlamingADependency() {
        val manifest = StartupManifest {
            metaData<AlphaInitializer> { AlphaInitializer() }
            lazyInitializer<GammaInitializer> { GammaInitializer() }
            remove<GammaInitializer>()
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        val exception = assertFailsWith<StartupException> {
            appInitializer.initializeComponent(initializerKey<GammaInitializer>())
        }
        val gamma = componentName(initializerKey<GammaInitializer>())
        assertEquals(
            "Cannot initialize $gamma. A remove() entry hides it. Stop removing it to make " +
                "it resolvable again. Startup.install on Android reflects the class it is " +
                "asked for without consulting a StartupManifest, so it creates it there anyway.",
            exception.message,
        )
        val expected: List<AnyInitializerKey> = listOf(initializerKey<GammaInitializer>())
        assertEquals(expected, exception.components)
        assertEquals(listOf("alpha"), TestLog.created)
    }

    /**
     * An install that failed leaves nothing behind for the next one to trip over. The
     * initializer the failed run built is discarded, so re-registering the component with a
     * corrected factory really does replace it, which is what [StartupManifest.plus]
     * promises and what AndroidX gives for free by reflecting a fresh instance every time.
     */
    @Test
    fun letsALaterInstallReplaceAComponentAFailedInstallLeftUncreated() {
        val first = StartupManifest {
            metaData<FailingInitializer> { FailingInitializer() }
            metaData(initializerKey<ReplaceableInitializer>()) { ReplaceableInitializer("old") }
        }
        assertFailsWith<StartupException> { Startup.install(DefaultContext, first) }
        assertEquals(listOf("failing"), TestLog.created)
        val second = StartupManifest {
            remove<FailingInitializer>()
            metaData(initializerKey<ReplaceableInitializer>()) { ReplaceableInitializer("new") }
        }
        Startup.install(DefaultContext, second)
        assertEquals(listOf("failing", "replaceable:new"), TestLog.created)
    }

    /**
     * A component that resolves something from inside `dependencies()` starts a nested run
     * while the outer one is still planning and has created nothing. That run must not take
     * the outer run's initializers with it when it ends: everything already built would be
     * built again, and `dependencies()` would then have been read off a different instance
     * than the one `create` runs on.
     */
    @Test
    fun keepsTheOuterPlansInitializersWhenDependenciesResolvesSomething() {
        val manifest = StartupManifest {
            metaData<BetaInitializer> {
                TestLog.record("built:beta")
                BetaInitializer()
            }
            metaData<PlanReentrantInitializer> { PlanReentrantInitializer() }
            lazyInitializer<AlphaInitializer> { AlphaInitializer() }
        }
        Startup.install(DefaultContext, manifest)
        assertEquals(1, TestLog.created.count { it == "built:beta" }, TestLog.created.toString())
        assertEquals(1, TestLog.created.count { it == "beta" }, TestLog.created.toString())
    }

    /**
     * Two nested `create` calls deep, the components in flight are not adjacent: the outer
     * one asked for a bridge that needed the inner one first. The reported path names the
     * bridge rather than joining the two in-flight components with an edge that exists
     * nowhere, which is what makes [StartupException.components] safe to assert on.
     */
    @Test
    fun namesTheComponentThatLinksTwoNestedCreateCalls() {
        val manifest = StartupManifest {
            metaData<DeepCallerInitializer> { DeepCallerInitializer() }
            lazyInitializer<DeepBridgeInitializer> { DeepBridgeInitializer() }
            lazyInitializer<DeepNestedCallerInitializer> { DeepNestedCallerInitializer() }
            lazyInitializer<DeepTailInitializer> { DeepTailInitializer() }
        }
        val exception = assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest)
        }
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<DeepCallerInitializer>(),
            initializerKey<DeepBridgeInitializer>(),
            initializerKey<DeepNestedCallerInitializer>(),
            initializerKey<DeepTailInitializer>(),
            initializerKey<DeepCallerInitializer>(),
        )
        assertEquals(expected, exception.components)
        val rendered = expected.joinToString(" -> ") { componentName(it) }
        val caller = componentName(initializerKey<DeepCallerInitializer>())
        assertEquals("Cannot initialize $caller. Cycle detected: $rendered", exception.message)
        assertEquals(listOf("deepCaller", "deepNestedCaller"), TestLog.created)
    }

    /**
     * A runner receives one wave at a time, in dependency order, with independent
     * components grouped together so a host can run them at once. The components it
     * created are the engine's afterwards, readable like any other.
     */
    @Test
    fun handsEachWaveToTheRunnerWithIndependentComponentsGrouped() {
        val sizes = mutableListOf<Int>()
        val manifest = StartupManifest {
            metaData<BetaInitializer> { BetaInitializer() }
            metaData<AlphaInitializer> { AlphaInitializer() }
            metaData<IndependentAInitializer> { IndependentAInitializer() }
        }
        val appInitializer = Startup.install(DefaultContext, manifest) { wave ->
            sizes.add(wave.size)
            for (task in wave) task()
        }
        assertEquals(listOf(2, 1), sizes)
        val created = TestLog.created
        assertEquals(true, created.indexOf("alpha") < created.indexOf("beta"), created.toString())
        assertEquals("alpha", appInitializer.initializeComponent(initializerKey<AlphaInitializer>()))
        assertEquals("beta", appInitializer.initializeComponent(initializerKey<BetaInitializer>()))
    }

    /** A task that throws inside the runner fails the install, as a sequential one does. */
    @Test
    fun wrapsAFailureRaisedInsideTheRunner() {
        val manifest = StartupManifest {
            metaData<FailingInitializer> { FailingInitializer() }
        }
        assertFailsWith<StartupException> {
            Startup.install(DefaultContext, manifest) { wave -> for (task in wave) task() }
        }
    }

    private fun diamond(): StartupManifest = StartupManifest {
        metaData<DeltaInitializer> { DeltaInitializer() }
        lazyInitializer<BetaInitializer> { BetaInitializer() }
        lazyInitializer<GammaInitializer> { GammaInitializer() }
        lazyInitializer<AlphaInitializer> { AlphaInitializer() }
    }
}

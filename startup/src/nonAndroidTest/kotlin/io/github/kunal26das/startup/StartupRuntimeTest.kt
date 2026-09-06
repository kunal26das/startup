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

    /** The creation order is readable back as keys, not only as whatever a test logged. */
    @Suppress("DEPRECATION")
    @Test
    fun recordsWhatItCreated() {
        val appInitializer = Startup.install(DefaultContext, diamond())
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<BetaInitializer>(),
            initializerKey<GammaInitializer>(),
            initializerKey<DeltaInitializer>(),
        )
        assertEquals(expected, appInitializer.initializationOrder())
        assertEquals(true, appInitializer.isInitialized(initializerKey<DeltaInitializer>()))
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
    @Suppress("DEPRECATION")
    @Test
    fun skipsAComponentCreatedFromInsideAnotherCreate() {
        val manifest = StartupManifest {
            metaData<ForwardCallerInitializer> { ForwardCallerInitializer() }
            metaData<AlphaInitializer> { AlphaInitializer() }
        }
        val appInitializer = Startup.install(DefaultContext, manifest)
        assertEquals(listOf("alpha", "forwardCaller"), TestLog.created)
        val expected: List<AnyInitializerKey> = listOf(
            initializerKey<AlphaInitializer>(),
            initializerKey<ForwardCallerInitializer>(),
        )
        assertEquals(expected, appInitializer.initializationOrder())
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
            initializerKey<BackEdgeCallerInitializer>(),
        )
        assertEquals(expected, exception.components)
        val caller = componentName(initializerKey<BackEdgeCallerInitializer>())
        assertEquals("Cannot initialize $caller. Cycle detected: $caller -> $caller", exception.message)
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
    @Suppress("DEPRECATION")
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
        assertEquals(true, initializerKey<BetaInitializer>() in appInitializer.manifest())
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

    private fun diamond(): StartupManifest = StartupManifest {
        metaData<DeltaInitializer> { DeltaInitializer() }
        lazyInitializer<BetaInitializer> { BetaInitializer() }
        lazyInitializer<GammaInitializer> { GammaInitializer() }
        lazyInitializer<AlphaInitializer> { AlphaInitializer() }
    }
}

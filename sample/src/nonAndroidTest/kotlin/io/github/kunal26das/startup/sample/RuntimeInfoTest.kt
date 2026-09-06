package io.github.kunal26das.startup.sample

import io.github.kunal26das.startup.DefaultContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the `expect` over `BaseInitializer` shape on whichever of the ten non-Android
 * targets is running.
 *
 * The compile is most of the assertion, and it is a compile a platform build cannot make
 * on its own: [RuntimeInfoInitializer] is metadata-compiled by `./gradlew build` as well,
 * which is the task that rejects the same class with its `create` redeclaration removed.
 * What is left to check at run time is that the pair behaves like any other initializer.
 */
class RuntimeInfoTest {

    /** The right `actual` was compiled in, and it created the component. */
    @Test
    fun theShapeNamesThisLibrarysRuntime() {
        SampleLauncher.launch()
        assertEquals(
            "io.github.kunal26das.startup",
            SampleStartup.runtimeInfo(DefaultContext).name,
        )
    }

    /** The dependency list comes from `BaseInitializer`, which the `expect` never names. */
    @Test
    fun theShapeInheritsAnEmptyDependencyList() {
        assertEquals(emptyList(), RuntimeInfoInitializer().dependencies())
    }
}

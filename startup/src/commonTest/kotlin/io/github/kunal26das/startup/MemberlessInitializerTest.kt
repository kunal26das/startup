package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the memberless platform-specific initializer shape on the eleven platform
 * compilations, which are the only compilations it survives.
 *
 * An `expect class` extending [BaseInitializer] needs no body for a platform compilation,
 * because `create` is inherited rather than expected; a metadata compilation rejects the
 * same file, so a consumer whose module has one has to redeclare `create` the way
 * [PlatformInitializer] does. The compile of [MemberlessInitializer] here is most of the
 * assertion, and this checks the pair behaves like the redeclared one.
 */
class MemberlessInitializerTest {

    /** The dependency list comes from [BaseInitializer], not from a redeclaration. */
    @Test
    fun theShapeInheritsAnEmptyDependencyList() {
        assertEquals(emptyList(), MemberlessInitializer().dependencies())
    }

    /** It has one key on every target, whichever `actual` was compiled in. */
    @Test
    fun theShapeHasOneKeyOnEveryTarget() {
        val manifest = StartupManifest { metaData<MemberlessInitializer> { MemberlessInitializer() } }
        assertEquals(true, manifest.isEager(initializerKey<MemberlessInitializer>()))
        assertEquals<AnyInitializerKey>(
            initializerKey<MemberlessInitializer>(),
            initializerKey(MemberlessInitializer()),
        )
    }
}

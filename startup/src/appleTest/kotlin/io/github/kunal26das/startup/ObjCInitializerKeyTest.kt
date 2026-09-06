@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package io.github.kunal26das.startup

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate

/**
 * Pins the Objective-C class overload of [initializerKey], which is how Swift names a
 * Kotlin initializer without constructing one. A Kotlin test binary exports nothing to
 * Objective-C, so the class that reaches the happy path can only come from a linked
 * framework; `:startup`'s `checkObjCExport` asserts the export shape, and this asserts
 * that the one input a Swift caller can get wrong fails loudly.
 */
class ObjCInitializerKeyTest {

    /** A class Kotlin did not declare has no key, and says so. */
    @Test
    fun anObjectiveCClassThatKotlinDidNotDeclareIsRejected() {
        val failure = assertFailsWith<StartupException> { initializerKey(NSDate) }
        assertTrue(failure.message.orEmpty().contains("NSDate"))
        assertTrue(failure.message.orEmpty().contains("initializerKey(initializer:)"))
    }
}

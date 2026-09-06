package io.github.kunal26das.startup.sample

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplicationMain

/**
 * The entry point of the sample application on iOS.
 *
 * The other targets print the report and exit, which is what a program does. An iOS app is
 * not that: it hands control to `UIApplicationMain` and never returns, so the report is
 * rendered by [SampleViewController] instead, and printed as well so that
 * `xcrun simctl launch --console` shows the same lines the other ten targets print.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
fun main() {
    memScoped {
        val arguments = listOf("sample".cstr.ptr).toCValues()
        autoreleasepool {
            UIApplicationMain(1, arguments, null, NSStringFromClass(SampleAppDelegate))
        }
    }
}

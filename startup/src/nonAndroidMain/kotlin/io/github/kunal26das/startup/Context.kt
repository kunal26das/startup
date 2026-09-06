@file:OptIn(ExperimentalObjCName::class)

package io.github.kunal26das.startup

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Off Android there is no platform context, so this is an empty abstract class. Extend it
 * to hand initializers something of your own, or use [DefaultContext] when they need
 * nothing.
 *
 * The Objective-C export names it `StartupContext`, not `Context`. A Kotlin `typealias`
 * does not survive that boundary, so without the rename Swift would see the bare name
 * this library already tells Kotlin authors to avoid, and it would collide with
 * `UIViewControllerRepresentable.Context` in every Compose Multiplatform host.
 */
@ObjCName("StartupContext")
actual abstract class Context actual constructor()

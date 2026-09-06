package io.github.kunal26das.startup.sample

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow

/**
 * The application delegate iOS launches the sample through.
 *
 * It is the iOS counterpart of the `main` the other targets run: UIKit finds it by the
 * name [main] hands to `UIApplicationMain`, instantiates it with `init`, and the shared
 * startup graph is booted from the view controller it installs.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
class SampleAppDelegate : UIResponder, UIApplicationDelegateProtocol {

    private var mainWindow: UIWindow? = null

    /**
     * The `init` UIKit instantiates the delegate with. Without `@OverrideInit` the
     * Objective-C runtime finds no `init` on the class and the process dies at launch
     * with `init is not implemented`.
     */
    @OverrideInit
    constructor() : super()

    /** The window UIKit reads back after [application] created it. */
    override fun window(): UIWindow? = mainWindow

    /** Lets UIKit replace the window. */
    override fun setWindow(window: UIWindow?) {
        mainWindow = window
    }

    /** Shows [SampleViewController], which is what boots and renders the sample. */
    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?,
    ): Boolean {
        mainWindow = UIWindow(frame = UIScreen.mainScreen.bounds).apply {
            rootViewController = SampleViewController()
            makeKeyAndVisible()
        }
        return true
    }

    /** Makes the class visible to the Objective-C runtime, which is how UIKit finds it. */
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta
}

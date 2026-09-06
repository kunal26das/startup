package io.github.kunal26das.startup.sample

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightRegular
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UITextView
import platform.UIKit.UIViewController
import platform.UIKit.labelColor
import platform.UIKit.systemBackgroundColor

/**
 * Displays [SampleReport] on screen and prints the same lines to the process output.
 *
 * This is the whole iOS entry point, and it is the same call every other target makes:
 * [SampleLauncher.report] boots the shared graph and returns the lines, which are then
 * shown. Nothing about the startup library is iOS specific; only the way the lines reach
 * a human is, so `xcrun simctl launch --console` prints exactly what the desktop build
 * prints while the simulator shows it.
 */
@OptIn(ExperimentalForeignApi::class)
class SampleViewController : UIViewController(nibName = null, bundle = null) {

    private var report: UITextView? = null

    /** Boots the sample and installs a scrollable, monospaced view of its report. */
    override fun viewDidLoad() {
        super.viewDidLoad()
        val lines = SampleLauncher.report()
        for (line in lines) println(line)
        view.backgroundColor = UIColor.systemBackgroundColor
        report = UITextView(frame = view.bounds).apply {
            text = lines.joinToString("\n")
            font = UIFont.monospacedSystemFontOfSize(13.0, UIFontWeightRegular)
            textColor = UIColor.labelColor
            backgroundColor = UIColor.systemBackgroundColor
            setEditable(false)
            textContainerInset = UIEdgeInsetsMake(16.0, 16.0, 16.0, 16.0)
            contentInsetAdjustmentBehavior =
                UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentAlways
            view.addSubview(this)
        }
    }

    /** Keeps the report the size of the screen when the device is rotated. */
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        report?.setFrame(view.bounds)
    }
}

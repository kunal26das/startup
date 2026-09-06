package io.github.kunal26das.startup.sample.app

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import io.github.kunal26das.startup.sample.AndroidSampleStartup
import io.github.kunal26das.startup.sample.SampleReport

/**
 * Shows what `androidx.startup` did to the shared graph before this activity existed.
 *
 * Nothing here bootstraps anything. The components were created by
 * `androidx.startup.InitializationProvider` at process start, from the `<meta-data>`
 * entries the `:sample` library contributes to the merged AndroidManifest, which is the
 * whole point of the Android path: the same initializers the other ten targets install
 * programmatically arrive here through the manifest instead.
 *
 * [AndroidSampleStartup.isEager] is the proof. AndroidX answers it from what the provider
 * discovered in the manifest, so a component started any other way still reports `false`.
 */
class MainActivity : Activity() {

    /** Renders the report on screen and mirrors it to Logcat under the `StartupSample` tag. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lines = report()
        for (line in lines) Log.i(TAG, line)
        val text = TextView(this).apply {
            text = lines.joinToString("\n")
            typeface = Typeface.MONOSPACE
            textSize = TEXT_SIZE
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }
        setContentView(
            ScrollView(this).apply {
                fitsSystemWindows = true
                addView(text)
            },
        )
    }

    private fun report(): List<String> = buildList {
        add("started by androidx.startup.InitializationProvider: ${AndroidSampleStartup.isEager(this@MainActivity)}")
        val drift = AndroidSampleStartup.androidManifestDrift(this@MainActivity)
        add("androidmanifest drift: ${drift.size}")
        for (line in drift) add("  $line")
        add("")
        addAll(SampleReport.lines(this@MainActivity))
    }

    private companion object {

        private const val TAG = "StartupSample"

        private const val TEXT_SIZE = 12f

        private const val PADDING = 32
    }
}

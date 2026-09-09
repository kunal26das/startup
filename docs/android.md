# Android startup

[Back to the README](../README.md)

On Android, `Initializer` and `AppInitializer` are type aliases for AndroidX's types.
Your shared initializer is a regular AndroidX initializer. AndroidX constructs it reflectively
and caches its result; it does not invoke the factories in `StartupManifest`.

## Automatic startup

Declare eager initializers in the AndroidManifest contributed by your shared module
(`src/androidMain/AndroidManifest.xml`) or your application (`src/main/AndroidManifest.xml`):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="com.example.startup.AnalyticsInitializer"
                android:value="androidx.startup" />
        </provider>
    </application>
</manifest>
```

This uses the [README example](../README.md#quick-start). AndroidX starts analytics before
`Application.onCreate` and creates its declared logger dependency first. You do not need a
separate XML entry for a dependency unless you want it to be an eager root itself.

Use fully qualified initializer class names. Every initializer must be a class with a public
no-argument constructor, including platform `actual` classes. An `object` or a constructor that
requires an injected client cannot be created by AndroidX. Obtain platform data from `create(context)`
and services from declared dependencies. See [platform-specific initializers](platform-initializers.md).

The [sample manifest](../sample/src/androidMain/AndroidManifest.xml) is a complete working example.

## Manual startup and lazy access

If your application chooses a later startup point, leave those initializers out of the provider's
XML and install the shared graph yourself. For example, inside your application's `onCreate`:

```kotlin
val startup = Startup.install(applicationContext, appStartup)
val logger = startup.initializeComponent(initializerKey<LoggerInitializer>())
```

Import `appStartup` and `LoggerInitializer` from your shared module, and `Startup` and
`initializerKey` from `io.github.kunal26das.startup`. `Startup.install` starts the eager entries;
AndroidX follows their dependencies and constructs their classes. The Kotlin factories are still ignored.

For an automatically started app, obtain the same instance with `Startup.getInstance(applicationContext)`.
Android-only code can also use AndroidX's original spelling:

```kotlin
androidx.startup.AppInitializer.getInstance(applicationContext)
    .initializeComponent(LoggerInitializer::class.java)
```

`isEagerlyInitialized` reports discovery through the provider's XML. Starting a component only
through `Startup.install` does not make that query return `true`. It is not an “already created” check.
A custom `WaveRunner` is ignored on Android.

## Disable an included initializer

If another library contributes an eager initializer, remove its metadata in your application's
provider block:

```xml
<meta-data
    android:name="com.example.library.DebugInitializer"
    tools:node="remove" />
```

The surrounding manifest needs `xmlns:tools`, as shown above. For the corresponding shared graph,
use `remove<DebugInitializer>()` after `include(libraryManifest)`.

These are separate operations. A Kotlin `remove` entry cannot undo provider startup, and removing
an eager root does not prevent AndroidX from constructing it when another initializer depends on it.
Remove that dependency too if the component must never run. On other platforms, a dependency on a
removed component makes planning fail.

## Keep Android XML and the shared graph aligned

`StartupManifest` does not generate, merge, or validate Android XML. A component can work on every
other target and silently fail to start automatically on Android if its metadata is missing.
Test that your intended eager roots match in both places.

For the README example, put `AndroidManifestParityTest.kt` in the shared module's
`src/androidHostTest/kotlin/com/example/startup/`. The test below compares AndroidX entries inside
its provider, leaving unrelated application metadata alone:

```kotlin
package com.example.startup

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** Keeps this application's Android and shared eager registrations aligned. */
class AndroidManifestParityTest {
    /** The provider declares exactly the eager roots in the shared graph. */
    @Test
    fun eagerRegistrationsMatch() {
        val path = checkNotNull(System.getProperty("myapp.androidManifest"))
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(File(path))
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val providers = document.getElementsByTagName("provider")
        val declared = mutableSetOf<String>()
        for (index in 0 until providers.length) {
            val provider = providers.item(index) as org.w3c.dom.Element
            if (provider.getAttributeNS(androidNamespace, "name") !=
                "androidx.startup.InitializationProvider") continue
            val entries = provider.getElementsByTagName("meta-data")
            for (entryIndex in 0 until entries.length) {
                val entry = entries.item(entryIndex) as org.w3c.dom.Element
                if (entry.getAttributeNS(androidNamespace, "value") == "androidx.startup") {
                    declared.add(entry.getAttributeNS(androidNamespace, "name"))
                }
            }
        }
        assertEquals(appStartup.eagerComponents.map { it.name }.toSet(), declared)
    }
}
```

Declare the XML as a Gradle test input so editing it reruns the test. For a module using the Kotlin
Multiplatform Android library plugin, add this to its `build.gradle.kts`:

```kotlin
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

kotlin {
    android {
        withHostTest {}
    }
}

tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    val androidManifest = layout.projectDirectory.file("src/androidMain/AndroidManifest.xml")
    inputs.file(androidManifest)
        .withPropertyName("androidMainManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("myapp.androidManifest", androidManifest.asFile.absolutePath)
}
```

`withHostTest {}` creates the host-test compilation; add it to your existing `android` block if
host tests are not enabled yet. Use `implementation(kotlin("test"))` in your test dependencies.
Android application modules use
different test task/source-set names; adapt the task selection and manifest path accordingly.

This checks the source manifest you supply, not the final merged manifest. If dependencies contribute
other AndroidX initializers, define which ones this parity check owns, or inspect the merged manifest
as well. Account explicitly for XML-only initializers and merger removals instead of silently ignoring
a shared eager registration.

The repository's [parity test](../sample/src/androidHostTest/kotlin/io/github/kunal26das/startup/sample/AndroidManifestParityTest.kt)
uses a smaller parser for its dedicated sample manifest. To verify your own check, temporarily remove
an eager metadata entry, confirm the test fails, restore it, and confirm the test passes again.

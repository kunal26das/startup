# startup

Initialize your Kotlin Multiplatform app in dependency order. Write shared initializers once,
register what should start eagerly, and resolve everything else when you need it.

On Android, the library uses [AndroidX App Startup](https://developer.android.com/topic/libraries/app-startup)
directly through type aliases. On other platforms, it supplies a runtime with explicit registration,
cached results, and cycle diagnostics. Initialization is sequential on the calling thread by default.

[Quick start](#quick-start) · [Android setup](docs/android.md) · [Swift](docs/swift.md) ·
[Run the sample](sample/README.md) · [Contribute](CONTRIBUTING.md) · [Changelog](CHANGELOG.md)

## Installation

Add the dependency to your shared module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.kunal26das:startup:3.0.1")
        }
    }
}
```

Your dependency repositories need `mavenCentral()` and `google()` for the AndroidX dependency.
Use `api` plus framework `export(...)` when Swift needs these types; follow the
[Swift integration guide](docs/swift.md).

| Requirement | Value |
| --- | --- |
| Kotlin compiler | 2.4 or newer; this repository builds with 2.4.10 |
| JVM bytecode target | 11 or newer, including Android consumers |
| Android devices | API 21 or newer |
| Android compile SDK | 34 or newer |

The Android artifact exposes `androidx.startup:startup-runtime` as an API dependency and publishes the same
two Android floors that dependency does: **`minSdk` 21** and **`minCompileSdk` 34**.
The build checks these values against both AARs and the documentation.

Dependency examples use **3.0.1**. See the [changelog](CHANGELOG.md) for fixes and the
[migration guide](docs/migration.md) for behavior changes when upgrading.

## Quick start

This example creates a logger before starting analytics. Put the following four files in your
shared module under `src/commonMain/kotlin/com/example/startup/`.

### 1. Define a component

`Logger.kt`:

```kotlin
package com.example.startup

/** A minimal logger for this example. */
class Logger {
    /** Writes a message to standard output. */
    fun log(message: String) = println(message)
}
```

### 2. Create its initializer

`LoggerInitializer.kt`:

```kotlin
package com.example.startup

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/** Creates the shared logger. */
class LoggerInitializer : BaseInitializer<Logger>() {
    /** Builds the logger on first use. */
    override fun create(context: StartupContext): Logger = Logger()
}
```

Extend `BaseInitializer<T>` when there are no dependencies. `T` is the value callers receive.
Use `StartupContext` in shared code to avoid a name collision with `android.content.Context`.

### 3. Declare a dependency

`AnalyticsInitializer.kt`:

```kotlin
package com.example.startup

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.initializerKey

/** Starts analytics after the logger is available. */
class AnalyticsInitializer : Initializer<Unit> {
    /** Performs the startup work without returning a service. */
    override fun create(context: StartupContext) {
        val logger = Startup.getInstance(context)
            .initializeComponent(initializerKey<LoggerInitializer>())
        logger.log("Analytics ready")
    }

    /** Ensures the logger is created first on every platform. */
    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoggerInitializer>())
}
```

Declare every ordering requirement in `dependencies()`. Use `Initializer<Unit>` for startup work
that has no value to return. Keep constructors lightweight and put startup work in `create`.

### 4. Register the graph

`AppStartup.kt`:

```kotlin
package com.example.startup

import io.github.kunal26das.startup.StartupManifest

/** The shared startup graph. */
val appStartup = StartupManifest {
    lazyInitializer<LoggerInitializer> { LoggerInitializer() }
    metaData<AnalyticsInitializer> { AnalyticsInitializer() }
}
```

| Registration | Behavior |
| --- | --- |
| `metaData<T> { T() }` | Creates the component eagerly during startup |
| `lazyInitializer<T> { T() }` | Creates it on demand, including when an eager component depends on it |
| `include(otherManifest)` | Merges another graph; later registrations override earlier ones |
| `remove<T>()` | Hides an included registration; Android XML removal is a separate step |

Here the logger is registered as lazy but is created at startup because analytics depends on it.
All Android-compatible initializers need a **public no-argument constructor**. Register factories
that build the exact initializer class named by the key.

### Android startup

In your shared module's `src/androidMain/AndroidManifest.xml`, declare the eager initializer:

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

AndroidX starts analytics automatically and follows its dependency to the logger. This path needs
no `Startup.install` call. **The Kotlin manifest does not generate Android XML**: keep the eager
registrations in both files aligned. The [Android guide](docs/android.md) covers manual startup,
removal, and a test that catches registration drift.

### Other platforms

Call `Startup.install` once from your application's Kotlin entry point before using its services.
For example, put this `main` in a JVM or Node source set (`src/desktopMain` or `src/jsMain` in this
repository), rather than in `commonMain`:

```kotlin
package com.example.startup

import io.github.kunal26das.startup.DefaultContext
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.initializerKey

/** Boots the graph and reads a cached component. */
fun main() {
    val startup = Startup.install(DefaultContext, appStartup)
    val logger = startup.initializeComponent(initializerKey<LoggerInitializer>())
    logger.log("App ready")
}
```

Output:

```text
Analytics ready
App ready
```

`DefaultContext` is available in non-Android source sets. Shared code can instead accept a
`StartupContext` from its host. Use an application context on Android. For an iOS host, see the
[Swift startup example](docs/swift.md).

The first `Startup.getInstance` or `Startup.install` call establishes the process-wide context.
Repeated reads return the cached component; another install merges registrations and does not
recreate components that already exist.

## Validate your graph

Add `src/commonTest/kotlin/com/example/startup/AppStartupTest.kt` so missing registrations, cycles,
and mismatched factories fail before launch:

```kotlin
package com.example.startup

import io.github.kunal26das.startup.StartupPlanner
import kotlin.test.Test

/** Checks the graph without starting its services. */
class AppStartupTest {
    /** Every registered component has a valid dependency graph. */
    @Test
    fun graphIsValid() {
        StartupPlanner.validate(appStartup)
    }
}
```

This needs `implementation(kotlin("test"))` in `commonTest.dependencies`.
Validation constructs initializers and reads `dependencies()` on every platform, including Android,
but does not call `create`. Keep factories and dependency declarations free of startup side effects.

## Platform behavior

| Platform | How startup runs | What to remember |
| --- | --- | --- |
| Android | AndroidX reads the merged AndroidManifest, or you call `Startup.install` manually | XML controls automatic startup; factories are ignored and classes are constructed reflectively |
| iOS, macOS, JVM, Linux, Windows | Call `Startup.install` with a `StartupManifest` | Register every component you intend to resolve |
| JavaScript and Wasm | Call `Startup.install` with a `StartupManifest` | Ordinary initializers work; the blocking `CoroutineInitializer` bridge is unsupported |

Configured targets: `android`, `desktop` (JVM), `iosArm64`, `iosSimulatorArm64`, `iosX64`,
`macosArm64`, `macosX64`, `linuxX64`, `mingwX64`, `js`, and `wasmJs`.
Both web targets support Node and browsers. See [platform verification](CONTRIBUTING.md#platform-verification)
for host requirements and CI coverage; a linked test binary does not necessarily mean it ran.

Dependencies always run first. The order of unrelated components can differ between Android and
other targets. A custom `WaveRunner` must also honor thread requirements: tasks that read through
`AppInitializer` must stay on the installing thread, even for completed dependencies.
Start with the default runner and read [concurrency and coroutines](docs/runtime.md#custom-wave-runners)
before adding parallel startup.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Works elsewhere but never starts on Android | Add the eager initializer's fully qualified class name to Android XML |
| Android fails with `NoSuchMethodException` | Use a class with a public no-argument constructor; get dependencies in `create` |
| “No initializer is registered” | Register the component and install its manifest before resolving it |
| “Cycle detected” | Inspect `StartupException.components` and remove the circular dependency |
| A runner refuses a dependency read or startup hangs | Follow the [runner thread rules](docs/runtime.md#custom-wave-runners) and [coroutine restrictions](docs/runtime.md#coroutine-initializers) |
| Kotlin metadata or JVM inlining error | Use a Kotlin 2.4-compatible compiler and JVM target 11 or newer |
| Swift cannot see registration helpers | Check both `api(...)` and `export(...)` in the [Swift guide](docs/swift.md) |

AndroidX runtime failures use AndroidX's exception type; shared planner validation uses this
library's `StartupException`. See [diagnostics](docs/runtime.md#diagnostics) for the distinction.

## Documentation

| I want to… | Guide |
| --- | --- |
| Configure Android startup, removal, or parity tests | [Android](docs/android.md) |
| Initialize a different SDK on each platform | [Platform-specific initializers](docs/platform-initializers.md) |
| Register and resolve components from Swift | [Swift and Objective-C](docs/swift.md) |
| Compose manifests, inspect plans, or use custom runners | [Runtime and API guide](docs/runtime.md) |
| Upgrade an existing application | [Migration guide](docs/migration.md) and [changelog](CHANGELOG.md) |
| Run an example on my platform | [Sample app](sample/README.md) |
| Build, test, or contribute to this repository | [Contributing](CONTRIBUTING.md) |

## License

[Apache-2.0](LICENSE).

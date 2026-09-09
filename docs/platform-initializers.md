# Platform-specific initializers

Use an `expect` initializer when starting a component requires different platform code. Keep
the component's public type shared, then implement the initializer in each platform source set.
The initializer keeps the same class name and registers in the same manifest on every target.

For shared initialization and installation, start with the [README](../README.md).
See [Android setup](android.md) for automatic discovery and manifest entries.

## A complete expect/actual example

This example returns information about the current platform. Replace the body of `create` with
your SDK setup when adapting it to an application.

Define the product in `commonMain`, in `com/example/bootstrap/PlatformInfo.kt`:

```kotlin
package com.example.bootstrap

/** Identifies the platform that created this component. */
class PlatformInfo(val name: String)
```

Define its initializer in `commonMain`, in `com/example/bootstrap/PlatformInfoInitializer.kt`:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/** Creates platform information with an implementation supplied by each target. */
expect class PlatformInfoInitializer() : BaseInitializer<PlatformInfo> {
    /** Builds the shared product using platform code. */
    override fun create(context: StartupContext): PlatformInfo
}
```

Add the Android implementation at the same package path in `androidMain`:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/** Creates platform information from the Android context. */
actual class PlatformInfoInitializer actual constructor() : BaseInitializer<PlatformInfo>() {
    /** Includes the Android application's package name. */
    actual override fun create(context: StartupContext): PlatformInfo =
        PlatformInfo("Android (${context.packageName})")
}
```

Add the Apple implementation at the same package path in `appleMain`:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.BaseInitializer
import io.github.kunal26das.startup.StartupContext

/** Creates platform information shared by Apple targets. */
actual class PlatformInfoInitializer actual constructor() : BaseInitializer<PlatformInfo>() {
    /** Returns the platform label. */
    actual override fun create(context: StartupContext): PlatformInfo = PlatformInfo("Apple")
}
```

Provide an `actual` for every target your module includes. An intermediate source set such as
`appleMain` can share an implementation between its targets; use `iosMain` if the implementation
is specific to iOS. These source sets must be connected to your module's target hierarchy.

Register it from `commonMain`:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.StartupManifest

/** Registers platform information for eager creation. */
val platformManifest = StartupManifest {
    metaData<PlatformInfoInitializer> { PlatformInfoInitializer() }
}
```

Install this manifest as described in the [README](../README.md). For a complete repository
example, see the sample's
[shared RuntimeInfoInitializer](../sample/src/commonMain/kotlin/io/github/kunal26das/startup/sample/RuntimeInfoInitializer.kt),
[Android implementation](../sample/src/androidMain/kotlin/io/github/kunal26das/startup/sample/RuntimeInfoInitializer.kt),
and [non-Android implementation](../sample/src/nonAndroidMain/kotlin/io/github/kunal26das/startup/sample/RuntimeInfoInitializer.kt).
The sample defines its own `nonAndroidMain` source set to share one implementation across all
non-Android targets.

## Declare abstract members in common code

Keep `override fun create(...)` in the `expect` declaration. `BaseInitializer` provides a
`dependencies()` implementation, but its `create` remains abstract. The matching platform method
therefore uses `actual override`.

This shorter declaration is incomplete for a normal shared module:

```kotlin
expect class PlatformInfoInitializer() : BaseInitializer<PlatformInfo>
```

It can pass individual platform compilation and linking while failing
`compileCommonMainKotlinMetadata` with “does not implement abstract member.” A successful
platform build does not validate this declaration. Run your module's metadata compilation or
the full `./gradlew build`; this repository checks the sample with
`./gradlew :sample:compileCommonMainKotlinMetadata` as well.

If you implement `Initializer` directly, redeclare **both** abstract members in the `expect`:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.AnyInitializerKey
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.StartupContext

/** Declares both initializer methods when implementing the interface directly. */
expect class PlatformInfoInitializer() : Initializer<PlatformInfo> {
    /** Builds the shared product using platform code. */
    override fun create(context: StartupContext): PlatformInfo
    /** Names the components that must be created first. */
    override fun dependencies(): List<AnyInitializerKey>
}
```

This is an alternative to the `BaseInitializer` declaration above. Its `actual` classes
implement both methods with `actual override`. Return the keys of the components needed by
`create` from `dependencies()`.

The sample's
[CrashReportingInitializer declaration](../sample/src/commonMain/kotlin/io/github/kunal26das/startup/sample/CrashReportingInitializer.kt)
and its [Android](../sample/src/androidMain/kotlin/io/github/kunal26das/startup/sample/CrashReportingInitializer.kt)
and [non-Android](../sample/src/nonAndroidMain/kotlin/io/github/kunal26das/startup/sample/CrashReportingInitializer.kt)
implementations show a reporter that declares and reads a logger dependency.

## Keep Android construction compatible

An initializer used on Android must be a **class with a public no-argument constructor**.
AndroidX creates it reflectively and ignores the factory supplied to `StartupManifest`.

- Put initialization work in `create(context)`, not the constructor.
- Obtain configuration from the context or declared dependencies. Constructor arguments and
  state stored only in a supplied initializer instance do not carry over to Android's instance.
- Avoid `object`, inner, and anonymous initializer implementations when AndroidX must instantiate
  them. Use a named class with the required constructor.
- Make the factory return exactly the class its key names. Registering a subclass under a
  superclass key is rejected when the graph is planned.

A factory can work on every non-Android target and still fail at Android startup if its class
requires constructor arguments. Keep shared registrations in the form shown above:
`metaData<PlatformInfoInitializer> { PlatformInfoInitializer() }`.

For Android automatic startup, the manifest entry uses the initializer's fully qualified class
name, here `com.example.bootstrap.PlatformInfoInitializer`. Its `actual` implementation still
implements AndroidX's `Initializer` interface directly. See [Android setup](android.md) for the
complete provider configuration.

## Register an initializer supplied at run time

A plugin or host application may supply an initializer whose class is unknown to the calling
code. Build its key from the instance and register it with the explicit-key overload:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.StartupManifest
import io.github.kunal26das.startup.initializerKey

/** Adds host-supplied initializers to the normal startup graph. */
fun suppliedManifest(initializers: List<Initializer<*>>): StartupManifest = StartupManifest {
    for (initializer in initializers) {
        metaData(initializerKey(initializer)) { initializer }
    }
}
```

Install the result normally. Each supplied initializer participates in dependency ordering,
deduplication, and diagnostics. Use `lazyInitializer(key) { instance }` to defer creation, or
`remove(key)` to remove a registration contributed by another manifest.

An instance key identifies its **class**, not that particular object. Two instances of the same
class produce equal keys; they do not register two separate components. The last registration
for that key wins when manifests are composed.

The instance overload returns `AnyInitializerKey`. Read its product with
`initializeComponentOrNull`, which also represents an initializer that returns `null`:

```kotlin
package com.example.bootstrap

import io.github.kunal26das.startup.AppInitializer
import io.github.kunal26das.startup.Initializer
import io.github.kunal26das.startup.initializeComponentOrNull
import io.github.kunal26das.startup.initializerKey

/** Reads the product associated with a supplied initializer's runtime class. */
fun readSupplied(app: AppInitializer, initializer: Initializer<*>): Any? =
    app.initializeComponentOrNull(initializerKey(initializer))
```

See [runtime behavior](runtime.md) for manifest composition, caching, and removal semantics.
Android still constructs the class reflectively; it does not reuse the supplied instance.
For Swift and Objective-C hosts, see the [Swift guide](swift.md), which uses these same key
overloads.

## Share the initializer when only a value differs

An entire initializer does not need to be `expect` just to obtain a platform name or SDK setting.
Keep one shared initializer and put the varying value or operation behind a smaller `expect`
declaration. The sample's
[Platform declaration](../sample/src/nonAndroidMain/kotlin/io/github/kunal26das/startup/sample/Platform.kt)
and [Apple implementation](../sample/src/appleMain/kotlin/io/github/kunal26das/startup/sample/Platform.kt)
demonstrate this approach. An `expect` can live in an intermediate source set, so targets that
do not need it need not see it.

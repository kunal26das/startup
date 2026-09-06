# startup

A Kotlin Multiplatform port of [AndroidX App Startup](https://developer.android.com/topic/libraries/app-startup).

On Android it **is** `androidx.startup`. `Initializer`, `AppInitializer`, `Context` and the class
token are `typealias`es of the AndroidX types, so an initializer written once in `commonMain`
compiles to `implements androidx.startup.Initializer`, is discovered by the same
`InitializationProvider`, and is instantiated by the same reflection. There is no wrapper type and
no adapter anywhere in the Android path.

On the other ten targets the library ships its own runtime. It computes the initialization order
with Kahn's algorithm, executes it sequentially on the calling thread, and reports cycles as a
trimmed path rather than as a bare "cycle detected".

## Targets

`android`, `desktop` (JVM), `iosArm64`, `iosSimulatorArm64`, `iosX64`, `macosArm64`, `macosX64`,
`linuxX64`, `mingwX64`, `js` (browser and Node), `wasmJs` (browser and Node).

`macosX64` and `iosX64` are compiled and linked but never run: they are disabled on an arm64 Mac and
CI has no x86_64 macOS runner, so a green build is not evidence that their test binaries executed.

## Installation

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.kunal26das:startup:1.0.0")
        }
    }
}
```

The Android artifact depends on `androidx.startup:startup-runtime` with `api` scope, so an Android
consumer can implement `Initializer` without declaring AndroidX itself, and it publishes the same
two Android floors that dependency does: **`minSdk` 21** and **`minCompileSdk` 34**. Adopting this
library never narrows the device range or forces a `compileSdk` move relative to plain
`androidx.startup`; `:startup:checkAndroidFloors` fails the build if either floor rises.

Two requirements come from the way the artifacts are compiled:

- **Kotlin 2.4 or newer.** The library ships metadata version 2.4.0. AGP 9's bundled compiler is
  older and reports *Class ... was compiled with an incompatible version of Kotlin*, so an Android
  consumer has to apply the Kotlin Multiplatform or Kotlin plugin at 2.4.x rather than rely on it.
- **JVM target 11 or newer.** Every registration function is `inline`, and Kotlin refuses to inline
  bytecode built for a newer JVM target than the caller's.

One more requirement comes from the consumer's own code rather than from the artifacts. An
application that writes an `expect class` initializer, which is the shape below for anything
touching a platform SDK, needs

```kotlin
kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
}
```

Kotlin still reports `expect`/`actual` classes as Beta, so without that flag every such initializer
emits a `BETA_EXPECT_ACTUAL_CLASSES` warning. It is a warning, not an error, and a consumer whose
initializers all live in `commonMain` and extend `BaseInitializer` needs no flag at all.

## Usage

### Write an initializer once, in `commonMain`

```kotlin
class LoggerInitializer : BaseInitializer<Logger>() {
    override fun create(context: StartupContext): Logger = Logger()
}

class NetworkInitializer : Initializer<Network> {
    override fun create(context: StartupContext): Network {
        val logger = Startup.getInstance(context)
            .initializeComponent(initializerKey<LoggerInitializer>())
        return Network(logger)
    }

    override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoggerInitializer>())
}
```

`BaseInitializer` exists because neither half of an `expect`/`actual` pair may give a member a
default body. Extend it whenever an initializer has no dependencies and the `dependencies()`
override disappears.

Use `StartupContext` rather than `Context` in shared code. Both name the same type, but `Context`
collides with `android.content.Context` in any file that imports both.

### Write a platform-specific initializer for platform SDKs

Starting Crashlytics, a Cocoa reporter and a browser reporter are different calls, so declare the
initializer `expect` and give each platform its own `actual`. The class keeps one fully qualified
name on every target, so a single AndroidManifest `<meta-data>` entry addresses it whichever
`actual` is compiled in, and it registers in the same manifest as a shared initializer.

```kotlin
expect class CrashReportingInitializer() : Initializer<CrashReporting> {
    override fun create(context: StartupContext): CrashReporting
    override fun dependencies(): List<AnyInitializerKey>
}
```

```kotlin
actual class CrashReportingInitializer actual constructor() : Initializer<CrashReporting> {
    actual override fun create(context: StartupContext): CrashReporting {
        val logger = Startup.getInstance(context)
            .initializeComponent(initializerKey<LoggerInitializer>())
        return CrashReporting("AndroidCrashReporter(${context.packageName})", logger)
    }

    actual override fun dependencies(): List<AnyInitializerKey> =
        listOf(initializerKey<LoggerInitializer>())
}
```

The `expect` class has to redeclare every member it does not inherit a body for. `Initializer`
carries both of its members abstract, so a subclass that declares none is abstract; an `expect` that
omits them fails with *"has no corresponding expected declaration"* on the `actual` side.

**Extend `BaseInitializer` and only `create` has to be redeclared.** `BaseInitializer` supplies a
concrete `dependencies()`, so the `expect` names one member instead of two. This is the shape to
reach for unless the initializer really does declare dependencies:

```kotlin
expect class CrashReportingInitializer() : BaseInitializer<CrashReporting> {
    override fun create(context: StartupContext): CrashReporting
}
```

```kotlin
actual class CrashReportingInitializer actual constructor() : BaseInitializer<CrashReporting>() {
    actual override fun create(context: StartupContext): CrashReporting =
        CrashReporting("AndroidCrashReporter(${context.packageName})")
}
```

Redeclaring `create` on the `expect` is what makes it an expected member, so the `actual` spells its
override `actual override`. Leave the `actual` off and the compiler answers *Declaration must be
marked with 'actual'*.

**There is a shorter form, and it is not portable.** Drop the body from the `expect` entirely and
`create` becomes an inherited abstract member rather than an expected one, so the `actual` overrides
it with a plain `override` and the `expect` never names it at all:

```kotlin
expect class CrashReportingInitializer() : BaseInitializer<CrashReporting>
```

That compiles, links and runs on all eleven **platform** compilations. A **metadata** compilation
rejects it:

```
e: CrashReportingInitializer.kt:5:8 Class 'CrashReportingInitializer' is not abstract and does not
implement abstract member:
expect fun create(context: Context): T
```

The task is `compileCommonMainKotlinMetadata`, and every KMP module with a shared `commonMain` has
one: anything published, anything running KSP in `commonMain`, anything relying on cinterop
commonization. `./gradlew build` runs it; `compileKotlin<Target>`, `compileAndroidMain` and
`link<Target>` do not, so this shape can pass a target-by-target verification on all eleven and still
fail the next `build`. It is not a `BaseInitializer` quirk either — a plain `abstract class` with an
unimplemented abstract member behaves identically under an `expect class`, so nothing this library
could ship would change it. Reach for it only in a module you know has no metadata compilation, such
as a test source set.

Either shape compiles to `implements androidx.startup.Initializer` with a public no-argument
constructor and AndroidX's own `dependencies()` signature, so reflection is unaffected.
`PlatformInitializer` and `MemberlessInitializer` in the library's own `commonTest` pin both across
the eleven platform compilations and `AndroidInitializerContractTest` pins their bytecode; `sample`'s
`RuntimeInfoInitializer` is what keeps this section honest, because it lives in a `commonMain` that
really is metadata-compiled. Delete its `override fun create` line and `./gradlew build` goes red.

**Every `actual` must be a class with a public no-argument constructor.** AndroidX ignores the
factory in the manifest object and reflects with `getDeclaredConstructor().newInstance()`, so an
`actual object` compiles everywhere and throws only on Android, at process start. `sample`'s
`CrashReportingContractTest` pins both halves of that contract with reflection.

Only the part that varies has to be `expect`. Where the initializer itself is shared and just needs
a per-platform value, keep one class in `commonMain` and put the seam behind a small `expect` — the
sample resolves its SDK name through `expect object Platform`, with `actual`s in `appleMain`,
`desktopMain`, `jsMain`, `wasmJsMain`, `linuxMain` and `mingwMain`. An `expect` may be declared in
an intermediate source set, so `Platform` lives in `nonAndroidMain` and Android never sees it.

### Declare the manifest once, in `commonMain`

```kotlin
val manifest = StartupManifest {
    metaData<AnalyticsInitializer> { AnalyticsInitializer() }
    lazyInitializer<NetworkInitializer> { NetworkInitializer() }
    lazyInitializer<LoggerInitializer> { LoggerInitializer() }
}
```

`metaData` registers a component that is initialized eagerly at startup, the equivalent of a
`<meta-data android:value="androidx.startup" />` entry. `lazyInitializer` registers one that is
created only when something asks for it. `remove<T>()` is the equivalent of `tools:node="remove"`
and hides an entry an included manifest contributed.

Manifests compose, later entries winning, so a library can ship one that an application overrides:

```kotlin
val applicationManifest = StartupManifest {
    include(libraryManifest)
    remove<LibraryDebugInitializer>()
    metaData<ApplicationInitializer> { ApplicationInitializer() }
}
```

On Android `remove<T>()` only suppresses what `Startup.install` would otherwise start. A component a
library contributed through its own AndroidManifest is created by `InitializationProvider` before any
application code runs, so nothing here can reach it; suppressing that needs a real
`tools:node="remove"` entry in the application's AndroidManifest, which `androidManifestMetadata()`
emits.

### Register a component whose key only exists at run time

`metaData<T>`, `lazyInitializer<T>` and `remove<T>()` name the component at compile time. Each also
has an overload that takes the key instead, for an initializer the compiler cannot name: one a host
application constructed and handed to Kotlin, one discovered from a plugin, or one written in Swift.
`initializerKey(initializer)` builds the key from the instance, and it is the same key
`initializerKey<T>()` would have reified:

```kotlin
fun manifest(supplied: List<Initializer<*>>): StartupManifest = StartupManifest {
    metaData<AnalyticsInitializer> { AnalyticsInitializer() }
    for (initializer in supplied) metaData(initializerKey(initializer)) { initializer }
}
```

Registered that way, a host-supplied initializer is an ordinary node: it is ordered behind whatever
it declares in `dependencies()`, other components may depend on it, it is created once, and a cycle
or a missing registration around it is diagnosed like any other. Running such initializers outside
the graph, before `Startup.install`, gives up all four.

### Boot

```kotlin
Startup.install(context, manifest)

val analytics = Startup.getInstance(context)
    .initializeComponent(initializerKey<AnalyticsInitializer>())
```

### On Android

On Android the AndroidManifest is the primary way to start components: `InitializationProvider`
reads it before any application code runs, exactly as it does for a pure AndroidX app. Declare the
eager components there and nothing else is needed at boot. `sample/src/androidMain/AndroidManifest.xml`
is a worked example, and `AndroidManifestParityTest` fails the build if it drifts from
`SampleStartup.manifest`.

`Startup.install` remains available as the programmatic equivalent, and is the only route on the
other ten targets.

`androidManifestMetadata()` prints the `<meta-data>` lines, one per eager component and nothing
else. Run it on Android:

```kotlin
println(manifest.androidManifestMetadata())
```

and that is the whole output, one line per entry:

```xml
<meta-data android:name="com.example.AnalyticsInitializer" android:value="androidx.startup" />
```

Those lines are what goes inside the provider block. The block itself is yours to write once, and
is not part of the output:

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
                android:name="com.example.AnalyticsInitializer"
                android:value="androidx.startup" />
        </provider>
    </application>
</manifest>
```

Generate the lines on Android. `InitializationProvider` resolves each name with `Class.forName`, so
every one has to be fully qualified, and only the Android key can supply that; the other ten targets
name a class simply, because `KClass.qualifiedName` does not compile on Kotlin/JS. The
`xmlns:tools` declaration is required as soon as a tombstone is in the output.

AndroidX answers `isEagerlyInitialized` from what `InitializationProvider` discovered in the
AndroidManifest, so a component started only by `Startup.install` still reports `false` there.
Pasting the generated lines into the manifest is what makes the two agree.

### Keep the two Android registries in step

This is the one failure this library can produce that plain `androidx.startup` cannot, and it is
worth naming. On Android the `StartupManifest` factories are never called: `InitializationProvider`
reads the AndroidManifest and nothing else. So a component registered with `metaData<T>` and left
out of the XML runs correctly on ten targets and silently never runs on the eleventh. There is no
exception, no log and no lint check, because from AndroidX's point of view nothing is wrong.

Two Android-only functions close that gap without the consumer having to own a test:

```kotlin
manifest.verifyAndroidManifest(context)

val drift: List<String> = manifest.androidManifestDrift(context)
```

Both read the `<meta-data>` entries `androidx.startup.InitializationProvider` really declares, from
`PackageManager.getProviderInfo(..., GET_META_DATA)`, which is the same registry AndroidX itself
reads at process start. `androidManifestDrift` returns one line per disagreement and creates
nothing; `verifyAndroidManifest` throws `StartupException` listing them. Call either from a
debug-only initializer and a forgotten `<meta-data>` line becomes a launch-time failure naming the
component instead of a component that quietly never runs.

Both directions are reported. A component eager in the `StartupManifest` and absent from the XML
never runs on Android; one the XML declares while the `StartupManifest` keeps it lazy or removed
runs eagerly on Android alone. A name the XML declares that the `StartupManifest` has never heard of
is **not** reported: a mixed application is free to declare initializers written directly against
`androidx.startup` beside these, and reporting those would make the check useless in exactly the
applications that need it most.

`manifest.androidManifestDrift(declared: Set<String>)` is the same comparison against a set of names
you supply, and is `commonMain` code, so a unit test can read the XML itself and assert on it.
`sample`'s `AndroidManifestParityTest` does exactly that and fails the build on drift.

**Put the XML on the test task's inputs, or the check stops running.** A test that reads a file
Gradle does not know about stays `UP-TO-DATE` when only that file changes, so deleting a
`<meta-data>` line leaves the build green and the drift undetected — which is the very failure the
check exists to catch:

```kotlin
tasks.withType<Test>().configureEach {
    val manifest = layout.projectDirectory.file("src/androidMain/AndroidManifest.xml")
    inputs.file(manifest)
        .withPropertyName("androidMainManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("myapp.androidManifest", manifest.asFile.absolutePath)
}
```

`verifyAndroidManifest(context)` has no such trap, because it runs in the application rather than in
Gradle and reads the merged manifest of the process it is running in.

Android-only source sets can keep using the verbatim AndroidX spelling against the very same
components:

```kotlin
androidx.startup.AppInitializer.getInstance(context)
    .initializeComponent(AnalyticsInitializer::class.java)
```

### On every other platform

There is no manifest merger and no `ContentProvider`, so `Startup.install` is the only registration
step. Pass `DefaultContext` when your initializers need nothing from the platform:

```kotlin
Startup.install(DefaultContext, manifest)
```

Initialization runs sequentially on the calling thread, and every entry point is serialized behind
one reentrant lock, which is what AndroidX gets from `synchronized (sLock)`: a component is created
exactly once however many threads ask for it, and an `Initializer.create` may call back into
`initializeComponent` without deadlocking. There are no coroutines anywhere in the library, because
`runBlocking` does not exist on Kotlin/JS or Kotlin/Wasm. `StartupPlan.waves` exposes the Kahn
levels as data, and that is an inspection API, not a scheduling hook — see **Startup is sequential,
and the waves will not change that** below.

`AppInitializer` carries three members here that it does not carry on Android, and they are the
diagnostics for "did my graph actually run the way I declared it?":

```kotlin
val appInitializer = Startup.install(DefaultContext, manifest)

appInitializer.isInitialized(initializerKey<NetworkInitializer>())
appInitializer.initializationOrder()
appInitializer.manifest()
```

**They do not exist on Android, and therefore not in `commonMain`.** On Android `AppInitializer`
**is** `androidx.startup.AppInitializer`, which exposes neither the order it created things in nor
whether a given component exists, and keeps no accessible state to derive either from. Declaring
them in the `expect` would only move the failure from an unresolved reference to a stub that lies,
so a call to any of the three compiles in `desktopMain` and fails in `commonMain`. Shared code that
needs an order on all eleven targets has to record one from inside its own `create`, which is what
`sample`'s `SampleReport` does.

### From Swift and Objective-C

**Export the library from your framework first.** `implementation("io.github.kunal26das:startup")`
is enough for Kotlin and is not enough for Swift. A dependency module that a framework does not
export has its module name mangled into every class the header does carry, and the declarations that
appear in no exported signature are dropped from it altogether. Add both lines below, or the Swift
snippets in this section name types that do not exist:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        api("io.github.kunal26das:startup:1.0.0")
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            export("io.github.kunal26das:startup:1.0.0")
        }
    }
}
```

A build whose targets are declared elsewhere, in a convention plugin, reaches the same frameworks
without naming them:

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export("io.github.kunal26das:startup:1.0.0")
        }
    }
}
```

`export` requires `api`. Left on `implementation`, the build fails with *dependencies exported in
the framework are not specified as API dependencies of a corresponding source set*.

You may already have it without writing the line. A framework with `transitiveExport = true` that
exports a module which declares `api("io.github.kunal26das:startup:1.0.0")` exports this library too,
which is what a convention plugin that exports a shared `core` module typically produces. Check the
generated header for `swift_name("InitializerKeyKt")` before adding anything: if it is there, the
export is already in place.

This is what a consumer's own framework header contains either way. It is measured rather than
predicted: `:sample:checkConsumerObjCExport` links one framework each way and greps both.

| declaration        | with `export(...)`  | without it                |
| ------------------ | ------------------- | ------------------------- |
| `Initializer`      | `Initializer`       | `StartupInitializer`      |
| `Context`          | `StartupContext`    | `StartupStartupContext`   |
| `InitializerKey`   | `InitializerKey`    | `StartupInitializerKey`   |
| `StartupManifest`  | `StartupManifest`   | `StartupStartupManifest`  |
| `initializerKey()` | `InitializerKeyKt`  | **absent entirely**       |
| `DefaultContext`   | `DefaultContext`    | **absent entirely**       |

The last two rows are the ones that bite. A top-level function facade appears in no exported
signature, so `InitializerKeyKt` never reaches the header at all: a Swift class can conform to
`StartupInitializer` and can never build a key, which leaves its `dependencies()` able to return only
`[]`. Swift reports `cannot find type 'InitializerKey' in scope` and says nothing about `export`.

With the export in place, `Startup`, `StartupManifest`, `StartupManifestBuilder`, `AppInitializer`,
`Initializer`, `BaseInitializer`, `InitializerKey`, `InitializerKeyKt`, `StartupContext` and
`DefaultContext` all arrive under those names, so an iOS host can build a manifest and implement an
initializer in Swift. Two things follow from the fact that a `reified` type argument cannot cross
that boundary:

- **The `reified` registration functions are hidden from the header.** `metaData<T>`,
  `lazyInitializer<T>`, `remove<T>()` and `initializerKey<T>()` carry `@HiddenFromObjC`, because
  Kotlin/Native would otherwise export their non-inline bodies with `T` collapsed to `Initializer<*>`
  — four Swift-callable methods that compile, run, and register every call site under one key.
- **Swift uses the key overloads instead.** `metaData(component:factory:)`,
  `lazyInitializer(component:factory:)`, `remove(component:)` and `initializerKey(initializer:)` are
  exported and do what they say:

```swift
let manifest = StartupManifest.companion.invoke { builder in
    let lifecycle = ViewControllerLifecycleInitializer()
    builder.metaData(component: InitializerKeyKt.initializerKey(initializer: lifecycle)) { lifecycle }
}
Startup.shared.install(context: DefaultContext.shared, manifest: manifest)
```

**The Swift name of `Context` is `StartupContext`.** A Kotlin `typealias` does not survive the
Objective-C export, so the class itself carries `@ObjCName("StartupContext")`. That keeps the Swift
name equal to the alias this README already tells Kotlin authors to prefer, and it keeps a bare
`Context` out of the framework's namespace — which matters, because `UIViewControllerRepresentable`
declares a `Context` of its own and a framework-level one shadows it in the iOS host file every
Compose Multiplatform app has, with a `does not conform to protocol` error that never mentions the
name.

**Naming a Kotlin initializer from Swift needs no instance.** `initializerKey(objCClass:)` takes the
class object, on all Apple targets:

```swift
final class HostInitializer: NSObject, Initializer {
    func create(context: StartupContext) -> Any? { ... }

    func dependencies() -> [InitializerKey<Initializer>] {
        [InitializerKeyKt.initializerKey(objCClass: KoinInitializer.self)]
    }
}
```

`initializerKey(initializer:)` remains the overload for an initializer the host already holds, which
is what a Swift one always is: a Swift class has no Kotlin class behind it, and `KotlinKClass` is not
obtainable from Swift. Prefer `objCClass:` for a Kotlin component, because it does not run that
component's constructor, so a constructor with a side effect does not fire merely from naming it. It
throws `StartupException` for a class Kotlin did not declare.

`:startup`'s `checkObjCExport` task links `Startup.framework` and asserts on the generated header,
and `:sample`'s `checkConsumerObjCExport` does the same for the two frameworks a *consumer* gets, so
neither the export shape nor the recipe above can regress unnoticed.

## Where the two runtimes differ

Everything in the API mapping below behaves the same on all eleven targets. Three things do not, and
all three are cases where code written and tested on Android would misbehave elsewhere.

- **The order of independent components.** Both runtimes always create a dependency before the
  component that declares it. For components with no edge between them, AndroidX walks each eager
  root depth first while `StartupPlanner` emits Kahn levels, so the two pick different valid
  topological orders. Anything that must run before something else has to say so in `dependencies()`.
- **Components nobody registered.** AndroidX resolves any class reflectively and never consults a
  manifest, so on Android `initializeComponent` succeeds for a component that no `StartupManifest`
  registers and for one registered with `remove<T>()`. Off Android the manifest is the only registry
  there is, and both throw `StartupException`.
- **Where a failure comes from.** Off Android the planner raises this library's `StartupException`
  with a `components` path. On Android the failure comes out of AndroidX as
  `androidx.startup.StartupException`, with the message `Cannot initialize <FQCN>. Cycle detected.`
  and no path. `StartupPlanner.validate(manifest)` is the way to get this library's diagnostics on
  Android too.
- **Which registry decides.** Off Android the `StartupManifest` is the whole registry. On Android
  it is the AndroidManifest, and the factories in the `StartupManifest` are never called. That is
  the one failure mode adopting this library adds, and `verifyAndroidManifest(context)` is the
  answer to it; see **Keep the two Android registries in step** above.

### Startup is sequential, and the waves will not change that

Both runtimes create one component at a time on the calling thread. If you are migrating off a
hand-rolled runtime that ran each level of the graph concurrently, **you lose that**, and the cost is
the critical path of every initializer that really does block.

`StartupPlan.waves` looks like the way to get it back and is not. It is diagnostic data: the levels
of the graph, where `waves.flatten()` is exactly `plan.order`. Nothing in the public API executes a
level, and nothing can be made to. `AppInitializer.initializeComponent` and `Startup.install` are
both serialized behind one reentrant lock, so N threads driving one wave through them queue instead
of overlapping, and a `create` running on a worker thread that reads a dependency back through
`initializeComponent` — the pattern AndroidX documents, and the one every example on this page uses
— would block on a lock the calling thread still holds. Releasing that lock for the duration of a
wave would let a second `install` interleave, which is the thing it exists to prevent, so a
`runWave` parameter is not a change this library is going to make.

Concurrent startup therefore means not using `AppInitializer` for the concurrent part. You already
have everything needed to do that outside the library: plan with
`StartupPlanner.plan(manifest, roots, satisfied)`, read `plan.waves`, construct your own initializers
— you wrote the factories — call `create(context)` on a level in parallel, and hold the results
yourself. That is a real fork in the road, not a seam, and it is worth measuring first: an
initializer that hands its work to a background scheduler and returns immediately costs the same
either way.

## API mapping

| `androidx.startup`                                   | `io.github.kunal26das.startup`                    | On Android                     |
| ---------------------------------------------------- | -------------------------------------------------- | ------------------------------ |
| `androidx.startup.Initializer<T>`                     | `Initializer<T>`                                    | `typealias`                    |
| `android.content.Context`                             | `Context`, `StartupContext`                         | `typealias`                    |
| `Class<out Initializer<*>>`                           | `InitializerKey<T>`, `AnyInitializerKey`            | `typealias` to `java.lang.Class`|
| `MyInitializer::class.java`                           | `initializerKey<MyInitializer>()`                   | inlines to a class constant    |
| `instance.getClass()`                                 | `initializerKey(instance)`                          | `::class.java` on the instance |
| no equivalent                                         | `initializerKey(objCClass)`                         | **absent**, Apple targets only |
| `androidx.startup.AppInitializer`                     | `AppInitializer`                                    | `typealias`                    |
| `AppInitializer.getInstance(context)`                 | `Startup.getInstance(context)`                      | delegates to the static        |
| `AppInitializer.initializeComponent(component)`       | `AppInitializer.initializeComponent(component)`     | same method                    |
| `AppInitializer.isEagerlyInitialized(component)`      | `AppInitializer.isEagerlyInitialized(component)`    | same method                    |
| `<meta-data android:value="androidx.startup" />`      | `StartupManifest { metaData<T> { T() } }`           | still the manifest             |
| no equivalent                                         | `StartupManifest { metaData(key) { it } }`          | still the manifest             |
| `tools:node="remove"`                                 | `StartupManifest { remove<T>() }`                   | still the manifest             |
| `InitializationProvider.onCreate()`                   | `Startup.install(context, manifest)`                | eagerly initializes            |
| `androidx.startup.StartupException`                   | `StartupException`                                  | **not** a `typealias`          |
| no equivalent                                         | `manifest.androidManifestMetadata()`                | Android only in practice       |
| no equivalent                                         | `manifest.verifyAndroidManifest(context)`           | Android only                   |
| no equivalent                                         | `manifest.androidManifestDrift(context)`            | Android only                   |
| no equivalent                                         | `AppInitializer.isInitialized(component)`           | **absent** on Android          |
| no equivalent                                         | `AppInitializer.initializationOrder()`              | **absent** on Android          |
| no equivalent                                         | `AppInitializer.manifest()`                         | **absent** on Android          |

`StartupException` is deliberately our own type. AndroidX annotates its exception
`@RestrictTo(LIBRARY)`, so aliasing it would make every consumer's `catch` clause fail lint's
error-severity `RestrictedApi` check. Failures raised by AndroidX itself still arrive as AndroidX's
own type on Android, exactly as they do in an app that uses `androidx.startup` directly.

There is no `expect companion object` on `AppInitializer` because a Java static has no member for
one to match, which is why `Startup` exists.

The last three rows are the only asymmetry in the table, and it runs the other way from the rest:
they are members the non-Android runtime has and Android does not, so they are absent from the
`expect` and unavailable in `commonMain`. `androidx.startup.AppInitializer` exposes no equivalent
and no accessible state to build one from. See **On every other platform** above.

## The constructor-argument footgun

Register **factories**, never instances, and let every factory construct its initializer with no
arguments:

```kotlin
val manifest = StartupManifest {
    metaData<NetworkInitializer> { NetworkInitializer() }
}
```

A factory that passes constructor arguments compiles, and works on all ten non-Android targets:

```kotlin
val manifest = StartupManifest {
    metaData<NetworkInitializer> { NetworkInitializer(httpClient) }
}
```

On Android it throws. AndroidX never calls the factory: `InitializationProvider` and
`AppInitializer` build every initializer with `getDeclaredConstructor().newInstance()`, so a class
without a public no-argument constructor fails at runtime with
`StartupException(NoSuchMethodException)`. Take what an initializer needs from `create(context)`,
or from another component resolved through `initializeComponent`.

For the same reason the factory is never used to construct the initializer twice: it is called at
most once per manifest, and only off Android.

## Diagnostics

These diagnostics come from `StartupPlanner`, which is what runs on the ten non-Android targets. On
Android the initialization itself is AndroidX's, so a failure there arrives as
`androidx.startup.StartupException` instead. `StartupPlanner.validate(manifest)` is `commonMain`
code and runs everywhere, including in an Android unit test, so it is how to get the diagnostics
below on every platform.

A cycle names the component the walk re-entered and prints the cycle itself, not the acyclic path
that led to it. For `Entry -> LoopHead -> LoopTail -> LoopHead`:

```
Cannot initialize LoopHead. Cycle detected: LoopHead -> LoopTail -> LoopHead
```

A path longer than twelve components is elided in the middle. The same path is available as data on
`StartupException.components`, first element repeated last, so a test can assert on it instead of
matching text:

```kotlin
val exception = assertFailsWith<StartupException> { StartupPlanner.validate(manifest) }
assertEquals(
    listOf(
        initializerKey<LoopHeadInitializer>(),
        initializerKey<LoopTailInitializer>(),
        initializerKey<LoopHeadInitializer>(),
    ),
    exception.components,
)
```

A dependency nobody registered names both ends and the remedy:

```
Cannot initialize Orphan. No initializer is registered for it, required by OrphanDependent.
Register it in a StartupManifest with metaData or lazyInitializer, then install that manifest
with Startup.install(context, manifest).
```

Component names are fully qualified on Android, where the key is a `java.lang.Class`, and simple
elsewhere, because `KClass.qualifiedName` does not compile on Kotlin/JS.

`StartupPlanner.validate(manifest)` walks the whole graph without creating anything, which makes a
cycle or a missing registration a test failure rather than a launch failure.

A component that re-enters the runtime from inside its own `create` for something that leads back to
it is caught the same way, at the point of re-entry, rather than recursing until the stack dies.

## Running the sample

`sample` is a real application on every target, not a compilation unit that only has to type check.
It boots the shared graph and prints what happened: which components are registered and how eagerly,
the order they were actually created in, the platform SDK `CrashReportingInitializer` started, and
the shared logger after an `analytics.track("launch")` call. Those lines come from `SampleReport` in
`commonMain`, so every entry point is one loop over the same list.

| Target          | Command                                                                              |
| --------------- | ------------------------------------------------------------------------------------ |
| Desktop, JVM    | `./gradlew :sample:desktopRun`                                                         |
| Android         | `./gradlew :androidApp:installDebug` and launch **App Startup sample**                 |
| macOS           | `./gradlew :sample:runDebugExecutableMacosArm64`                                       |
| iOS simulator   | `./gradlew :sample:iosSimulatorApp`, then `xcrun simctl install` and `launch`           |
| Node, Kotlin/JS | `./gradlew :sample:jsNodeRun`                                                          |
| Browser, JS     | `./gradlew :sample:jsBrowserRun`                                                       |
| Node, Wasm      | `./gradlew :sample:wasmJsNodeRun`                                                      |
| Browser, Wasm   | `./gradlew :sample:wasmJsBrowserRun`                                                   |
| macOS, x86-64   | `./gradlew :sample:runDebugExecutableMacosX64` (Rosetta)                                |
| Linux           | `./gradlew :sample:linkDebugExecutableLinuxX64`, then run it in an amd64 container      |
| Windows         | `./gradlew :sample:linkDebugExecutableMingwX64`, then run it under Wine                 |

Every row above was executed on one Apple-silicon Mac. `macosX64` runs through Rosetta. The Linux and
Windows binaries run inside a Linux VM, which is worth doing before a release because it is the only
local proof those two targets do more than link:

```
colima start --vm-type=vz --vz-rosetta
docker run --rm --platform linux/amd64 -v "$PWD":/w -w /w ubuntu:24.04 \
  ./sample/build/bin/linuxX64/debugExecutable/sample.kexe
docker run --rm --platform linux/amd64 -v "$PWD":/w -w /w -e HOME=/tmp -e WINEDEBUG=-all \
  debian:bookworm bash -c 'apt-get update -qq && apt-get install -y -qq wine && \
  wine sample/build/bin/mingwX64/debugExecutable/sample.exe'
```

Two targets link here but cannot be executed on this host, for reasons outside the project:

- `iosX64` needs an x86-64 simulator runtime. The installed iOS 26.5 runtime ships `dyld_sim` as arm64
  only, so a Rosetta spawn aborts with `could not use 'dyld_sim' because it is not a compatible arch`.
- `iosArm64` needs a physical device attached; a paired but disconnected iPhone reports
  `transport: None` and cannot be targeted.

iOS is a real app, installed and launched like any other. `:sample:iosSimulatorApp` links the
Kotlin/Native binary, lays out `SampleApp.app` around the `Info.plist` in `sample/iosApp`, and
ad-hoc signs it:

```
./gradlew :sample:iosSimulatorApp
xcrun simctl boot "iPhone 17 Pro Max"
xcrun simctl install booted sample/build/iosApp/SampleApp.app
xcrun simctl launch --console booted io.github.kunal26das.startup.sample.app
```

The app shows the report in a scrollable monospaced view and prints the same lines, so `--console`
gives the desktop output while the simulator shows the screen. Its entry point is the `main` in
`iosMain`, which hands control to `UIApplicationMain`; the other ten targets share the `main` in
`consoleMain`, which prints and exits.

The two browser tasks start a webpack dev server on <http://localhost:8080/> and never exit; stop
them with Ctrl-C. They bind the same port, so run one at a time.

**Not runnable on a macOS host.** `linuxX64` and `mingwX64` link there and nowhere else: the
artifacts are an x86-64 ELF binary and a PE32+ executable, and running either on macOS fails with
`exec format error`. Link them locally, run them on Linux and Windows, which is what CI does.
`macosX64`, `iosX64` and `iosArm64` link as well and are not run either, for want of an x86-64 host
and a physical device.

`androidApp` is the one module that exists purely to be launched. `sample` stays a Kotlin
Multiplatform *library*, because its own `AndroidManifest.xml` and `AndroidManifestParityTest` are
what prove the shared initializers are declarable the AndroidX way; `androidApp` applies
`com.android.application`, depends on `sample`, and does nothing but display the report and mirror it
to Logcat under the `StartupSample` tag. It is not published.

Nothing in `androidApp` calls `Startup.install`. The components are created by
`androidx.startup.InitializationProvider` at process start, from the `<meta-data>` entries `sample`
contributes to the merged manifest, and the first line of the report is AndroidX's own
`isEagerlyInitialized` answering for that:

```
started by androidx.startup.InitializationProvider: true
```

Run the same sample on two platforms and the difference documented above is visible in the output:
Android creates the graph in AndroidX's depth-first order and everything else in Kahn levels.

## Building

```
./gradlew build
./gradlew testAndroidHostTest desktopTest macosArm64Test iosSimulatorArm64Test jsNodeTest \
          wasmJsNodeTest linkDebugTestLinuxX64 linkDebugTestMingwX64
```

`build` also links a debug executable for every Kotlin/Native target and assembles the Android
sample app, because `sample` declares `binaries.executable()` and `androidApp` is a real application
module. Three verification tasks run beside the tests:

- `:startup:checkObjCExport` links `Startup.framework` for `iosSimulatorArm64` and asserts on the
  generated Objective-C header — the reified registration functions absent, the key-taking overloads
  present, `Context` exported as `StartupContext`.
- `:sample:checkConsumerObjCExport` links two frameworks from `sample`, which is a *consumer* of the
  library: one exports `:startup` and one does not. It asserts that the first carries the names the
  Swift snippets above use and the second carries the `Startup`-prefixed ones with no
  `InitializerKeyKt`, so the difference `export(...)` makes stays true.
- `:startup:checkAndroidFloors` unzips the published AAR and fails if `minSdkVersion` rises above 21
  or `minCompileSdk` above 34.

The first two need a macOS host and skip elsewhere; the third runs anywhere.

`linuxX64Test`, `mingwX64Test`, `macosX64Test` and `iosX64Test` are disabled on an arm64 Mac, so
linking their test binaries is the local proof. CI runs them for real on `ubuntu-latest` and
`windows-latest`.

## License

Apache-2.0. See [LICENSE](LICENSE).

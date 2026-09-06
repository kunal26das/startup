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
            implementation("io.github.kunal26das:startup:3.0.0")
        }
    }
}
```

Use `api` rather than `implementation` if an iOS host has to see these types, because a framework
can only `export` a dependency its source set declares with `api`; see **From Swift and
Objective-C** below.

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
`tools:node="remove"` entry in the application's AndroidManifest, written by hand.

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

Each eager component is one `<meta-data>` line inside the provider block. Write those lines by hand,
exactly as a plain `androidx.startup` application does:

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

`InitializationProvider` resolves each name with `Class.forName`, so every one has to be fully
qualified. The `xmlns:tools` declaration is required as soon as a `tools:node="remove"` entry is in
the block.

AndroidX answers `isEagerlyInitialized` from what `InitializationProvider` discovered in the
AndroidManifest, so a component started only by `Startup.install` still reports `false` there.
Declaring the component in the manifest is what makes the two agree.

### Keep the two Android registries in step

This is the one failure this library can produce that plain `androidx.startup` cannot, and it is
worth naming. On Android the `StartupManifest` factories are never called: `InitializationProvider`
reads the AndroidManifest and nothing else. So a component registered with `metaData<T>` and left
out of the XML runs correctly on ten targets and, in an app that relies on
`InitializationProvider` alone, silently never runs on the eleventh. `Startup.install`
does start it on Android, but `isEagerlyInitialized` still reports `false` for it. There is no
exception, no log and no lint check, because from AndroidX's point of view nothing is wrong.

Until 1.1.0 the library answered that itself, with `verifyAndroidManifest(context)`,
`androidManifestDrift(context)` and `androidManifestDrift(declared)`. **2.0.0 removes all three**:
`androidx.startup` has no counterpart for any of them, and mirroring `androidx.startup` is this
library's whole contract. **The problem has not gone away with them**, so read the rest of this
section rather than treating the removal as a fix. The answer from 2.0.0 is the one a plain
`androidx.startup` application already uses, and it is two things.

**The AndroidManifest is the source of truth on Android.** Write its `<meta-data>` entries by hand,
declare there exactly what should start eagerly, and treat the `StartupManifest` as the registry for
the other ten targets. Nothing in the library reconciles the two for you.

**Keep a parity test of your own.** It is a dozen lines against the API that is staying, and it goes
in an Android source set because that is where a key is a `java.lang.Class` and can name its
component the way `Class.forName` needs it named:

```kotlin
class AndroidManifestParityTest {

    @Test
    fun theManifestDeclaresExactlyTheEagerComponents() {
        val xml = File(System.getProperty("myapp.androidManifest")!!).readText()
        val declared = Regex("<meta-data[^>]*?android:name=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(manifest.eagerComponents.map { it.name }.toSet(), declared)
    }
}
```

Both directions matter. A component eager in the `StartupManifest` and absent from the XML never
runs on Android; one the XML declares while the `StartupManifest` keeps it lazy or removed runs
eagerly on Android alone. A name the XML declares that the `StartupManifest` has never heard of is
your call rather than the library's: a mixed application is free to declare initializers written
directly against `androidx.startup` beside these, and a test that reports those is useless in exactly
the applications that need one most. The set comparison above treats such a name as a failure, so
subtract them, or compare only the names the `StartupManifest` knows.

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

`sample`'s own `AndroidManifestParityTest` is exactly the test above, run against a real
`AndroidManifest.xml` and a real `StartupManifest` on every build. Copy it. Its negative control is
the whole point: delete a `<meta-data>` line from `sample/src/androidMain/AndroidManifest.xml` and
`./gradlew :sample:testAndroidHostTest` fails, naming the component that would have stopped running
on Android alone.

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
`initializeComponent` without deadlocking. The engine holds no coroutine of its own, because
`runBlocking` does not exist on Kotlin/JS or Kotlin/Wasm; a component whose own work suspends says
so with `CoroutineInitializer`, see **Initializing something that suspends** below.
`StartupPlan.waves` exposes the Kahn levels as data, and
`Startup.install(context, manifest, runner)` hands each level to a `WaveRunner` of your choosing —
see **Running a wave concurrently** below for what a task may not do.

`AppInitializer.initializeComponentOrNull(key)` is the read for a component whose key is an
`AnyInitializerKey` — the element type of `dependencies()`, what `initializerKey(initializer)`
returns, and the only key a host that discovered an initializer at run time can build — and for one
whose `create` returned null. `initializeComponent` takes neither: its key is
`InitializerKey<out Initializer<T>>` and its `T` is bound to `Any`.

`AppInitializer` is the same two members here that it is on Android. Until 1.1.0 it carried three
more off Android — `isInitialized(component)`, `initializationOrder()` and `manifest()` — and
**2.0.0 removes them**. On Android `AppInitializer` **is** `androidx.startup.AppInitializer`, which
exposes neither the order it created things in nor whether a given component exists, and keeps no
accessible state to derive either from, so `androidx.startup` had no counterpart to mirror and those
three were the only members of the API that ten targets had and the eleventh did not.

Record what you need from inside your own `create`, which is what `sample`'s `SampleReport` does:

```kotlin
class NetworkInitializer : Initializer<Network> {
    override fun create(context: StartupContext): Network {
        val logger = Startup.getInstance(context)
            .initializeComponent(initializerKey<LoggerInitializer>())
        return Network(logger).also { logger.ready("network") }
    }

    override fun dependencies(): List<AnyInitializerKey> = listOf(initializerKey<LoggerInitializer>())
}
```

That reads the same on all eleven targets, which the removed members never could. For the manifest,
hold on to the `StartupManifest` you passed to `Startup.install`: it is an ordinary value, and
`components`, `eagerComponents`, `isEager` and `in` all still answer from it. See **Upgrading from
1.x** below.

### From Swift and Objective-C

**Export the library from your framework first.** `implementation("io.github.kunal26das:startup")`
is enough for Kotlin and is not enough for Swift. A dependency module that a framework does not
export has its module name mangled into every class the header does carry, and the declarations that
appear in no exported signature are dropped from it altogether. Add both lines below, or the Swift
snippets in this section name types that do not exist:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        api("io.github.kunal26das:startup:3.0.0")
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            export("io.github.kunal26das:startup:3.0.0")
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
            export("io.github.kunal26das:startup:3.0.0")
        }
    }
}
```

`export` requires `api`. Left on `implementation`, the build fails with *dependencies exported in
the framework are not specified as API dependencies of a corresponding source set*.

You may already have it without writing the line. A framework with `transitiveExport = true` that
exports a module which declares `api("io.github.kunal26das:startup:3.0.0")` exports this library too,
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
func boot(_ application: UIApplication) throws {
    let manifest = StartupManifest.companion.invoke { builder in
        let lifecycle = ViewControllerLifecycleInitializer()
        builder.metaData(component: InitializerKeyKt.initializerKey(initializer: lifecycle)) { lifecycle }
    }
    try Startup.shared.install(context: DefaultContext.shared, manifest: manifest)
}
```

`install` is `try` from 3.0.0; see **Every entry point carries `@Throws`** below.

**The Swift name of `Context` is `StartupContext`.** A Kotlin `typealias` does not survive the
Objective-C export, so the class itself carries `@ObjCName("StartupContext")`. That keeps the Swift
name equal to the alias this README already tells Kotlin authors to prefer, and it keeps a bare
`Context` out of the framework's namespace — which matters, because `UIViewControllerRepresentable`
declares a `Context` of its own and a framework-level one shadows it in the iOS host file every
Compose Multiplatform app has, with a `does not conform to protocol` error that never mentions the
name.

**Name any initializer from Swift with `initializerKey(initializer:)`.** It is the only key overload
Swift can reach in practice — a `reified` type argument cannot cross the boundary, and although
`initializerKey(kClass:)` is exported, a `KotlinKClass` is not obtainable from Swift to pass it. It
works the same whether the initializer was written in Kotlin or in Swift:

```swift
final class HostInitializer: NSObject, Initializer {
    func create(context: StartupContext) -> Any? { ... }

    func dependencies() -> [InitializerKey<Initializer>] {
        [InitializerKeyKt.initializerKey(initializer: KoinInitializer())]
    }
}
```

`KoinInitializer` there is a Kotlin class, and naming it this way **constructs one**. Be clear-eyed
about that: `initializerKey(objCClass:)`, removed in 2.0.0, took the class object and constructed
nothing, so a Swift `dependencies()` that names three Kotlin components now runs three constructors
that the returned keys then throw away, once per call. It is free for an initializer that holds
nothing in its constructor and does its work in `create`, which is what this library asks of every
initializer anyway: AndroidX builds each one reflectively through
`getDeclaredConstructor().newInstance()`, at a moment the author does not choose. It is not free for
one whose constructor has a side effect, and such an initializer should not have one. If the cost is
real for you, hoist the keys into a `let` computed once rather than rebuilding them per
`dependencies()` call. See **Upgrading from 1.x** below.

**Every entry point carries `@Throws(StartupException::class)`.** Without it Kotlin/Native does not
propagate an exception to Swift as an `NSError`: it prints *Function doesn't have or inherit
@Throws annotation and thus exception isn't propagated* and terminates the process, so a diagnosable
launch failure arrived as an abort. `Startup.install`, `Startup.getInstance`,
`AppInitializer.initializeComponent`, `AppInitializer.initializeComponentOrNull`,
`AppInitializer.isEagerlyInitialized`, `StartupTask.invoke`, `StartupPlanner.plan` and
`StartupPlanner.validate` are all `try` in Swift now. `:startup:checkObjCExport` asserts the
`NSError` parameter on every one of them, so a dropped annotation fails the build rather than the
app.

**A Swift `WaveRunner` has one spelling that compiles**, because the task list crosses as an array
of Objective-C objects:

```swift
final class ConcurrentWaveRunner: NSObject, WaveRunner {
    func run(wave: [StartupTask]) {
        DispatchQueue.concurrentPerform(iterations: wave.count) { index in
            do { try wave[index].invoke() } catch { }
        }
    }
}
```

**A Swift runner cannot rethrow, and does not have to.** `run` is exported without an error
parameter, so a `throws` conformance does not compile — Swift reports *candidate throws, but
protocol does not allow it*. Catching and dropping is correct here: the task recorded the failure
before it reached you, and the engine re-raises it as a `StartupException` naming the component once
`run` returns. That is why the contract below says a runner must let a failure out *where the
language allows*.

`concurrentPerform` runs some iterations on the calling thread and some on workers, which is worth
knowing: an iteration that lands on the calling thread may resolve another component and one that
lands on a worker may not, so a runner written this way must not touch `AppInitializer` at all.

`:startup`'s `checkObjCExport` task links `Startup.framework` and asserts on the generated header,
and `:sample`'s `checkConsumerObjCExport` does the same for the two frameworks a *consumer* gets, so
neither the export shape nor the recipe above can regress unnoticed.

## Where the two runtimes differ

Everything in the API mapping below behaves the same on all eleven targets. Six things do not, and
the first four are cases where code written and tested on Android would misbehave elsewhere.

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
- **A factory that builds something else.** A factory has to produce exactly the class its key
  names. The check is `commonMain`, so it fires on every target where the graph is planned — but
  `Startup.install` on Android never calls a factory, so there it fires only under
  `StartupPlanner.validate(manifest)`. An Android app that never validates keeps running, on the
  class AndroidX reflected rather than the one the factory would have built.
- **A component that produces nothing.** Off Android `Initializer<T>` leaves `T` unbounded and the
  Objective-C export gives a Swift author `Any?` to return, so `create` may hand back null; the
  engine stores it, `initializeComponentOrNull` returns it, and `initializeComponent` names the
  component rather than failing a cast. On Android `androidx.startup.Initializer.create` is
  `@NonNull`, so such an initializer does not compile at all — an Android-compatible component with
  no product is an `Initializer<Unit>` that returns `Unit`. It is the same constraint that bounds
  `CoroutineInitializer<T : Any>`.
- **Which registry decides.** Off Android the `StartupManifest` is the whole registry. On Android
  it is the AndroidManifest, and the factories in the `StartupManifest` are never called. That is
  the one failure mode adopting this library adds. The AndroidManifest is the source of truth there
  and a parity test of your own is the answer to it; see **Keep the two Android registries in step**
  above.

### Running a wave concurrently

By default both runtimes create one component at a time on the calling thread. Everything in a
`StartupPlan` wave depends only on earlier waves, though, so a wave is safe to run all at once, and
`Startup.install(context, manifest, runner)` hands each one to a `WaveRunner` in turn:

```kotlin
Startup.install(context, manifest) { wave ->
    runBlocking { coroutineScope { wave.map { async(startupDispatcher) { it() } }.awaitAll() } }
}
```

**The dispatcher there is the whole point.** A task is an ordinary blocking call rather than a
suspending one, so `async { }` without a dispatcher inherits `runBlocking`'s single-threaded event
loop and runs the wave one task after another on the calling thread — which is exactly what
`Startup.install(context, manifest)` already does, at the cost of a runner that looks concurrent.

Give it a dispatcher of its own rather than reaching for `Dispatchers.Default`. A task blocks the
thread it runs on for as long as its component takes, so a wave of `CoroutineInitializer`s
dispatched onto the pool their own `createAsync` resumes on starves that pool and hangs the install;
see **Initializing something that suspends**. `Dispatchers.IO` is elastic on both the JVM and
Kotlin/Native and is the safe default here.

The library keeps the ordering, the cycle detection, the deduplication and the created components;
the concurrency is yours. `install` waits for each wave before planning the next, so `run` **must**
invoke every task exactly once, must let a task's failure out rather than swallowing it, and must
not return until all of them have finished. A second invocation of a task is refused at the call, by
`StartupTask.invoke` itself; the other two are checked once `run` returns. Either way the violation
is a `StartupException` naming the components it applies to rather than a component quietly filed as
null.

**A task is a `StartupTask`, and it names the component it will create.** `task.component` is the
key and `task.toString()` is the component's name, so a runner can route a wave rather than merely
run it — the one component that has to stay on the calling thread dispatched differently from the
rest — and can attribute a slow or failed wave to a component. It is also where a host hangs its own
tracing, because the engine has no hook of its own:

```kotlin
Startup.install(context, manifest) { wave ->
    runBlocking {
        coroutineScope {
            wave.map { task ->
                async(if (task.component == mainThreadOnly) Dispatchers.Main else Dispatchers.IO) {
                    trace(task.toString()) { task() }
                }
            }.awaitAll()
        }
    }
}
```

**A task may call `AppInitializer.initializeComponent` only from the thread that called `install`,
and only for what an earlier wave already created.** The lock is held across the whole install and it
is reentrant, so a runner that stays on the calling thread — which is every runner on Kotlin/JS and
Kotlin/Wasm — may read a component an earlier wave built. From any other thread that call can never
be served, and it now fails immediately with a `StartupException` saying so rather than waiting on a
lock the installing thread cannot release until `run` returns.

A component of the wave being run right now is refused too, on every thread. Nothing a wave creates
is written back until `run` returns, so a sibling is neither created nor creatable from inside one —
and it is refused by name rather than reported as a cycle, because two components share a wave
precisely when neither declares the other, so there is no cycle to draw. A component that asks for
*itself* inside a wave gets the same refusal, which says so; without a runner that one is still
reported as the cycle it is. Declaring the edge in
`dependencies()` is what makes the call safe from anywhere, because that is what puts the dependency
in a strictly earlier wave. That is what makes `sample`'s own manifest safe under a runner:
`NetworkInitializer` and `AnalyticsInitializer` resolve what they need imperatively *and* declare the
same edges, which is the AndroidX-documented pattern, so each of them only ever reads back a
component an earlier wave already built.

On Android the runner is ignored — `androidx.startup` creates each component itself, depth first
on the calling thread, and offers no seam to change it. For ordinary `Initializer`s a runner is
therefore a performance decision on the other ten targets and never a correctness one, so anything
that must run before something else still has to say so in `dependencies()`.

**For a `CoroutineInitializer` it is a correctness decision.** `create` blocks the thread it is
called on until `createAsync` finishes, and the runner is what picks that thread: on Android it is
always `InitializationProvider`'s, which is the main thread, while off Android a runner can move it
to a worker or leave it on whoever called `install`. Which thread that is decides whether the
component's own dispatching can make progress — see **Initializing something that suspends**.

You can also skip `AppInitializer` for the concurrent part entirely: plan with
`StartupPlanner.plan(manifest, roots, satisfied)`, read `plan.waves`, construct your own initializers
— you wrote the factories — call `create(context)` on a level in parallel, and hold the results
yourself. That is a fork in the road rather than a seam, and it is worth measuring first: an
initializer that hands its work to a background scheduler and returns immediately costs the same
either way.

### Initializing something that suspends

The graph's promise is that a dependency is *created* before the component that declares it. For a
component whose real work is a `suspend` call — which is nearly every mobile SDK — a plain
`Initializer` can only keep half of it: `create` has nowhere to await, so the idiomatic escape is to
launch the work and return, and the graph then orders the launches rather than the completions. A
dependency edge that does not wait is not a dependency edge.

`CoroutineInitializer` is where a component says its work suspends:

```kotlin
class FirebaseInitializer : CoroutineInitializer<Unit> {
    override suspend fun createAsync(context: StartupContext) = Firebase.start(context)
}
```

`create` is inherited and blocks the calling thread until `createAsync` returns, so anything
declaring this component in `dependencies()` starts after it has finished rather than after it has
begun. Four consequences follow, and every one of them is yours to accept:

- **It blocks.** On Android that thread is whichever one `InitializationProvider` runs on, which is
  the main thread; elsewhere it is whoever called `Startup.install`, unless a `WaveRunner` moved the
  wave. A component that must not block startup should still launch and return, and say so by
  staying an ordinary `Initializer`.
- **It must not need the thread it is blocking.** A `createAsync` that dispatches to the main
  dispatcher, from the main thread, deadlocks. That is the ordinary `runBlocking` hazard and this
  type does not change it.
- **It must not be dispatched onto the pool it will resume on.** This is the same hazard one step
  out, and it is the one a runner walks into. Each blocked `create` holds a worker of the pool the
  runner sent it to; if `createAsync` then needs a worker of that same pool to resume, a wave with
  as many such components as the pool has parallelism starves. Measured: twenty components whose
  `createAsync` does `withContext(Dispatchers.Default)`, run by a `Dispatchers.Default` runner on an
  18-core machine, entered 18 bodies, resumed none, and never returned. Three of the same components
  finish; so do twenty that only `delay`. Give the runner a dispatcher the components do not use.
- **The thread guard does not reach past `createAsync`.** A wave task that calls
  `AppInitializer.initializeComponent` is refused at once, but only on the thread the task body runs
  on. `createAsync` switching dispatchers moves the call to a thread the guard does not know about,
  where it waits for a lock the install cannot release — with or without a runner. Declare the edge
  in `dependencies()`; resolving a component from inside `createAsync` is not supported.

**`CoroutineInitializer` is a Kotlin-side type.** Kotlin interface default bodies do not become
Objective-C protocol defaults, so a Swift class conforming to it inherits nothing and must write the
blocking `create(context:)` itself. A Swift initializer that has to await should implement
`Initializer` directly and do its own waiting.

`CoroutineInitializer<T : Any>` bounds `T` to `Any`, because `androidx.startup.Initializer` declares
`create` `@NonNull` and this interface implements it on Android like any other.

Kotlin/JS and Kotlin/Wasm have one thread and no way to park it, so there is nothing for `create` to
block and it throws a `StartupException` that says exactly that. Those two targets run every
ordinary `Initializer` as before; it is only the blocking bridge that has nowhere to stand.

## API mapping

| `androidx.startup`                                   | `io.github.kunal26das.startup`                    | On Android                     |
| ---------------------------------------------------- | -------------------------------------------------- | ------------------------------ |
| `androidx.startup.Initializer<T>`                     | `Initializer<T>`                                    | `typealias`                    |
| `android.content.Context`                             | `Context`, `StartupContext`                         | `typealias`                    |
| `Class<out Initializer<*>>`                           | `InitializerKey<T>`, `AnyInitializerKey`            | `typealias` to `java.lang.Class`|
| `MyInitializer::class.java`                           | `initializerKey<MyInitializer>()`                   | inlines to a class constant    |
| `instance.getClass()`                                 | `initializerKey(instance)`                          | `::class.java` on the instance |
| `Class.forName(name)`                                 | `initializerKey(kClass)`                            | `::class.java` on the KClass   |
| `androidx.startup.AppInitializer`                     | `AppInitializer`                                    | `typealias`                    |
| `AppInitializer.getInstance(context)`                 | `Startup.getInstance(context)`                      | delegates to the static        |
| `AppInitializer.initializeComponent(component)`       | `AppInitializer.initializeComponent(component)`     | same method                    |
| `AppInitializer.isEagerlyInitialized(component)`      | `AppInitializer.isEagerlyInitialized(component)`    | same method                    |
| `<meta-data android:value="androidx.startup" />`      | `StartupManifest { metaData<T> { T() } }`           | still the manifest             |
| no equivalent                                         | `StartupManifest { metaData(key) { it } }`          | still the manifest             |
| `tools:node="remove"`                                 | `StartupManifest { remove<T>() }`                   | still the manifest             |
| `InitializationProvider.onCreate()`                   | `Startup.install(context, manifest)`                | eagerly initializes            |
| no equivalent                                         | `Startup.install(context, manifest, runner)`        | runner ignored                 |
| no equivalent                                         | `WaveRunner`, `StartupTask`                         | never called                   |
| no equivalent                                         | `AppInitializer.initializeComponentOrNull(key)`     | delegates; null not expressible|
| no equivalent                                         | `CoroutineInitializer<T : Any>`                     | blocks the provider's thread   |
| `androidx.startup.StartupException`                   | `StartupException`                                  | **not** a `typealias`          |

`StartupException` is deliberately our own type. AndroidX annotates its exception
`@RestrictTo(LIBRARY)`, so aliasing it would make every consumer's `catch` clause fail lint's
error-severity `RestrictedApi` check. Failures raised by AndroidX itself still arrive as AndroidX's
own type on Android, exactly as they do in an app that uses `androidx.startup` directly.

There is no `expect companion object` on `AppInitializer` because a Java static has no member for
one to match, which is why `Startup` exists.

Until 1.1.0 this table carried eight more rows, each of them a declaration `androidx.startup`
genuinely had no counterpart for, and each of them a platform asymmetry: four existed for Android's
manifest and did nothing useful off it, three existed on the other ten targets and not on Android,
one existed on the Apple targets alone. **2.0.0 removes all eight**, so the table now has one shape
on all eleven targets. See **Upgrading from 1.x** below.

One *no equivalent* row survives, `StartupManifest { metaData(key) { it } }`, and it was never in
that group. It is the registration `androidx.startup` performs by name in XML, expressed as a key,
which makes it *closer* to `androidx.startup` than the `reified` overload beside it, and it is the
only way Swift or a plugin host can register an initializer the compiler cannot name.

## Upgrading from 2.0.0

**2.1.0 tightens one rule.** A factory must now build exactly the class its key names;
2.0.0 accepted a subclass and filed it under the supertype. That never worked on Android,
where AndroidX ignores the factory and reflects the key, so `metaData<Base> { Derived() }`
started `Base` on Android and `Derived` everywhere else from one manifest. Register the
subclass under its own key. Because the covariance of Kotlin function types lets the
`reified` overloads express the mismatch too, this affects `metaData<T> { ... }` and
`lazyInitializer<T> { ... }` as well as the key-taking overloads.

The rejection lives in `commonMain` and fires wherever the graph is planned, Android
included: `Startup.install` never calls a factory there, so it does not surface it, but
`StartupPlanner.validate(manifest)` does, on all eleven targets.

2.1.0 also **adds** `Startup.install(context, manifest, runner)` and the `WaveRunner` it takes,
which run each wave of the plan however the host wants rather than on the calling thread; see
**Running a wave concurrently**. It is ignored on Android, and nothing else published in 2.0.0
changed.

## Upgrading from 2.x

**3.0.0 changes what a `WaveRunner` receives.** `run(wave: List<() -> Unit>)` is now
`run(wave: List<StartupTask>)`. A task is still invoked the same way — `it()` — so a runner written
as `wave.map { async { it() } }` needs no edit; one that named the type of its parameter does. What
the change buys is that a task now names its component, which is what makes routing, tracing and an
attributable failure possible at all.

**3.0.0 enforces the `WaveRunner` contract it always documented.** A runner that runs a task twice
is refused at the second call; one that skips a task or catches a task's failure and returns anyway
is a `StartupException` naming the components once `run` returns, instead of a component filed as
null that fails much later somewhere else. A wave that
fails now also keeps the components that succeeded beside the failure, which is what the sequential
path always did.

**3.0.0 fails fast instead of waiting for a lock that cannot be released.** A wave task that calls
`AppInitializer.initializeComponent` from a thread other than the installing one now throws
immediately. It used to park on the JVM and spin without yielding on Kotlin/Native, where it cost a
core for as long as the install ran and never ended. A task on the installing thread still works for
anything an earlier wave created, as it always did — the flat prohibition in the 2.x README was
wrong about that half — but not for a component of the wave in flight, which is refused by name. A
thread that is not running a task is unaffected: its wait for the install to finish really does end,
so it still waits.

`StartupTask`'s constructor is public, so a runner can still be exercised against a wave the test
built rather than only through `Startup.install` with a real manifest.

**3.0.0 adds `AppInitializer.initializeComponentOrNull(key)`**, the read for an `AnyInitializerKey`
and for a component whose `create` returned null. Both were previously unreadable: the key type did
not fit `initializeComponent`, and a null product failed its cast with a bare
`NullPointerException` raised inside the library. That failure is now a `StartupException` naming
the component.

**3.0.0 adds `CoroutineInitializer`**, and with it a dependency on `kotlinx-coroutines-core` for
the Android, JVM and native artifacts. See **Initializing something that suspends**.

**3.0.0 annotates the public API with `@Throws(StartupException::class)`**, which is source-breaking
for Swift: a call that could fail now needs `try`. Kotlin callers are unaffected.

## Upgrading from 1.x

**2.0.0 removes the eight declarations 1.1.0 deprecated, and nothing else.** Each carried
`DeprecationLevel.WARNING` in 1.1.0, so a consumer that took the warnings has nothing left to do;
one upgrading straight from 1.0.0 gets errors instead, and the table below is the whole list.

The reason is the same for all eight. **None of them had a counterpart in `androidx.startup`**, and
mirroring `androidx.startup` is this library's whole contract — for Android that contract is literal,
because `Initializer`, `AppInitializer`, `Context` and the key are `typealias`es of the AndroidX
types. They were also the only platform asymmetry in the API mapping table: three existed on the ten
non-Android targets and not on Android, four existed for Android's manifest and did nothing useful
off it, and one existed on the Apple targets alone.

| removed in 2.0.0                                 | replacement                                                  |
| ------------------------------------------------ | ------------------------------------------------------------ |
| `AppInitializer.isInitialized(component)`         | record it from inside your own `Initializer.create`           |
| `AppInitializer.initializationOrder()`            | record it from inside your own `Initializer.create`           |
| `AppInitializer.manifest()`                       | keep the `StartupManifest` you passed to `Startup.install`    |
| `manifest.androidManifestMetadata()`              | write the `<meta-data>` entries by hand                       |
| `manifest.androidManifestDrift(declared)`         | keep a parity test of your own                                |
| `manifest.androidManifestDrift(context)`          | keep a parity test of your own                                |
| `manifest.verifyAndroidManifest(context)`         | keep a parity test of your own                                |
| `initializerKey(objCClass)`                       | `initializerKey(initializer)`, from an instance               |

**The first three.** `sample`'s `SampleReport` is the worked example. The initialization order it
prints is a list each component appends to from inside its own `create`, which is exactly why the
report reads the same on all eleven targets instead of on ten. `isInitialized` has no direct
replacement and does not need one: asking for a component that already exists returns it without
running `create` again, so there is nothing to guard. `manifest()` handed back the value you passed
to `Startup.install` — keep it in a `val`, and `components`, `eagerComponents`, `isEager` and `in`
all still answer from it.

**The Android four.** The problem they addressed has not gone away. The `StartupManifest` and the
AndroidManifest really are two registries and only Android reads the second, so a component in one
and missing from the other still misbehaves on exactly one platform. What changes is who owns the
answer: from 2.0.0 the AndroidManifest is the source of truth on Android, its `<meta-data>` entries
are written by hand as a plain `androidx.startup` application writes them, and a consumer that wants
the two held in step keeps its own test. **Keep the two Android registries in step** above has one,
in a dozen lines, against the API that is staying, and `sample`'s `AndroidManifestParityTest` runs
exactly it on every build. The generated `<meta-data>` block is also gone, so paste the lines once
from that test's failure message or write them out; they are three lines for three eager components
and they change about as often as the components do.

**The Apple one.** `initializerKey(objCClass:)` was the only Apple-only declaration in the API.
`initializerKey(initializer:)` needs an instance, so naming a component from Swift now constructs a
throwaway one and runs its constructor — a cost the class-object overload did not have, and the one
genuine regression in this release. It bites only an initializer whose constructor does something,
and none should: AndroidX builds every initializer reflectively through
`getDeclaredConstructor().newInstance()`, at a moment the author does not choose, so the work belongs
in `create`.

**Nothing else changes.** Every other declaration published in 1.1.0 stays exactly as it was, the
key-taking registration overloads included — `metaData(component, factory)`,
`lazyInitializer(component, factory)`, `remove(component)`, `initializerKey(initializer)` and
`initializerKey(kClass)`. They are not deprecated and are not going anywhere: `androidx.startup`'s
own registration is by name in XML, so a key-taking overload is *closer* to it than a `reified` one,
and without them Swift cannot register a host-supplied initializer at all.

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
most once per run, and only off Android. `StartupPlanner.plan` and
`StartupPlanner.validate` also call it, on every target including Android, because reading
`dependencies()` needs an instance.

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

A component a `remove<T>()` entry hides says so, rather than claiming nobody registered it, so the
remedy on offer is not to re-register the entry the application took out:

```
Cannot initialize Alpha. A remove() entry hides it, and Beta still declares it as a dependency.
Drop that dependencies() entry, or stop removing the component. Startup.install on Android reads
dependencies() reflectively without consulting a StartupManifest, so it creates it there anyway.
```

A factory registered under a key it does not build names both classes and the remedy:

```
Cannot initialize Alpha. Its factory produced a Beta instead. A factory has to build the class its
key names: the product would be filed under the registered key here, while Startup.install on
Android ignores the factory and reflects the key, so one manifest would build two different
graphs. Register it under its own key.
```

Component names are fully qualified on Android, where the key is a `java.lang.Class`, and simple
elsewhere, because `KClass.qualifiedName` does not compile on Kotlin/JS.

`StartupPlanner.validate(manifest)` walks the whole graph without calling `Initializer.create`,
which makes a cycle or a missing registration a test failure rather than a launch failure. It does
construct every registered initializer, because reading `dependencies()` needs one.

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
`iosMain`, which hands control to `UIApplicationMain`; the other seven console targets share the
`main` in `consoleMain`, which prints and exits.

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
linking their test binaries is the local proof. CI runs the first two for real on `ubuntu-latest`
and `windows-latest`; `macosX64Test` and `iosX64Test` run nowhere.

## License

Apache-2.0. See [LICENSE](LICENSE).

# CLAUDE.md

A Kotlin Multiplatform port of AndroidX App Startup, published as `io.github.kunal26das:startup`.
On Android the public API is a set of `typealias`es of `androidx.startup`, with zero wrapper types.
On the other ten targets it is a hand-written runtime whose order comes from Kahn's algorithm.

## House style

- **KDoc on every public declaration.**
- **No inline `//` comments, no block comments, no commented-out code, anywhere.** Not in Kotlin,
  not in Gradle scripts, not in YAML, not in config files. KDoc is the only comment form allowed.
- **One top-level declaration per Kotlin file, and the file name is the declaration name.** The only
  exception is `InitializerKey.kt`, where the three `initializerKey` overloads live beside the class
  they build: all of them must be top-level, because a Java class cannot satisfy an
  `expect companion object`. They also have to stay in that one file: a non-`expect` function in the
  `commonMain` copy would generate a second `InitializerKeyKt` JVM facade and collide with the
  `androidMain` and `desktopMain` ones, which is why the instance overload is `expect`/`actual`
  despite having one possible body.
- No AI attribution in commits, pull requests or files.

## Layout

```
startup/                  the published library
  src/commonMain          the public API and StartupPlanner
  src/androidMain         typealiases onto androidx.startup, plus the AndroidManifest parity check
  src/nonAndroidMain      the runtime for the other ten targets
  src/appleMain           initializerKey(objCClass), so Swift can name a class without one
  src/desktopMain         the JVM StartupLock
  src/nativeMain          the Kotlin/Native StartupLock and its thread token
  src/jsMain              the single-threaded StartupLock
  src/wasmJsMain          the single-threaded StartupLock
  src/commonTest          planner and manifest tests, run on all eleven targets
  src/nonAndroidTest      engine tests, run on the ten non-Android targets
  src/desktopTest         the concurrency test, which needs real threads
  src/appleTest           the Objective-C class overload's rejection path
  src/androidHostTest     the Android bytecode contract, asserted reflectively
sample/                   not published; proves a consumer writes an initializer once
  src/commonMain          shared initializers, the two expect initializers, SampleReport
  src/androidMain         the Android actual, plus the AndroidManifest <meta-data> entries
  src/nonAndroidMain      the non-Android actual, the expect Platform, and SampleLauncher
  src/consoleMain         the main() that prints and exits, for the seven console targets
  src/iosMain             the main() that calls UIApplicationMain, plus the delegate and controller
  src/{apple,desktop,js,wasmJs,linux,mingw}Main   one Platform actual each
  src/{js,wasmJs}Main/resources                   the browser page for each web target
  src/androidHostTest     manifest parity and the AndroidX reflection contract
  src/nonAndroidTest      bootstrap, platform-SDK and report tests, run on all ten targets
  iosApp/Info.plist       the bundle the iOS app is assembled around
androidApp/               not published; the launchable Android application
  src/main                one Activity, plus the manifest that merges the sample's provider block
```

The sample is runnable, not merely compilable. `binaries.executable()` on every Kotlin/Native target
and on both web targets, and `mainRun { mainClass }` on `desktop`, give ten builds an entry point.
Seven of them share the `fun main()` in `consoleMain`, which prints the report and exits. The three
iOS targets use the `fun main()` in `iosMain` instead, because an app there is a UIKit process that
never returns; `consoleMain` exists purely so that the two cannot collide, and it is
`nonAndroidMain` minus `iosMain`. Only `NativeBuildType.DEBUG` executables are declared, because a
release link per target would double what `./gradlew build` has to do for a sample nobody ships.

`:sample:iosSimulatorApp` is what makes iOS an app rather than a binary: it lays `SampleApp.app` out
around `sample/iosApp/Info.plist`, copies the linked `.kexe` in as the bundle executable, and ad-hoc
signs it, so `xcrun simctl install` and `xcrun simctl launch` work on it like any other app. It is
deliberately not wired into `assemble`, which would make `./gradlew build` host specific.

`androidApp` exists because `sample` has to stay a library: its `AndroidManifest.xml` and
`AndroidManifestParityTest` are the proof that the shared initializers are declarable the AndroidX
way, and a `com.android.application` module cannot contribute that. `androidApp` applies
`com.android.application` alone; AGP 9 refuses `org.jetbrains.kotlin.android` beside it, because
Kotlin support is built in. Nothing in it calls `Startup.install`: the components arrive through the
merged manifest, and `AndroidSampleStartup.isEager` reports what `InitializationProvider` actually
discovered.

`SampleReport` lives in `commonMain` and is the whole of what every entry point prints, Android
included. The initialization order it shows is `Logger.initialized`, a list each component appends
to from inside its own `create` by calling `Logger.ready`. Neither runtime reports an order of its
own — AndroidX exposes none, and the port's public API is fixed — so the sample records one as it is
built, rather than recovering it afterwards by matching log lines, which would silently absorb any
unrelated message that happened to end in ` ready`. Any new sample initializer that has the logger among its
dependencies has to call `logger.ready(...)` or it will be missing from that section;
`RuntimeInfoInitializer` has no dependencies and deliberately appears elsewhere in the report.

The sample carries all three initializer shapes on purpose. `AnalyticsInitializer` is written once in
`commonMain` because nothing it does is platform specific. `CrashReportingInitializer` is `expect`
because starting a platform SDK is not the same call on every target, and it is the shape a real
Firebase or AppsFlyer integration takes. It extends `Initializer` rather than `BaseInitializer`
because it genuinely declares a dependency, so its `expect` class must redeclare `create` and
`dependencies`: `Initializer` carries its members, so an `expect` that omits them fails with "has no
corresponding expected declaration" on the `actual` side. `RuntimeInfoInitializer` is the third
shape and the one README.md recommends by default: `expect` over `BaseInitializer`, which carries a
concrete `dependencies`, so only `create` is redeclared. It exists in `commonMain` rather than in a
test source set because `commonMain` is metadata-compiled and a test source set is not; see
**Verified shapes**. It declares no dependencies, so it has no `Logger` to log to and reports itself
in its own section of `SampleReport` instead of joining the initialization order.

Every `actual` initializer must be a class with a public no-argument constructor, never an `object`.
AndroidX reflects with `getDeclaredConstructor().newInstance()` and ignores the manifest factory, so
an `object` compiles on all eleven targets and throws only on Android.

`AndroidManifestParityTest` holds `sample/src/androidMain/AndroidManifest.xml` and
`SampleStartup.manifest` in step, through `StartupManifest.androidManifestDrift(Set<String>)`, so a
failure names the components that drifted. The manifest is declared as an `inputs.file` on the test
task; without that the task stays up to date when the XML changes and the assertion silently stops
running.

`MainActivity` prints `AndroidSampleStartup.androidManifestDrift(context)` above the report, which
is the only exercise of the `PackageManager` half of that check: `androidManifestDrift(Context)`
reads the merged manifest of the running application through
`getProviderInfo(..., GET_META_DATA)` and cannot be unit tested without an instrumented context.
Verify it by launching `androidApp` and reading `androidmanifest drift: 0` back from Logcat.

Sample tests share one process-wide `Startup`, so they share one `Logger`. Assert relative order or
containment, never the whole message list.

`sample` declares `nonAndroidMain` and `nonAndroidTest` of its own only because it ships a launcher
and a test that use `DefaultContext`, which is a non-Android type. A consumer that stays in
`commonMain` needs no source-set surgery at all: one `commonMain.dependencies` line is the whole
integration.

## Source-set hierarchy

`applyDefaultHierarchyTemplate()` plus four `dependsOn` edges, which is what covers all ten
non-Android targets. `native` is the template's own group, so `iosArm64`, `iosSimulatorArm64`,
`iosX64`, `macosArm64`, `macosX64`, `linuxX64` and `mingwX64` all reach `nonAndroidMain` through it.

```
commonMain
├── androidMain
└── nonAndroidMain
    ├── desktopMain
    ├── jsMain
    ├── wasmJsMain
    └── nativeMain
        └── appleMain
```

`sample` adds one more edge on top of that, `consoleMain`, which `desktopMain`, `jsMain`,
`wasmJsMain`, `macosMain`, `linuxMain` and `mingwMain` depend on and `iosMain` does not. A source
set may depend on two parents, so `macosMain` reaching both `appleMain` and `consoleMain` is fine.
That is what lets `iosMain` declare its own `main` without redeclaring the console one.

Declare the edges with `getByName` and `create`, never `by getting` and `by creating`, which are
deprecated on Gradle 9.7.1. Missing even one edge produces
`Expected <X> has no actual declaration in module <commonMain>`.

## Verified shapes, and the near-misses that fail

Every construct below was compile-verified on all eleven targets. Each one has a neighbour that
looks equivalent and does not compile; those are listed so they are not re-derived.

- `expect abstract class Context()` — must be `abstract` and must carry the explicit `()`.
  `expect class Context` gives *the modalities are different ('final' vs 'abstract')*,
  `expect interface Context` gives *the class kinds are different*, and an `actual value class`
  gives *the modifiers are different* plus an unresolved `@JvmInline` off the JVM.
- `typealias StartupContext = Context` — a plain, non-`expect` alias, so an Android consumer can
  import it beside `android.content.Context` without
  *Conflicting import: imported name 'Context' is ambiguous*.
- `expect class InitializerKey<T : Any>` — the key must keep `java.lang.Class`'s exact arity and
  bound. A non-generic key gives both *Right-hand side of actual type alias cannot contain use-site
  variance or star projections* and *the number of type parameters is different*. Off Android,
  `actual typealias InitializerKey<T> = kotlin.reflect.KClass<T>` gives *the class kinds are
  different*, because `KClass` is an interface and `java.lang.Class` is a class, so the non-Android
  actual has to be a real class wrapping a `KClass`.
- `typealias AnyInitializerKey = InitializerKey<out Initializer<*>>` — the projection lives at the
  use site, in a plain alias, for the same reason.
- `expect interface Initializer<T>` — neither member may have a default body. A body on the
  `expect` member gives *Expected declaration cannot have a body*; a body on only the non-Android
  `actual` gives *the modalities are different ('abstract' vs 'open')*. `BaseInitializer` exists to
  supply the default that the pair forbids: a class overriding an abstract interface member is
  unconstrained.
- `actual typealias AppInitializer = androidx.startup.AppInitializer` — this works, but only while
  the component parameter is spelled through `InitializerKey`. Spelling it `Class<...>` or
  `KClass<...>` directly is what makes it fail. An `expect companion object` is genuinely
  impossible, because a Java static has no corresponding member; `Startup` exists for that reason.
- `@PublishedApi internal constructor` on the non-Android `InitializerKey` — required. A plain
  `internal` constructor called from the public `inline fun initializerKey()` is a hard error:
  *public-API inline function cannot access non-public-API*.
- `KClass.simpleName`, never `KClass.qualifiedName` — reading `qualifiedName` is a hard compile
  error on Kotlin/JS. Keeping the name on the key's own `toString()` is what saves a per-platform
  source set.
- Do **not** alias `androidx.startup.StartupException` or `androidx.startup.StartupLogger`. Both
  carry `@RestrictTo(RestrictTo.Scope.LIBRARY)` in the shipped 1.2.0 bytecode, so aliasing them
  makes every consumer's `catch` trip lint's error-severity `RestrictedApi` and fail
  `lintVitalRelease`. A `@file:Suppress` here does not protect a consumer.
- `androidMain.dependencies { api(libs.androidx.startup) }` — `api`, never `implementation`. With
  `implementation` the published POM emits `<scope>runtime</scope>` and a downstream Android
  consumer cannot compile `class X : Initializer<Y>`.
- `minSdk = 21` and `aarMetadata { minCompileSdk = 34 }`, which are exactly the floors
  `androidx.startup:startup-runtime:1.2.0` publishes. Neither may rise. The Android half of this
  library touches `ComponentName.getClassName`, `Context.getPackageManager`, `Context.getPackageName`,
  `PackageManager.getProviderInfo`, `ProviderInfo.metaData`, `Bundle.getString` and `Bundle.keySet`,
  every one of them API 1, so a higher floor buys nothing and costs a consumer everything: a
  `minSdk` above theirs fails `processDebugMainManifest` outright, and `minCompileSdk` is worse
  still, because `checkAarMetadata` is unconditional and has no `tools:` escape hatch — a library
  that publishes the newest SDK in existence forces every consumer to move `compileSdk` to adopt it.
  `compileSdk` stays at 37; it is the level this repository builds against and it does not reach the
  published metadata now that `aarMetadata` pins it. `:startup:checkAndroidFloors` unzips
  `build/outputs/aar/startup.aar` and fails on either value. Both floors were wrong before
  1.0.0 and neither was visible in this repository's own build, because `sample` and `androidApp`
  were written against the same numbers.
- `android { }` inside `kotlin { }`, not the AGP-9.4.0-deprecated `androidLibrary { }`.
- `expect class X() : BaseInitializer<T> { override fun create(context: Context): T }`, with an
  `actual` whose override is spelled `actual override fun create` — the portable platform-specific
  initializer shape, and the one README.md recommends by default. Redeclaring `create` makes it an
  expected member, which is why the `actual` needs the `actual` modifier; omit it and the compiler
  reports *Declaration must be marked with 'actual'*. `expect class X() : Initializer<T>` has to
  redeclare both members and is only needed when the initializer really does declare dependencies.
- `expect class X() : BaseInitializer<T>` with **no body** and a plain `override` on the `actual` is
  legal in a **platform** compilation and rejected by a **metadata** compilation, which is why it
  may not be recommended and may not be written in `commonMain` anywhere in this repository:

  ```
  e: X.kt:5:8 Class 'X' is not abstract and does not implement abstract member:
  expect fun create(context: Context): T
  ```

  Reproduced here by putting that shape in `sample/src/commonMain`:
  `:sample:compileKotlinDesktop` and `:sample:compileAndroidMain` succeed and
  `:sample:compileCommonMainKotlinMetadata` fails. It is a general Kotlin rule about an `expect
  class` inheriting an unimplemented abstract member — `abstract class B { abstract fun f(): String }`
  plus `expect class C() : B` fails the same way — so `BaseInitializer` cannot be reshaped around it,
  and an `abstract override fun create` on `BaseInitializer` only changes *abstract member* to
  *abstract base class member*. The blast radius is every module with a shared `commonMain`:
  published libraries, KSP in `commonMain`, cinterop commonization.
- **Where the two shapes are pinned, and why the pin moved.** `MemberlessInitializer` and
  `PlatformInitializer` in `startup/src/commonTest`, with actuals in `nonAndroidTest` and
  `androidHostTest`, cover the eleven platform compilations, and `AndroidInitializerContractTest`
  asserts the bytecode of both is unchanged: public no-argument constructor,
  `implements androidx.startup.Initializer`, AndroidX's own `dependencies()` signature. A test source
  set gets **no** metadata compilation — `ls startup/build/classes/kotlin/metadata` lists
  `appleMain`, `commonMain`, `nativeMain` and `nonAndroidMain` and nothing ending in `Test` — so
  those two files cannot say anything about the shape's portability, and for two rounds this file
  and README.md claimed a portability they never checked. `sample`'s `RuntimeInfoInitializer` is the
  pin that can: it is the redeclaring shape in a `commonMain` that `./gradlew build` really does
  metadata-compile. Delete its `override fun create` line and the build goes red. Do not remove it,
  and do not add a memberless `expect` to any `commonMain` here.
- `@HiddenFromObjC` on every `inline reified` public function, from `kotlin.native` under
  `@file:OptIn(ExperimentalObjCRefinement::class)`. It resolves in `commonMain` because the
  annotation is `@OptionalExpectation` in the common stdlib, and it is not optional: Kotlin/Native
  exports the non-inline body of a public inline function with the reified parameter collapsed to
  its upper bound, so without it `metaData<T>`, `lazyInitializer<T>`, `remove<T>()` and
  `initializerKey<T>()` reach Swift as four methods that compile, run, and name the same component
  at every call site. Annotate the `actual`, not the `expect`, where the declaration is a pair.
  Every such function needs a non-reified overload taking an `AnyInitializerKey`, which is both the
  Swift entry point and the only way to register an initializer discovered at run time.
- `@ObjCName("StartupContext")` on the non-Android `Context` actual, from `kotlin.native` under
  `@file:OptIn(ExperimentalObjCName::class)`. A `typealias` does not survive the Objective-C export,
  so `StartupContext` reached Swift as the bare name `Context` — the one name this library tells
  Kotlin authors to avoid, and one that shadows `UIViewControllerRepresentable.Context` in the iOS
  host file every Compose Multiplatform app ships, with a `does not conform to protocol` error that
  never mentions it. Annotate the `actual`, not the `expect`. The annotation resolves in
  `nonAndroidMain`, which compiles for JS, Wasm and the JVM too, for the same reason
  `@HiddenFromObjC` does.
- `fun initializerKey(objCClass: ObjCClass)` in `appleMain`, over
  `kotlinx.cinterop.getOriginalKotlinClass`, under `@file:OptIn(BetaInteropApi::class,
  ExperimentalForeignApi::class)`. It exports as `initializerKey(objCClass:)` taking a `Class`, and
  it is the only way Swift can name a *Kotlin* initializer: `initializerKey(initializer:)` needs an
  instance, so naming a component with a side-effecting constructor used to start it. A Kotlin test
  binary exports nothing to Objective-C, so `getOriginalKotlinClass` can never resolve a Kotlin
  class there and only the rejection path is testable from Kotlin; the happy path is pinned in the
  header by `checkObjCExport` and was run once by hand, linking a Swift executable against
  `sample`'s exported framework and spawning it on the simulator.
- That file **must be named `InitializerKey.kt`**, matching the `commonMain` and `nonAndroidMain`
  files it joins. Kotlin/Native derives the Objective-C facade class from the file name, so naming
  it `ObjCInitializerKey.kt` puts the function in `ObjCInitializerKeyKt` and Swift cannot reach it
  through `InitializerKeyKt` at all — `swiftc` reports *no exact matches in call to class method
  'initializerKey'* and lists only the two overloads from the other facade. Same-named files across
  source sets are the established pattern here and they merge into one facade, which is what
  `Context.kt`, `AppInitializer.kt` and `InitializerKey.kt` already rely on.
- `export(project(":startup"))` on a consumer's framework is not optional, and neither README.md nor
  this file may imply otherwise, though it may already be there transitively: a framework with
  `transitiveExport = true` that exports a module declaring `api(...)` on this library exports this
  library too, which is what Wish's convention plugin produced and why it needed no new line. Kotlin/Native mangles a non-exported dependency module's name into
  every class it emits and drops the declarations that appear in no exported signature, so a
  consumer that only depends on `:startup` gets `StartupInitializer`, `StartupInitializerKey`,
  `StartupStartupContext`, and no `InitializerKeyKt` or `DefaultContext` at all. `:startup`'s own
  `checkObjCExport` cannot see this, because there `startup` is the framework's own module;
  `:sample:checkConsumerObjCExport` links one framework each way and greps both headers, which is
  the only configuration in this repository that matches what an application actually gets.
- `compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }` is required for the
  `expect`/`actual` classes above.
- No coroutines and no `runBlocking`: neither exists on Kotlin/JS or Kotlin/Wasm, and both are
  first-class targets here. Execution is sequential on the calling thread. `StartupPlan.waves`
  exposes the Kahn levels as **diagnostic data**, with `waves.flatten() == order` as its whole
  contract, and it is not a scheduling hook. Do not add a `runWave` parameter to `install`: every
  engine entry point is serialized behind one reentrant `StartupLock`, so a `create` running on a
  worker thread that reads a dependency back through `initializeComponent` — the AndroidX-documented
  pattern, used by `sample`'s own `NetworkInitializer`, `AnalyticsInitializer` and
  `CrashReportingInitializer` — blocks on a lock the calling thread still holds, and releasing the
  lock for the duration of a wave lets a second `install` interleave, which is what it exists to
  stop. A consumer who wants concurrency plans with `StartupPlanner`, reads `waves`, and runs its
  own initializers outside `AppInitializer`. Wish reported the migration costing it the concurrent
  startup its hand-rolled runtime had; that cost is real, it is documented in README.md's **Startup
  is sequential, and the waves will not change that**, and it is the price of the JS and Wasm
  targets.
- `internal expect class StartupLock` in `nonAndroidMain`, with four actuals: `ReentrantLock` on the
  JVM, a reentrant spin lock over `kotlin.concurrent.AtomicReference` plus a `@ThreadLocal` token on
  Native, and a direct call on JS and Wasm, which are single-threaded. AndroidX serializes every
  initialization inside `synchronized (sLock)`, so shared code cannot be unsynchronized off Android:
  eight threads asking for two components created them five to eight times without it, and
  intermittently reported a cycle on a graph with no edges.
- No `atomicfu` and no third-party dependency of any kind off Android.
- `jvmToolchain(21)` pins the compiler, and `jvmTarget` is `JVM_11` on both the `android` and the
  `desktop` target. Every registration function is `inline`, and Kotlin refuses to inline bytecode
  built for a newer JVM target than the caller's, so publishing Java 21 bytecode made the library
  uncompilable for a stock AGP consumer, whose default is 11. Verify a change here by publishing to
  `mavenLocal` and compiling a consumer at `jvmTarget` 11, not by building this project.
- No `buildSrc`.

## The iOS app

`sample/src/iosMain` is the only UIKit code in the repository, and every shape in it was found by
failing first.

- `@OverrideInit` is `kotlinx.cinterop.ObjCObjectBase.OverrideInit`, a nested annotation.
  `import kotlinx.cinterop.OverrideInit` is an unresolved reference. Leaving the annotation off
  compiles, links and installs, and then dies at launch inside `UIApplicationMain` with
  `init is not implemented in <mangled class name>`.
- The delegate needs `companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta`. That
  companion is what `NSStringFromClass` names, and it is how UIKit finds the class at all.
- A companion of an Objective-C subclass may hold no fields: a `const val` in one gives *Fields are
  not supported for Companion of subclass of ObjC type*. Use a local `val`.
- Objective-C properties declared in a category arrive as Kotlin extensions and have to be imported
  by name, `import platform.UIKit.systemBackgroundColor` and `labelColor` among them. Without the
  import it is an unresolved reference on a type that obviously has the property.
- Some of those are read-only with a separate setter function, and which ones is not guessable:
  `editable = false` gives *'val' cannot be reassigned* and `setEditable(false)` compiles, while
  `backgroundColor` is the other way round.
- `compileIosMainKotlinMetadata` and `compileKotlinIosSimulatorArm64` do not agree on which form
  exists. `setAutoresizingMask` and `setBackgroundColor` resolve for the target and not for the
  shared metadata, so `iosMain` must be compiled both ways before it is believed. `./gradlew build`
  does that; linking one target does not.
- `xcrun simctl spawn <device> sample.kexe` also runs the Kotlin/Native binary inside the simulator
  and prints the report, and `--standalone` is not required for it. It is not an app, though: no
  bundle, no `Info.plist`, no icon, no view. That is the difference `:sample:iosSimulatorApp` closes.

## The planner

`StartupPlanner` lives in `commonMain` as the single copy for all eleven targets, so a regression in
the ordering rules cannot hide on one platform. Five properties matter and each has a test:

1. Kahn with an in-degree map and a FIFO ready queue seeded in declaration order. Every map and set
   is insertion ordered, so the plan is a pure function of declaration order and dependency order.
2. `satisfied` is threaded through all three phases: the pending set, the in-degree counts and the
   dependents map. Dropping edges into already-created components in only one or two of them is a
   silent bug that shows up as a phantom cycle.
3. A cycle is recovered with an iterative three-color DFS over the residual set, and
   `stack.indexOf(dependency)` trims the acyclic approach tail, so `X -> Y -> Z -> Y` is reported as
   `Y -> Z -> Y`. This matches AndroidX, which throws at the point of re-entry.
4. The execution loop re-checks both guards on every iteration. `if (component in initialized)
   continue` stops a component that a nested `initializeComponent` already created from being
   created a second time when the loop reaches it; `if (component in creating)` catches a component
   that is still in flight, which is a cycle the planner cannot see because the edge back was made
   imperatively rather than declared. Without the second guard that case recursed until the stack
   died, and killed the process outright on Kotlin/Native.
5. Everything a component declares is read inside a guard, `dependencies()` as well as `create()`, so
   a caller only ever has to catch `StartupException`. AndroidX wraps both in the same `try`.

## Where the two runtimes differ

Deliberate, documented in README.md, and not to be "fixed" silently:

- Independent components are ordered by AndroidX's depth-first walk on Android and by Kahn levels
  elsewhere. Both are valid topological orders; only a declared dependency is portable.
- `initializeComponent` succeeds on Android for a component no manifest registers, because AndroidX
  reflects and never reads a manifest. Off Android it throws.
- `androidManifestMetadata()` can only emit fully qualified names on Android, because
  `KClass.qualifiedName` does not compile on Kotlin/JS. `AndroidInitializerContractTest` pins the
  Android output; the `commonTest` assertion can only check the shape. It returns the `<meta-data>`
  lines and nothing else — no `<manifest>`, `<application>` or `<provider>` around them. README.md
  used to imply otherwise and no longer does.
- `AppInitializer` has three members off Android that Android does not have: `isInitialized`,
  `initializationOrder` and `manifest`. They stay out of the `expect` on purpose.
  `androidx.startup.AppInitializer` is a `typealias` target, so nothing can be added to it, and it
  exposes neither the order it created things in nor any state to derive one from; an `expect` with
  a lying Android `actual` would be worse than an unresolved reference. README.md's API-mapping
  table names all three as non-Android extras.
- `macosX64` and `iosX64` are compiled and linked but never executed anywhere, on this machine or in
  CI.

## Build

```
./gradlew build
./gradlew testAndroidHostTest desktopTest macosArm64Test iosSimulatorArm64Test jsNodeTest \
          wasmJsNodeTest linkDebugTestLinuxX64 linkDebugTestMingwX64
./gradlew :sample:compileAndroidMain :sample:compileKotlinDesktop :sample:compileKotlinJs
./gradlew :startup:compileCommonMainKotlinMetadata :sample:compileCommonMainKotlinMetadata
./gradlew publishToMavenLocal
```

**A platform compilation is not a verification.** `compileAndroidMain`, `compileKotlin<Target>` and
`link<Target>` accept `expect`/`actual` shapes that `compileCommonMainKotlinMetadata` rejects, and
the metadata task is the one a consumer's own published module runs. `./gradlew build` covers both;
a hand-written list of per-target compile tasks does not, and twice now a round of verification that
listed only those reported a shape as working when it was not.

Three verification tasks are wired into `check` beyond the tests.

`:startup:checkObjCExport` links `Startup.framework` for `iosSimulatorArm64` and greps the generated
Objective-C header. It asserts that the four `inline reified` registration functions are absent from
it, that the five key-taking overloads are present, and that `Context` is exported as
`StartupContext` and not under its bare name.

`:sample:checkConsumerObjCExport` links two more frameworks from `sample`, which unlike `:startup`
is a *consumer* of the library. One exports `:startup` and one does not, and the task greps both:
the exported header must carry the unprefixed names README.md's Swift snippets use, and the bare one
must carry the `Startup`-prefixed names and no `InitializerKeyKt`. That pair is the only place this
repository sees the framework shape an application actually gets.

`:startup:checkAndroidFloors` unzips `startup/build/outputs/aar/startup.aar` and fails if
`minSdkVersion` rises above 21 or `minCompileSdk` above 34, which are `androidx.startup`'s own
floors. It needs no Mac.

The two Objective-C tasks are `onlyIf { HostManager.hostIsMac }`, so a Linux or Windows runner skips
them.

Running the sample, one target at a time:

```
./gradlew :sample:desktopRun
./gradlew :sample:runDebugExecutableMacosArm64
./gradlew :sample:jsNodeRun :sample:wasmJsNodeRun
./gradlew :sample:jsBrowserRun
./gradlew :sample:wasmJsBrowserRun
./gradlew :androidApp:installDebug
./gradlew :sample:iosSimulatorApp
xcrun simctl boot "iPhone 17 Pro Max"
xcrun simctl install booted sample/build/iosApp/SampleApp.app
xcrun simctl launch --console booted io.github.kunal26das.startup.sample.app
```

`jsNodeRun`, `jsBrowserRun`, `wasmJsNodeRun` and `wasmJsBrowserRun` are aliases registered in
`sample/build.gradle.kts`. Kotlin 2.4 only creates the `...DevelopmentRun` and `...ProductionRun`
pair itself.

`jsBrowserRun` and `wasmJsBrowserRun` never exit: they hold a webpack dev server on
<http://localhost:8080/> until Ctrl-C, and both bind that one port, so run one at a time and expect
no exit code from either.

The crash-reporting SDK the report names is per target, not per environment. `:sample:jsNodeRun`
prints `BrowserCrashReporter` under Node, because Kotlin/JS has one `jsMain` for both environments,
and macOS and iOS both print `AppleCrashReporter`, because `appleMain` is one source set. Neither is
a bug; neither is evidence of which environment ran.

`linuxX64` and `mingwX64` cannot be executed on a macOS host at all — the artifacts are an x86-64
ELF binary and a PE32+ executable, and both fail with `exec format error`. Link them here, run them
on Linux and Windows.

Adding a web executable pulls `webpack-dev-server` into the yarn workspace, so the first build after
that change fails `kotlinStoreYarnLock` until `./gradlew kotlinUpgradeYarnLock` is run.

An Android app targeting SDK 35 or newer is edge to edge, and a `setContentView` root that does not
handle insets is drawn under the status bar and the action bar. `androidApp` uses a `NoActionBar`
theme and `fitsSystemWindows = true`; without both, the first four lines of the report are invisible
on screen while still appearing in Logcat.

`linuxX64Test`, `mingwX64Test`, `macosX64Test` and `iosX64Test` are auto-disabled on an arm64 Mac,
so linking their test binaries is the local proof; CI runs the first two for real on
`ubuntu-latest` and `windows-latest`.

Publications are signed only when `-PsignPublications=true` is passed, so `publishToMavenLocal`
works on a machine with no GPG key. A real release therefore reads:

```
./gradlew publishAndReleaseToMavenCentral -PsignPublications=true
```

with `ORG_GRADLE_PROJECT_signingInMemoryKey`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`,
`ORG_GRADLE_PROJECT_mavenCentralUsername` and `ORG_GRADLE_PROJECT_mavenCentralPassword` in the
environment. No CI job does this; it is a manual step.

# CLAUDE.md

A Kotlin Multiplatform port of AndroidX App Startup, published as `io.github.kunal26das:startup`.
On Android the public API is a set of `typealias`es of `androidx.startup`, with zero wrapper types.
On the other ten targets it is a hand-written runtime whose order comes from Kahn's algorithm.

## House style

- **KDoc on every public declaration.**
- **No inline `//` comments, no block comments, no commented-out code, anywhere.** Not in Kotlin,
  not in Gradle scripts, not in YAML, not in config files. KDoc is the only comment form allowed.
- **One top-level declaration per Kotlin file, and the file name is the declaration name.** A file
  may carry a second top-level declaration only when the first cannot own it — a public extension on
  a `typealias`'d Java type, or an internal helper whose `expect` declaration's contract is written
  in terms of it. Four kinds of file qualify today:
  - `InitializerKey.kt`, where the three `initializerKey` overloads live beside the class they
    build: all of them must be top-level, because a Java class cannot satisfy an
    `expect companion object`. They also have to stay in that one file: a non-`expect` function in
    the `commonMain` copy would generate a second `InitializerKeyKt` JVM facade and collide with the
    `androidMain` and `desktopMain` ones, which is why the instance overload is `expect`/`actual`
    despite having one possible body.
  - `AppInitializer.kt` in `commonMain`, `androidMain` and `nonAndroidMain`, for
    `initializeComponentOrNull`. Same forcing: on Android `AppInitializer` is a `typealias` to a Java
    class and cannot gain a member, so the read has to be an extension, and it has to be
    `expect`/`actual` for the same JVM-facade reason.
  - `nonAndroidMain/StartupLock.kt`, where `startupLockBarrier()` builds the one exception the lock
    itself raises.
  - `commonMain/CoroutineInitializer.kt`, where `COROUTINE_INITIALIZER_UNSUPPORTED` is the message
    the `awaitBlocking` actuals on JS and Wasm refuse with. It is internal and it is the whole of
    what those two actuals say, so the interface's file is where it belongs.
  The same rule is why `awaitBlocking` sits in its own `AwaitBlocking.kt` rather than beside
  `CoroutineInitializer`: the interface's file already generates a JVM facade for
  `COROUTINE_INITIALIZER_UNSUPPORTED`, and a same-named actual file would collide with it.
- No AI attribution in commits, pull requests or files.

## Layout

```
startup/                  the published library
  src/commonMain          the public API and StartupPlanner
  src/androidMain         typealiases onto androidx.startup, plus what cannot be one: Startup,
                          the initializerKey overloads, componentName, and the four 3.0.0 adds
                          — initializeComponentOrNull, StartupWaveThread, StartupOnce and
                          awaitBlocking
  src/nonAndroidMain      the runtime for the other ten targets
  src/desktopMain         the JVM StartupLock, StartupWaveThread, StartupOnce, awaitBlocking
  src/nativeMain          the Kotlin/Native StartupLock, its thread token, StartupWaveThread,
                          StartupOnce, startupYield's expect, awaitBlocking
  src/appleMain           the sched_yield actual, and nothing else
  src/linuxMain           the sched_yield actual, and nothing else
  src/mingwMain           the SwitchToThread actual, and nothing else
  src/jsMain              the single-threaded StartupLock, StartupWaveThread and StartupOnce,
                          and the awaitBlocking that refuses
  src/wasmJsMain          the single-threaded StartupLock, StartupWaveThread and StartupOnce,
                          and the awaitBlocking that refuses
  src/commonTest          planner and manifest tests, run on all eleven targets
  src/nonAndroidTest      engine tests, run on the ten non-Android targets
  src/desktopTest         the concurrency, wave-contract, barrier and coroutine tests, which
                          need real threads; the wave-sibling refusal is in nonAndroidTest,
                          because a same-thread runner needs none
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
`SampleStartup.manifest` in step, and from 2.0.0 it does so with no library API at all: it reads the
XML off disk and compares the `<meta-data>` names against
`SampleStartup.manifest.eagerComponents.map { it.name }`, which works because on Android an
`InitializerKey` **is** a `java.lang.Class`. It is now the exact test README.md tells a consumer to
copy, so the two must not drift apart — change one and change the other. The manifest is declared as
an `inputs.file` on the test task; without that the task stays up to date when the XML changes and
the assertion silently stops running. The negative control is the point of the whole thing: delete a
`<meta-data>` line and `:sample:testAndroidHostTest` must fail.

`MainActivity` prints only `AndroidSampleStartup.isEager(context)` above the report. Until 1.1.0 it
also printed `androidManifestDrift(context)`, which was the one exercise of the `PackageManager`
path; that declaration is gone in 2.0.0 and nothing replaced it, because the check it performed now
belongs to the consumer's own test rather than to the library.

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
```

`:startup` has an `appleMain`, `linuxMain` and `mingwMain` again as of 3.0.0. Each holds exactly one
declaration, the `startupYield` actual — `sched_yield` on Apple and Linux, `SwitchToThread` on
mingw — because the Kotlin/Native lock is a compare-and-set loop and there is no parking primitive
every native target shares. They had been deleted in 2.0.0, when removing `initializerKey(objCClass)`
emptied `appleMain`.

`sample` adds one more edge on top of that, `consoleMain`, which `desktopMain`, `jsMain`,
`wasmJsMain`, `macosMain`, `linuxMain` and `mingwMain` depend on and `iosMain` does not. A source
set may depend on two parents, so `macosMain` reaching both `appleMain` and `consoleMain` is fine.
That is what lets `iosMain` declare its own `main` without redeclaring the console one.

Declare the edges with `getByName` and `create`, never `by getting` and `by creating`, which are
deprecated on Gradle 9.7.1. Missing even one edge produces
`Expected <X> has no actual declaration in module <commonMain>`.

## Removed in 2.0.0

Eight declarations carried `@Deprecated(level = DeprecationLevel.WARNING)` in 1.1.0 and are **gone
in 2.0.0**:

| removed                                          | file it lived in                       |
| ------------------------------------------------ | -------------------------------------- |
| `AppInitializer.isInitialized(component)`         | `nonAndroidMain/AppInitializer.kt`      |
| `AppInitializer.initializationOrder()`            | `nonAndroidMain/AppInitializer.kt`      |
| `AppInitializer.manifest()`                       | `nonAndroidMain/AppInitializer.kt`      |
| `StartupManifest.androidManifestMetadata()`       | `commonMain/StartupManifest.kt`         |
| `StartupManifest.androidManifestDrift(Set)`       | `commonMain/StartupManifest.kt`         |
| `StartupManifest.androidManifestDrift(Context)`   | `androidMain/AndroidManifestDrift.kt`   |
| `StartupManifest.verifyAndroidManifest(Context)`  | `androidMain/VerifyAndroidManifest.kt`  |
| `initializerKey(objCClass)`                       | `appleMain/InitializerKey.kt`           |

The last three files are deleted outright, and with them the whole `appleMain` and `appleTest` source
sets, which held nothing else. `StartupEngine` lost `isInitialized`, `initializationOrder` and
`manifest` at the same time, because the three `AppInitializer` members were their only callers; its
`initialized` map stays, because the execution loop still reads it.

None of the eight had a counterpart in `androidx.startup`, and mirroring `androidx.startup` is the
whole contract of this library. They were also the only platform asymmetry in README.md's
API-mapping table: three existed on the ten non-Android targets and not on Android, four existed for
Android's manifest and did nothing useful off it, and one existed on the Apple targets alone. The API
is now one shape on all eleven targets.

**Do not add any of them back, and do not add a ninth declaration of that kind.** A declaration earns
its place in this library by having an `androidx.startup` counterpart to mirror, not by being useful
on its own. The Android drift check in particular will look worth reintroducing and is not: the
problem it addressed is real and is now the consumer's, answered by a parity test of their own, which
is what `sample`'s `AndroidManifestParityTest` and README.md's **Keep the two Android registries in
step** demonstrate.

3.0.0 adds four declarations with no counterpart, and each is here because the mirror is *broken*
without it rather than because it is useful. `WaveRunner`/`StartupTask` and `CoroutineInitializer`
are argued for under **Verified shapes** below. The other two:

- `AppInitializer.initializeComponentOrNull(key)`. `initializerKey(instance)` returns an
  `AnyInitializerKey`, which `initializeComponent` cannot take, and a `create` that returned null had
  nothing the typed read could hand back. So the runtime-key path — the only one Swift and a
  plugin host can use — could register a component and never read it, and a Swift initializer
  returning `nil`, which the Objective-C export makes the natural thing to write, crashed the reader
  with a `NullPointerException` raised inside the library. AndroidX has no counterpart because on
  Android neither problem exists: a key is a `java.lang.Class` and `create` is `@NonNull`. This
  declaration exists to make the other ten targets reach the behaviour Android already had.
- `StartupTask`'s public constructor. 2.1.0's wave was `List<() -> Unit>`, which a host could
  fabricate to test its own runner. Naming the component took that away; making the constructor
  public gives it back. The engine never trusts a task it did not create.

**What went with them, and what did not.** `AndroidManifestDriftTest` and `ObjCInitializerKeyTest`
tested only removed API and are deleted. `StartupManifestTest` lost its two
`androidManifestMetadata` tests, `AndroidInitializerContractTest` its two manifest-helper tests, and
`AndroidManifestParityTest` its generated-metadata test. One assertion inside a deleted test was
kept: `AndroidInitializerContractTest.aKeyNamesItsClassFully` pins that on Android
`componentName(key)` is the fully qualified name, which `androidManifestMetadataNamesClassesFully`
used to be the only place asserting, and which is what makes an AndroidManifest parity test possible
at all. `InitializerKeyTest.keysNameThemselves` deliberately does `substringAfterLast('.')`, so it
cannot stand in for it. `StartupRuntimeTest`'s three introspection
tests and `StartupManifestBuilderTest`'s one were **rewritten, not deleted**: every behaviour they
asserted is still real, and it is observed through what the components themselves record — `TestLog`
in `:startup`, `Logger.initialized` in `sample` — or through `isEagerlyInitialized`, `isEager`,
`contains` and the fact that resolving an already-created component adds nothing to the log. That is
the rule for any future removal here: an assertion is re-expressed against the surviving API or its
loss is stated, never quietly dropped.

**What is not removed, and must not be.** The key-taking registration overloads —
`metaData(component, factory)`, `lazyInitializer(component, factory)`, `remove(component)`,
`initializerKey(initializer)` and `initializerKey(kClass)`. `androidx.startup` registers by name in
XML, so a key-taking overload is *closer* to it than a `reified` one, and without them Swift cannot
register a host-supplied initializer at all. Everything else published in 1.1.0 is unchanged in
2.0.0; that release removes those eight and nothing else.

**2.1.0 tightens one rule and adds nothing.** `StartupManifest.initializerOf` now rejects a factory
whose product does not key to the component it was registered under, so
`metaData<Base> { Derived() }` throws where 2.0.0 filed the `Derived` under `Base`'s key. Kotlin
function types are covariant in their return type, so the `reified` overloads express the mismatch
as readily as the key-taking ones. It earns its place by the usual rule rather than in spite of it:
registering a subclass under a supertype key never worked on Android, where AndroidX reflects the
key and ignores the factory, so accepting it off Android let one manifest build two different
graphs. The check is `commonMain`, so it fires on Android too — under `StartupPlanner.validate`,
never under `Startup.install`, which calls no factory there. Do not phrase it as an "off Android"
rule anywhere; that was wrong in README.md once already.

One assertion was lost outright and is not covered anywhere. `androidManifestMetadata()` emitted a
line per node in declaration order, tombstones included, so `StartupManifestBuilderTest` could assert
where a `remove<T>()` entry sat among the others. No surviving public accessor exposes tombstones in
order: `components` and `eagerComponents` both exclude them. Node kind is still pinned exactly, by
`isEager` and `contains` over a Merge, a Lazy and a Remove entry; the ordinal position of a tombstone
is not, and cannot be without adding API that androidx.startup has no counterpart for.

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
  `androidx.startup:startup-runtime:1.2.0` publishes. Neither may rise. As of 2.0.0 `androidMain`
  calls no `android.*` method at all — it is `typealias`es plus a loop over
  `AppInitializer.initializeComponent`. Through 1.1.0 it reached `ComponentName.getClassName`,
  `Context.getPackageManager`, `Context.getPackageName`, `PackageManager.getProviderInfo`,
  `ProviderInfo.metaData`, `Bundle.getString` and `Bundle.keySet`, every one of them API 1, and all
  of them went with `androidManifestDrift(Context)`. A higher floor therefore buys nothing and costs
  a consumer everything: a
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
- **Every `initializerKey` overload lives in a file named `InitializerKey.kt`**, in whichever source
  set declares it. Kotlin/Native derives the Objective-C facade class from the file name, so an
  overload in `ObjCInitializerKey.kt` lands in `ObjCInitializerKeyKt` and Swift cannot reach it
  through `InitializerKeyKt` at all — `swiftc` reports *no exact matches in call to class method
  'initializerKey'* and lists only the overloads from the other facade. Same-named files across
  source sets are the established pattern here and they merge into one facade, which is what
  `Context.kt`, `AppInitializer.kt` and `InitializerKey.kt` already rely on. This is how
  `initializerKey(objCClass:)` was reachable from Swift while it existed, in an `appleMain`
  `InitializerKey.kt` over `kotlinx.cinterop.getOriginalKotlinClass`; it is gone in 2.0.0 and is not
  coming back, but the naming rule binds every overload that stays.
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
- No coroutine **in the engine**: `runBlocking` exists on neither Kotlin/JS nor Kotlin/Wasm, and
  both are first-class targets here. Execution is sequential on the calling thread unless the caller
  supplies a runner. `StartupPlan.waves` exposes the Kahn levels, `waves.flatten() == order` is
  still its whole contract, and as of 2.1.0 it is also the unit
  `Startup.install(context, manifest, runner)` hands to a `WaveRunner`. That is the seam this file
  spent three releases refusing, and it is only sound because of where it was put: the host runs the
  wave, and the library keeps the planning, ordering, cycle detection and the created components.
  As of 3.0.0 the unit is a `StartupTask`, which names its component, because a runner that cannot
  tell one task from another cannot give a component thread affinity, cannot trace one, and cannot
  say which one failed.
  **The lock is still held across the whole install, so a task inside a runner may not call
  `initializeComponent` from another thread** — there is no thread that could serve it. 3.0.0 makes
  that a `StartupException` raised at once rather than a wait: `internal expect object
  StartupWaveThread` is a thread-local flag `StartupTask.invoke` raises around the body, and
  `StartupLock.withLock` fails on the contention path when it is set instead of parking on the JVM
  or spinning a core on Native. The flag rather than a lock-wide barrier is the point: a thread that
  is *not* running a task is waiting for something that really does arrive when the install ends,
  and refusing it too would turn a correct call into a crash. A task **on the installing thread**
  may still resolve anything an **earlier** wave created, because the lock is reentrant; the 2.x flat
  prohibition was wrong about that half, and it is why `sample`'s own manifest —
  `NetworkInitializer`, `AnalyticsInitializer` and `CrashReportingInitializer` resolve each other
  imperatively, the AndroidX-documented pattern — can be installed with a same-thread runner after
  all. It works there because those components *also* declare the edges they resolve, which is what
  puts every dependency in a strictly earlier wave than the component reading it — not one component
  per wave: `CrashReportingInitializer` and `NetworkInitializer` share the second level, declaring
  nothing of each other. `:sample`'s `SampleWavePlanTest` checks that separation on a
  `StartupPlanner` plan, which is a pure function of the manifest and so says the same thing whatever
  order the suite runs in; an install there would prove nothing, because `Startup` is a process
  singleton and the graph is already built by the time any one test runs. The runner behaviour itself
  is pinned in `:startup`, against an isolated engine, by `WaveSiblingTest`. What the installing thread may **not** ask for is a component of
  the wave in flight. Nothing a wave creates is written back until `WaveRunner.run` returns, so a
  sibling is neither created nor creatable, and two components share a wave precisely when neither
  declares the other — there is no cycle there to report, and reporting one printed
  `Alpha -> Sibling -> Alpha` for a graph with no edges at all. `StartupEngine.inFlight` is the split:
  a `creating` entry that belongs to the wave being run is refused by name, and every other one is
  still a real cycle rendered as a real path. A component asking for *itself* inside a wave goes
  down the first arm too, and the refusal names that case in so many words. It really is a cycle,
  but `reentrantCycle` cannot draw it here: the path it renders is stitched from `creating`, which
  under a runner holds the whole wave rather than a nesting stack, so a self-call in a wave of two
  printed `Self -> Sibling -> Self`. Without a runner it is still `Cycle detected: Self -> Self`,
  and `StartupRuntimeTest.rejectsAComponentThatAsksForItself` pins that. Declaring the edge in `dependencies()` is what makes a
  component safe to run in a wave on any thread. On Android the runner is ignored: AndroidX
  owns creation and offers no seam, so a runner is a performance decision on the other ten targets
  and never a correctness one. Wish reported the 1.x migration costing it the concurrent startup its
  hand-rolled runtime had; that is the cost this closes, and README.md's **Running a wave
  concurrently** is the consumer-facing version.
- **`CoroutineInitializer` is the one coroutine in the artifact**, added in 3.0.0 against this
  file's own three-release stance, because the stance was answering the wrong question. `suspend` is
  a language feature on all eleven targets; only *blocking a thread to await one* is missing on JS
  and Wasm. Wish's real initializers are `suspend (WishContext) -> T` smuggled through a
  fire-and-forget `GlobalScope.launch`, so its graph ordered the launches and not the completions —
  which is to say its dependency edges did not wait, which is to say they were not edges. The bridge
  is `internal expect fun <T> awaitBlocking(block: suspend () -> T): T`: `runBlocking` on Android,
  JVM and Native, and a `StartupException` on JS and Wasm, exactly the shape `StartupLock` already
  uses for a capability nine targets have and two do not. It costs
  `kotlinx-coroutines-core` in the Android, JVM and native artifacts and nothing in the JS and Wasm
  ones. `CoroutineInitializer<T : Any>` bounds `T` because `androidx.startup.Initializer` declares
  `create` `@NonNull`, and without the bound `compileAndroidMain` rejects the override.
- `internal expect class StartupLock` in `nonAndroidMain`, with four actuals: `ReentrantLock` on the
  JVM, a reentrant spin lock over `kotlin.concurrent.AtomicReference` plus a `@ThreadLocal` token on
  Native, and a direct call on JS and Wasm, which are single-threaded. AndroidX serializes every
  initialization inside `synchronized (sLock)`, so shared code cannot be unsynchronized off Android:
  eight threads asking for two components created them five to eight times without it, and
  intermittently reported a cycle on a graph with no edges.
- No `atomicfu`, and exactly one third-party dependency off Android: `kotlinx-coroutines-core`,
  which 3.0.0 adds to `desktopMain` and `nativeMain` for `awaitBlocking` and to `androidMain`
  beside `androidx.startup`. It reaches no public signature — `suspend` is a language feature, so a
  consumer implements `CoroutineInitializer` without declaring coroutines itself — which is why
  `implementation` rather than `api` is right here and `api` is right for `androidx.startup`. The
  JS and Wasm artifacts have no dependency at all, because their `awaitBlocking` only throws.
  Verified by publishing to `mavenLocal` and building a consumer that declares nothing but this
  library. Nothing else earns a place: a second one has to close a hole in the mirror the way this
  one does, not merely be convenient.
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
the ordering rules cannot hide on one platform. Seven properties matter and each has a test:

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
6. The path that second guard reports is stitched from frames, not from `creating`. `creating` is
   the nesting stack, and two entries next to each other in it need share no edge: the outer
   component asked for something else that merely happened to need the inner one first. Rendering
   the stack directly printed `A -> B -> A` for a graph whose real cycle was `A -> D -> B -> E ->
   A`, naming an edge that exists nowhere and dropping the component that joined the frames. Each
   in-flight component therefore carries the `StartupEngine.Frame` — the roots and
   `StartupPlan.edges` of the `execute` call that created it — and `reentrantCycle` walks frame by
   frame, breadth first, filling in the hops between. `StartupPlan.edges` is internal and exists
   for nothing else. `namesTheComponentThatLinksTwoNestedCreateCalls` nests two levels deep, which
   is the only shape that catches this; do not delete it.
7. The engine's map of constructed-but-not-yet-created initializers is emptied when the outermost
   run unwinds, however it ends, and the guard is a `depth` counter rather than
   `creating.isEmpty()`. The map has to outlive a single plan, because `dependencies()` is read at
   plan time and `create()` runs later, and a nested `initializeComponent` has to see the same
   instance. It must not outlive the run: `installed += manifest` lets a later install replace a
   component's factory, and `initializerOf` answers from the map before it reads the factory, so an
   instance stranded by a failed run shadows the replacement and the corrective install fails
   identically. `creating` cannot be the guard, because `dependencies()` is itself allowed to call
   `initializeComponent`, and that nested run begins while the outermost one is still planning and
   `creating` is still empty — it would clear the map out from under the outer plan and rebuild
   everything already built. Both halves are pinned, by
   `letsALaterInstallReplaceAComponentAFailedInstallLeftUncreated` and
   `keepsTheOuterPlansInitializersWhenDependenciesResolvesSomething`. Through 1.1.0 the map was a
   field on `StartupManifest`, which `plus` rebuilt per install, so the scope was accidental.

## Where the two runtimes differ

Deliberate, documented in README.md, and not to be "fixed" silently:

- Independent components are ordered by AndroidX's depth-first walk on Android and by Kahn levels
  elsewhere. Both are valid topological orders; only a declared dependency is portable.
- `initializeComponent` succeeds on Android for a component no manifest registers, because AndroidX
  reflects and never reads a manifest. Off Android it throws. The same holds for a component
  `remove<T>()` hides, and for one an eager component still depends on: AndroidX reads
  `dependencies()` reflectively and never consults a `StartupManifest`, so it creates it there while
  off Android the manifest fails to plan. `requireRegistered` reports that as a removal rather than
  as a missing registration, so the remedy on offer is not to re-register the entry the application
  deliberately took out. Its two arms say different things, because the component can be reached as
  a dependency or asked for outright, and only the first has a `dependencies()` entry to drop.
- A factory that builds something other than the class its key names throws wherever the graph is
  planned. On Android that is `StartupPlanner.validate` only, because `Startup.install` never calls
  a factory there, so an app that does not validate keeps running on the class AndroidX reflected.
- A key names its class fully on Android, where it is a `java.lang.Class`, and simply everywhere
  else, because `KClass.qualifiedName` does not compile on Kotlin/JS. That is why an AndroidManifest
  parity check has to live in an Android source set: `sample`'s `AndroidManifestParityTest` compares
  `eagerComponents.map { it.name }` against the XML and could not do so from `commonTest`.
- `AppInitializer` is the same two members on all eleven targets as of 2.0.0. Until 1.1.0 it carried
  `isInitialized`, `initializationOrder` and `manifest` off Android; they stayed out of the `expect`
  because `androidx.startup.AppInitializer` is a `typealias` target, so nothing can be added to it,
  and it exposes neither the order it created things in nor any state to derive one from. Removing
  them closed this row of the difference rather than papering over it: the answer on all eleven
  targets is to record what you need from inside your own `create`, which is what `sample`'s
  `SampleReport` already does.
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

**`./gradlew build` emits no `w:` warning from any source file, and it stays that way.** As of 2.0.0
no `.kt` file carries `@Suppress("DEPRECATION")` at all — the eight declarations that needed one are
gone — so one appearing in Kotlin source again is a signal that something was reintroduced. The one
`w:` the build does print, `target macos_x64 is deprecated and will be removed soon`, comes from the
Kotlin/Native target-tier policy rather than from anything in this repository, appears whenever a
`macosX64` compilation actually executes rather than being up to date, and has no source-level
suppression — the two `@Suppress("DEPRECATION")` annotations left in the repository are the ones on
`macosX64()` in the two build scripts, and they silence the Gradle DSL deprecation, not the
compiler's.

Three verification tasks are wired into `check` beyond the tests.

`:startup:checkObjCExport` links `Startup.framework` for `iosSimulatorArm64` and greps the generated
Objective-C header. It asserts that the four `inline reified` registration functions are absent from
it, that the five key-taking overloads are present — `metaData(component:factory:)`,
`lazyInitializer(component:factory:)`, `remove(component:)`, `initializerKey(initializer:)` and
`initializerKey(kClass:)`, six until `initializerKey(objCClass:)` went in 2.0.0 — and that `Context`
is exported as `StartupContext` and not under its bare name.

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

`gradle.properties` sets `kotlin.incremental.wasm=false`, and the reason cannot live beside it
because the house style bans comments in config files. Kotlin 2.4.10's Wasm incremental compilation
crashes with `NoSuchElementException: Key ic#NN:kotlin.text/... is missing in the map` whenever an
edit changes which stdlib functions a shared source set references, reproduced four times out of
four. Remove the line when that is fixed upstream, not before.

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
environment. `.github/workflows/release.yml` runs that command — plus
`--no-configuration-cache` — on every push of a `v*` tag, so pushing a `v*` tag is itself an
irreversible Maven Central release; the command above is only the out-of-band fallback. It then
publishes to GitHub Packages and cuts a GitHub release, both unconditionally. A
`workflow_dispatch` run does the same except that the Central step is gated behind the
`maven-central` input, which exists because Central refuses a coordinate that is already
released, so a re-run can republish to GitHub Packages alone.

The job also fails outright if the pushed tag does not equal `artifactVersion` in
`startup/build.gradle.kts`. That is the check that makes an unbumped version a release-time
error rather than a silent republish attempt: any behaviour change needs the version moved in
the same commit, because `v1.0.0`, `v1.1.0`, `v2.0.0` and `v2.1.0` are all tagged and Central
holds every one of them immutably.

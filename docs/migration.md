# Migration guide

Apply each version's changes after the version you currently use. Use the
[installation guide](../README.md#installation) for dependency coordinates.

| Current version | Sections to apply |
| --- | --- |
| 1.x | 2.0.0 removals, 2.1.0 factory validation, then 3.0.0 API changes |
| 2.0.0 | 2.1.0 factory validation, then 3.0.0 API changes |
| 2.1.0 | 3.0.0 API changes |
| 3.0.0 | 3.0.1 task failure and runner checks |

## From 3.0.0 to 3.0.1

### Update direct task exception handling

`StartupTask.invoke()` now wraps an ordinary initializer failure in `StartupException`, with the
original throwable in `cause`. An initializer's existing `StartupException` passes through unchanged.
Kotlin code that invokes a task directly and catches a particular exception type must inspect the
wrapper's cause instead:

```kotlin
try {
    task()
} catch (failure: StartupException) {
    val original = failure.cause ?: failure
    println(original.message)
    throw failure
}
```

This allows ordinary task failures to cross the Swift boundary as catchable errors. A Swift runner
still implements the non-throwing `run(wave:)` protocol method and catches errors from each
`invoke()`; the engine reports recorded failures when the runner returns. See the
[Swift guide](swift.md).

### Run every task exactly once

If a runner catches an error from invoking the same task twice, `Startup.install` now reports the
violation after the runner returns. This also applies when the first invocation was still running
on another thread. Valid runners need no change: invoke each task once and wait for every task to
finish. See [runner behavior](runtime.md).

Cycles entered from factories or `dependencies()` callbacks now fail before recursive planning
exhausts the stack. Acyclic nested lookups remain supported, and a failed callback does not prevent
retrying with a corrected registration.

## From 2.x to 3.0.0

### Change explicitly typed runners

`WaveRunner.run` changed from `List<() -> Unit>` to `List<StartupTask>`. Update runner declarations
that name the parameter type:

```kotlin
import io.github.kunal26das.startup.StartupTask
import io.github.kunal26das.startup.WaveRunner

/** Runs every task on the calling thread and then reports the first failure. */
class CallingThreadRunner : WaveRunner {
    /** Completes the wave before propagating a task failure. */
    override fun run(wave: List<StartupTask>) {
        val results = wave.map { task -> runCatching { task() } }
        results.firstOrNull { it.isFailure }?.getOrThrow()
    }
}
```

Lambdas that infer the wave type and invoke each item with `it()` keep the same syntax. A task now
exposes `component` for routing and tracing. Its public constructor also lets you build tasks to
test a runner independently.

### Check runner completion and thread use

- Skipped tasks and swallowed task failures cause `StartupException` after `run` returns.
- A second invocation is rejected at that call. Version 3.0.1 additionally records a caught
  duplicate-invocation error for the engine to report.
- Successful components in a failed wave remain cached; a retry does not create them again.
- A task on a worker thread cannot call back into `AppInitializer` while installation holds the
  engine lock, even for a declared dependency from an earlier wave. Such calls now fail immediately.
- A task on the installing thread may read earlier-wave results, but cannot resolve itself or a
  sibling in the current wave. Unrelated callers on other threads still wait for installation.

Route tasks that perform engine lookups to the installing thread. Declaring a dependency controls
creation order; it does not make worker-thread lookups safe. Forwarding a lookup to another thread,
including through a coroutine dispatcher switch, can still deadlock. See
[execution and threading](runtime.md) for runner requirements. Android ignores the runner and uses
AndroidX initialization.

### Add `try` to Swift calls

The exported API now uses `@Throws(StartupException::class)`. Swift callers must use `try` for
`Startup.install`, `Startup.getInstance`, `AppInitializer.initializeComponent`,
`initializeComponentOrNull`, `isEagerlyInitialized`, `StartupTask.invoke`, `StartupPlanner.plan`, and
`StartupPlanner.validate`. See the [Swift guide](swift.md) for complete calling examples.

### Use the new APIs where needed

| Need | API and behavior |
| --- | --- |
| Read a runtime `AnyInitializerKey`, or a potentially null product | Import and call the `AppInitializer.initializeComponentOrNull(key)` extension; it returns `Any?`. |
| Read a known non-null product | Keep using `initializeComponent(key)`. A null product now raises a named `StartupException` instead of an internal `NullPointerException`. |
| Wait for suspending initialization | Implement `CoroutineInitializer<T : Any>` in Kotlin and override `createAsync(context)`. Its inherited `create` blocks until completion. |

`CoroutineInitializer` adds a `kotlinx-coroutines-core` dependency to Android, JVM, and native
artifacts. Its blocking bridge is unsupported on JS and Wasm; ordinary `Initializer` implementations
continue to work there. Review the [blocking and dispatcher constraints](runtime.md) before adopting
it. Swift implementations need their own waiting strategy rather than Kotlin interface defaults.

## From 2.0.0 to 2.1.0

### Register each factory under the class it constructs

A factory must return exactly the initializer class named by its key. Registering a subclass under
its base-class key now fails validation. Replace:

```kotlin
metaData<BaseInitializerType> { DerivedInitializer() }
```

with:

```kotlin
metaData<DerivedInitializer> { DerivedInitializer() }
```

Update dependency and lookup keys to name `DerivedInitializer` too. The same rule applies to
`lazyInitializer` and the overloads that take an explicit key.

Android reflects the registered class and ignores the factory. The non-Android runtime checks the
factory when planning; `StartupPlanner.validate(manifest)` performs the same check on every target,
including Android. Android's `Startup.install` does not call factories and therefore does not run
this validation.

Version 2.1.0 also adds the optional `Startup.install(context, manifest, runner)` overload and
`WaveRunner`. Existing calls without a runner continue to work. The runner is ignored on Android.

## From 1.x to 2.0.0

Replace the eight APIs deprecated in 1.1.0 and removed in 2.0.0:

| Removed API | Migration |
| --- | --- |
| `AppInitializer.isInitialized(component)` | Record state in your initializer if you need it. Repeated `initializeComponent` calls already return the cached result. |
| `AppInitializer.initializationOrder()` | Record completed components from `Initializer.create`. |
| `AppInitializer.manifest()` | Keep the `StartupManifest` passed to `Startup.install`. |
| `manifest.androidManifestMetadata()` | Write Android `<meta-data>` entries explicitly. |
| `manifest.androidManifestDrift(declared)` | Add a consumer-owned manifest parity test. |
| `manifest.androidManifestDrift(context)` | Add a consumer-owned manifest parity test. |
| `manifest.verifyAndroidManifest(context)` | Add a consumer-owned manifest parity test. |
| `initializerKey(objCClass)` | Use `initializerKey(initializer)` with an instance. |

The retained manifest exposes `components`, `eagerComponents`, `isEager(key)`, and `key in manifest`.
For recorded initialization order, see the sample's
[report implementation](../sample/src/commonMain/kotlin/io/github/kunal26das/startup/sample/SampleReport.kt).

Android still uses its XML manifest as the startup registry. Follow the [Android guide](android.md)
to declare eager components and test parity with the shared `StartupManifest`.

On Swift, replace class-object keys with an instance key:

```swift
let key = InitializerKeyKt.initializerKey(initializer: MyInitializer())
```

This constructs an initializer to obtain its key. Keep constructors free of startup work; place
that work in `create`. Cache keys if obtaining them repeatedly would be expensive. The
[Swift guide](swift.md) covers framework exports and registration.

The key-taking registration overloads remain available: `metaData(key, factory)`,
`lazyInitializer(key, factory)`, and `remove(key)`. Both `initializerKey(instance)` and
`initializerKey(kClass)` also remain available.

## Check initializer constructors on Android

An initializer shared with Android must be a class with a public no-argument constructor. Register
it with a factory such as:

```kotlin
val manifest = StartupManifest {
    metaData<NetworkInitializer> { NetworkInitializer() }
}
```

A factory such as `{ NetworkInitializer(httpClient) }` cannot configure the Android instance:
AndroidX ignores it and constructs the class reflectively. Without a public no-argument constructor,
startup fails with an AndroidX startup exception caused by `NoSuchMethodException`. Kotlin `object`
initializers also lack the constructor AndroidX needs.

Obtain configuration and declared dependencies from `create(context)`; see the
[quick start](../README.md#quick-start) and [platform initializer guide](platform-initializers.md).
`StartupPlanner.plan` and `validate` construct initializer instances even on Android, so keep
factories and constructors free of startup work.

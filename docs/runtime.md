# Runtime and API guide

[Back to the README](../README.md)

Use the [quick start](../README.md#quick-start) for a normal sequential startup graph. This guide
covers composition, diagnostics, runtime keys, and optional execution strategies.

## Compose manifests

`StartupManifest` describes eager roots, lazy registrations, and removals. `include` and `+` merge
manifests; the later entry for a key wins. Overriding a registration keeps its original position.

```kotlin
val appStartup = StartupManifest {
    include(libraryStartup)
    remove<DebugInitializer>()
    lazyInitializer<CacheInitializer> { CacheInitializer() }
    metaData<AppServicesInitializer> { AppServicesInitializer() }
}
```

In this schematic example, `libraryStartup` and the three initializer classes belong to your app.
The same merge can be expressed as `libraryStartup + applicationOverrides`.

A removed registration cannot create a new component off Android. If something still depends on
that registration, validation fails. Removing a registration does not evict a product already
cached by the runtime. On Android, remove the corresponding XML metadata separately; AndroidX can
still construct that class if another component depends on it. See
[Android removal](android.md#disable-an-included-initializer).

Constructors and factories should only construct initializers. Put service startup in `create`,
and use `dependencies()` to describe ordering. Planning and validation call factories even on Android,
where installation itself ignores them. A factory must return the exact class its key names.

## Resolve and inspect components

`Startup.install(context, manifest)` returns the process-wide `AppInitializer` and starts the eager
roots. `Startup.getInstance(context)` returns the same accessor. Results are cached by initializer key.
The first call establishes the context; choose a long-lived context suitable for the process.

| Need | API |
| --- | --- |
| Read or lazily create a typed component | `startup.initializeComponent(initializerKey<MyInitializer>())` |
| Read using a runtime key or allow a null product | `startup.initializeComponentOrNull(key)` |
| Ask whether a component is registered as eager | `startup.isEagerlyInitialized(key)` |
| Inspect your registry | `manifest.components`, `manifest.eagerComponents`, `manifest.isEager(key)`, `key in manifest` |
| Inspect dependency order without starting services | `StartupPlanner.plan(manifest, roots, satisfied)` |
| Validate every registered component | `StartupPlanner.validate(manifest)` |

`initializeComponentOrNull` is an extension; import it from `io.github.kunal26das.startup`.
It allows a null product, but still throws for missing registrations and initialization failures.
Android-compatible initializers must return a non-null product. Return `Unit` for work with no product.

Off Android, `isEagerlyInitialized` checks the installed Kotlin manifest. On Android, it reports
what `InitializationProvider` discovered in XML; programmatic installation alone does not mark a
component eager in that query.

Installing another manifest merges registrations but does not replace already-created products.
The accessor exposes no public reset, initialization-order history, or “is already created” query.
Record application diagnostics inside `create` and retain your own manifest value when needed.

For keys supplied at runtime, use `initializerKey(instance)` or `initializerKey(kClass)` and the
key-taking registration overloads. See [runtime registration](platform-initializers.md) and the
[Swift guide](swift.md) for complete examples.

## Inspect a plan

With `appStartup` from the README:

```kotlin
import com.example.startup.appStartup
import io.github.kunal26das.startup.StartupPlanner

/** Prints the graph's dependency order and independent waves. */
fun printStartupPlan() {
    val plan = StartupPlanner.plan(
        manifest = appStartup,
        roots = appStartup.eagerComponents,
        satisfied = emptySet(),
    )
    println(plan.order)
    println(plan.waves)
}
```

`order` lists components with dependencies first. `waves` groups components whose declared
dependencies are all in earlier waves; flattening it gives `order`. `isEmpty` reports an empty plan.
The `satisfied` set is for callers that already have those products; pass an empty set for a fresh graph.
Planning constructs initializers and reads their dependencies, but does not call `create`.

## Diagnostics

`StartupPlanner.validate(manifest)` checks the whole graph, including lazy registrations, on every
platform. Off Android, the runtime also uses this planner for the components it needs to create.

| Failure | Next step |
| --- | --- |
| `No initializer is registered` | Register the named component and install the manifest before reading it |
| `A remove() entry hides it` | Remove the dependency on that component or stop removing the registration |
| `Its factory produced a ... instead` | Register the initializer under its own class key |
| `Cycle detected` | Follow `StartupException.components` to remove the circular dependency |
| Runner skipped, repeated, or failed a task | Invoke each task once, wait for completion, and preserve failures |
| Worker task tries to access the startup engine | Run that initializer on the installing thread |

For a cycle such as `Entry -> LoopHead -> LoopTail -> LoopHead`, the diagnostic names the cycle:

```text
Cannot initialize LoopHead. Cycle detected: LoopHead -> LoopTail -> LoopHead
```

The complete cycle is available as keys in `StartupException.components`, with the first component
repeated at the end. Prefer those keys over parsing error text in tests. Long statically planned
cycles abbreviate the middle of the message; the component list remains complete. Other errors use
`components` to identify involved or failed initializers, so not every list is a cycle.

Version 3.0.1 also detects factory/dependency callback reentry before it can exhaust
the stack. Keep those callbacks declarative even though valid nested lookups are supported.

On Android, failures raised during initialization come from `androidx.startup.StartupException`.
This library's `StartupException` is a separate type and will not catch an AndroidX exception.
Shared `StartupPlanner.validate` uses the library exception on Android too. Component names are
fully qualified on Android and simple names on other targets.

## Custom wave runners

Start with `Startup.install(context, manifest)`. Add a `WaveRunner` only when you need control over
execution and have checked each initializer's thread requirements. Android ignores this runner.

A runner receives `List<StartupTask>` for one wave. It must:

1. Invoke every task exactly once.
2. Wait for every invoked task to finish before returning or throwing.
3. Propagate failures where the host language allows it. Swift runners catch task errors and return;
   the engine then reports the recorded failures.

`task.component` identifies the initializer and `task.toString()` gives its name for tracing.
Tasks in the current wave do not become readable until the runner returns.

### Thread rules

| Where the task runs | Can it read through `AppInitializer` during installation? |
| --- | --- |
| Installing thread | Yes, for a component completed in an earlier wave |
| Any worker thread | No, including reads of completed dependencies |
| Any thread, reading itself or a current-wave sibling | No |

The installing thread holds the engine lock for the entire operation. Declaring dependencies
orders the work; it does not release that lock. A direct worker lookup is refused. A task that
hands the lookup to another thread can instead deadlock because that thread is outside the task guard.

The sample's network, analytics, and crash-reporting initializers read their dependencies through
`AppInitializer`. Keep them on the installing thread. A same-thread runner is valid:

```kotlin
Startup.install(context, manifest) { wave ->
    val results = wave.map { task -> runCatching { task() } }
    results.firstOrNull { it.isFailure }?.getOrThrow()
}
```

Capturing each result lets the remaining tasks run after one fails. The runner then rethrows the
first failure once every task has finished.

For a mixed graph, call tasks that use `AppInitializer` directly on the installing thread, dispatch
only worker-safe tasks, and join all work before returning. UI work must also run on its required
thread. Dispatching to `Dispatchers.Main` while blocking the main thread is not a substitute for
calling the task directly.

### A runner for worker-safe tasks

The following JVM example is only for initializers that do not call back into
`AppInitializer` and have no requirement to run on the installing thread. It waits for every task
and then propagates the first failure. Add your own compatible `kotlinx-coroutines-core` dependency
when using coroutine APIs directly.

```kotlin
import io.github.kunal26das.startup.AppInitializer
import io.github.kunal26das.startup.Startup
import io.github.kunal26das.startup.StartupContext
import io.github.kunal26das.startup.StartupManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/** Installs a graph whose tasks can all run on worker threads. */
fun installWorkerSafe(
    context: StartupContext,
    manifest: StartupManifest,
): AppInitializer = Startup.install(context, manifest) { wave ->
    runBlocking {
        coroutineScope {
            val results = wave.map { task ->
                async(Dispatchers.IO) { runCatching { task() } }
            }.awaitAll()
            results.firstOrNull { it.isFailure }?.getOrThrow()
        }
    }
}
```

For Kotlin/Native, use the same function in a Native source set and add this platform-specific
import. `Dispatchers.IO` is an extension on Native and a member on the JVM; do not add this import
to the JVM version:

```kotlin
import kotlinx.coroutines.IO
```

Plain `async { ... }` inside `runBlocking` inherits its event loop, so blocking task bodies would
still run sequentially. If a task's asynchronous work needs a constrained pool, do not occupy that
same pool with blocking task invocations. Choosing a dispatcher does not make engine lookups safe.

In 3.0.1, ordinary task errors are wrapped in `StartupException` with the original cause,
and a caught duplicate invocation remains recorded. In 3.0.0, direct task calls can throw the original
exception and a swallowed duplicate may escape validation. See [migration](migration.md).

## Coroutine initializers

Use `CoroutineInitializer<T>` only when startup must wait for suspending work to finish. Its
inherited `create` blocks until `createAsync` completes; it does not make `Startup.install` suspend.
This bridge runs on Android, JVM, and Kotlin/Native. It throws on JS and Wasm, which cannot block
this way. Keep such initializers in supported source sets or provide a separate implementation.

For example, in a JVM source set:

```kotlin
import io.github.kunal26das.startup.CoroutineInitializer
import io.github.kunal26das.startup.StartupContext
import kotlinx.coroutines.delay

/** Demonstrates startup waiting for asynchronous work. */
class WarmupInitializer : CoroutineInitializer<Unit> {
    /** Stands in for a suspending warmup operation. */
    override suspend fun createAsync(context: StartupContext) {
        delay(10)
    }
}
```

Use a regular `Initializer` that launches background work and returns if readiness is not required
at startup. Dependencies will then order the launches, not completion of that background work.

Before choosing the blocking bridge:

- Keep main-thread startup brief. Android's provider starts on the main thread.
- Do not dispatch work to a thread that `create` is blocking, including the main thread.
- Do not fill a constrained pool with blocking tasks whose continuations need that same pool.
- Do not resolve components from `createAsync`, including after switching dispatchers. Arrange access
  to needed data before asynchronous work; declaring an edge does not make that lookup safe.

This is a Kotlin-side convenience. Swift does not inherit Kotlin interface default implementations;
see the [Swift guide](swift.md) for host-side initialization.

## AndroidX API mapping

| AndroidX concept | Shared API |
| --- | --- |
| `Initializer<T>` | `Initializer<T>`; use `BaseInitializer<T>` for no dependencies |
| Android `Context` | `StartupContext` (also available as `Context`) |
| `Class<out Initializer<*>>` | `InitializerKey<T>` / `AnyInitializerKey` |
| `MyInitializer::class.java` | `initializerKey<MyInitializer>()` |
| `AppInitializer.getInstance(context)` | `Startup.getInstance(context)` |
| `initializeComponent(class)` | `initializeComponent(key)` |
| Provider `<meta-data>` eager entry | `StartupManifest { metaData<T> { T() } }`, plus Android XML |
| `tools:node="remove"` | `remove<T>()`, plus Android XML removal |
| No direct counterpart | `StartupPlanner`, `WaveRunner`, `initializeComponentOrNull`, `CoroutineInitializer` |

On Android, the initializer, accessor, context, and key types are aliases, so existing AndroidX
call sites continue to work. Off Android, only registered components can be resolved; AndroidX can
reflect an unregistered class. Independent components may have different valid startup orders:
AndroidX traverses dependencies depth first, while this planner groups them in dependency levels.
Always declare an edge when ordering matters.

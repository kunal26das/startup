# Swift and Objective-C

Export `startup` from your Kotlin framework, then use its explicit-key API from Swift.
The examples below use a framework named `Shared`; replace that import with your framework name.

Dependency examples use **3.0.1**, which includes the task failure fixes described under
[Failures and versions](#failures-and-versions).
For installation and shared Kotlin initializers, see the [README](../README.md).

## Export the library from your framework

Add both `api` and `export` to your Kotlin Multiplatform module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        api("io.github.kunal26das:startup:3.0.1")
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            export("io.github.kunal26das:startup:3.0.1")
        }
    }
}
```

Adapt this to the targets your project already declares rather than declaring those targets a
second time. `implementation` alone is sufficient for Kotlin callers but does not export the
complete Swift API. `export` requires the dependency to be declared with `api`.

The repository's [sample build](../sample/build.gradle.kts) creates both an exported framework
and one without export so their generated APIs can be compared.

## Define, register, and read a Swift initializer

This complete example creates a host-owned string component. It uses ordinary sequential
installation and requires no Kotlin initializer class in your application:

```swift
import Foundation
import Shared

final class HostInitializer: NSObject, Initializer {
    func create(context: StartupContext) -> Any? {
        "Host ready"
    }

    func dependencies() -> [InitializerKey<Initializer>] {
        []
    }
}

func bootHost() throws -> String? {
    let initializer = HostInitializer()
    let key = InitializerKeyKt.initializerKey(initializer: initializer)
    let manifest = StartupManifest.companion.invoke { builder in
        builder.metaData(component: key) { initializer }
    }
    let app = try Startup.shared.install(
        context: DefaultContext.shared,
        manifest: manifest
    )
    return try app.initializeComponentOrNull(component: key) as? String
}
```

Call `bootHost()` with `try` from your startup code and handle any error there. The instance key
identifies `HostInitializer`'s class. Repeating the read returns the cached product rather than
running `create` again.

Use `StartupContext` in Swift signatures. Kotlin's `StartupContext` alias is backed by a class
explicitly exported with that Swift name; the framework does not expose it as plain `Context`.
Use `DefaultContext.shared` when your initializers need no host-specific context.

Swift's `create(context:)` returns `Any?`, so returning `nil` is allowed. Read such products with
`initializeComponentOrNull(component:)`. The non-null `initializeComponent(component:)` API
reports a `StartupException` when a non-Android initializer produced `null`.

## Declare dependencies with instance keys

Use `InitializerKeyKt.initializerKey(initializer:)` for both Kotlin and Swift initializer classes.
For example, this Swift initializer runs after the `HostInitializer` above:

```swift
import Foundation
import Shared

final class HostFeatureInitializer: NSObject, Initializer {
    private let hostKey = InitializerKeyKt.initializerKey(initializer: HostInitializer())

    func create(context: StartupContext) -> Any? {
        "Feature ready"
    }

    func dependencies() -> [InitializerKey<Initializer>] {
        [hostKey]
    }
}

func bootFeature() throws {
    let host = HostInitializer()
    let feature = HostFeatureInitializer()
    let manifest = StartupManifest.companion.invoke { builder in
        builder.lazyInitializer(
            component: InitializerKeyKt.initializerKey(initializer: host)
        ) { host }
        builder.metaData(
            component: InitializerKeyKt.initializerKey(initializer: feature)
        ) { feature }
    }
    _ = try Startup.shared.install(context: DefaultContext.shared, manifest: manifest)
}
```

The dependency key's temporary `HostInitializer()` and the registered `host` instance identify
the same class. Initializer constructors should be inexpensive and have no side effects; building
a key from a Kotlin initializer instance also runs its constructor. Store frequently used keys
instead of reconstructing their instances in every `dependencies()` call.

Swift does not call the reified Kotlin overloads `metaData<T>`, `lazyInitializer<T>`, `remove<T>()`,
or `initializerKey<T>()`; they are hidden from the generated header. Use these spellings:

| Operation | Swift API |
| --- | --- |
| Build a key | `InitializerKeyKt.initializerKey(initializer:)` |
| Register eagerly | `builder.metaData(component:factory:)` |
| Register lazily | `builder.lazyInitializer(component:factory:)` |
| Remove a registration | `builder.remove(component:)` |

Although `initializerKey(kClass:)` is exported, it requires a Kotlin `KClass` value. The instance
overload works directly with ordinary Swift or Kotlin initializer instances. See also
[runtime registration from Kotlin](platform-initializers.md#register-an-initializer-supplied-at-run-time).

## Failures and versions

Since **3.0.0**, the following APIs use `try` in Swift: `Startup.install`, `Startup.getInstance`,
`AppInitializer.initializeComponent`, `initializeComponentOrNull`, `isEagerlyInitialized`,
`StartupTask.invoke`, `StartupPlanner.plan`, and `StartupPlanner.validate`. Their Kotlin export
declares `StartupException`, which crosses this boundary as an `NSError`.

**Version 3.0.0 has a task-error bug:** a Kotlin initializer's ordinary exception can escape
`StartupTask.invoke` without being converted to the declared exception type. When a Swift runner
invokes that task, Kotlin/Native terminates the process before Swift can catch it. A Swift `catch`
does not repair that boundary. Ordinary installation without a Swift wave runner wraps initializer
failures inside the engine before returning to Swift.

**Version 3.0.1 fixes that boundary.** A task wraps an ordinary body failure in
`StartupException`, retaining the original cause, and passes an existing `StartupException`
through unchanged. The engine retains the original body failure for its install diagnostic.
A duplicate invocation also remains recorded if a runner catches the duplicate-call error.
Use version 3.0.1 or newer when invoking tasks from a Swift runner.

Swift implementations of `Initializer.create` and `WaveRunner.run` cannot add `throws` to those
protocol methods. Handle Swift SDK errors within your initializer or expose a Kotlin wrapper that
fits your application's failure model. `CoroutineInitializer` provides Kotlin default method
bodies, which are not Objective-C protocol defaults; implementing it in Swift does not supply
`create` or `dependencies` for you.

## A custom runner

Default installation already runs sequentially. If you add a runner for instrumentation or
scheduling, a safe starting point is to keep tasks on the installing thread:

With the **3.0.1 task-error fix** described above, this runner catches each task's
error and lets the engine report failures after the wave finishes:

```swift
import Foundation
import Shared

final class HostWaveRunner: NSObject, WaveRunner {
    func run(wave: [StartupTask]) {
        for task in wave {
            do {
                try task.invoke()
            } catch {
            }
        }
    }
}

func bootWithRunner(manifest: StartupManifest) throws {
    _ = try Startup.shared.install(
        context: DefaultContext.shared,
        manifest: manifest,
        runner: HostWaveRunner()
    )
}
```

The empty `catch` is intentional: `run` cannot rethrow, tasks record their failures, and the
engine checks them when `run` returns. Invoke every task exactly once and wait for every task
before returning.

A concurrent runner may dispatch only tasks whose `create` methods can run on workers and do not
call `AppInitializer`. The engine holds its lock throughout installation, so even an already-created,
declared dependency cannot be read from a worker through that API. A task that needs to read a
declared dependency must stay on the installing thread; a task may never read a sibling from the
current wave. Declaring dependency order does not change these thread restrictions.

`DispatchQueue.concurrentPerform` can run iterations on both the calling thread and workers, so
it does not keep a particular task on the installing thread. See [runtime behavior](runtime.md)
before adding concurrency or blocking asynchronous initialization.

## Existing framework configuration

If a convention plugin already declares your native targets and frameworks, configure those
existing frameworks instead. Keep the `api` dependency from the first example:

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export("io.github.kunal26das:startup:3.0.1")
        }
    }
}
```

A framework with `transitiveExport = true` can also export `startup` through a shared module it
exports, if that shared module declares `startup` with `api`. Check your generated header for
`swift_name("InitializerKeyKt")` to confirm that the key-building API is present.

If Swift reports missing `InitializerKey`, `InitializerKeyKt`, or `DefaultContext`, check export
configuration before changing your Swift declarations. The repository's consumer fixture has
these names:

| Declaration | With explicit export | Without explicit export |
| --- | --- | --- |
| Initializer interface | `Initializer` | `StartupInitializer` |
| Context | `StartupContext` | `StartupStartupContext` |
| Key type | `InitializerKey` | `StartupInitializerKey` |
| Manifest | `StartupManifest` | `StartupStartupManifest` |
| Key-building functions | `InitializerKeyKt` | Absent |
| Default context | `DefaultContext` | Absent |

`:startup:checkObjCExport` checks callable API signatures and error parameters.
`:sample:checkConsumerObjCExport` compares the exported and unexported consumer headers.
The [sample build](../sample/build.gradle.kts) contains the fixture configuration.

## Objective-C spellings

Objective-C uses framework-prefixed class names and `NSError **` parameters. Swift uses the
short names declared in the generated header. For example, in this repository's `Sample.framework`:

| Swift | Objective-C |
| --- | --- |
| `Startup.shared` | `[SampleStartup shared]` |
| `DefaultContext.shared` | `[SampleDefaultContext shared]` |
| `StartupManifest.companion.invoke { ... }` | `[[SampleStartupManifest companion] invokeBlock:...]` |
| `InitializerKeyKt.initializerKey(initializer:)` | `[SampleInitializerKeyKt initializerKeyInitializer:...]` |
| `Startup.shared.install(context:manifest:)` | `[[SampleStartup shared] installContext:... manifest:... error:...]` |

Use your framework's generated `.h` file for its exact prefix. This example compiles against the
repository's exported `Sample.framework` and registers its Kotlin `RuntimeInfoInitializer`:

```objective-c
#import <Foundation/Foundation.h>
#import <Sample/Sample.h>

void BootSample(void) {
    SampleRuntimeInfoInitializer *initializer = [SampleRuntimeInfoInitializer new];
    SampleInitializerKey<id<SampleInitializer>> *key =
        [SampleInitializerKeyKt initializerKeyInitializer:initializer];
    SampleStartupManifest *manifest = [[SampleStartupManifest companion]
        invokeBlock:^(SampleStartupManifestBuilder *builder) {
            [builder metaDataComponent:key factory:^id<SampleInitializer> {
                return initializer;
            }];
        }];
    NSError *error = nil;
    SampleAppInitializer *app = [[SampleStartup shared]
        installContext:[SampleDefaultContext shared] manifest:manifest error:&error];
    if (app == nil) {
        NSLog(@"Startup failed: %@", error);
    }
}
```

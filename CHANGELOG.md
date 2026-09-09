# Changelog

Migration instructions are in the [migration guide](docs/migration.md). Dependency coordinates are
in the [installation guide](README.md#installation).

## 3.0.1 — 2026-09-09

### Changed

- `StartupTask.invoke()` wraps ordinary initializer failures in `StartupException`, retaining the
  original throwable as `cause`. Existing `StartupException` instances pass through unchanged.
  Kotlin callers that handle task errors directly should inspect `cause`; Swift can now catch
  ordinary task failures without process termination.
- Runner documentation distinguishes dependency order from thread access: declaring an edge does
  not make engine lookups from worker threads safe.
- Reorganize the README around a complete quick start, with focused Android, Swift, runtime,
  migration, sample, and contributor guides.

### Fixed

- Record duplicate task invocations even when a runner catches the rejection, including concurrent
  duplicates while the original invocation is still running.
- Detect cycles entered through factories or `dependencies()` callbacks before recursive planning
  exhausts the stack. Preserve valid nested lookups, cycle paths across planning and creation, and
  cleanup for retry after failure.
- Refuse reentry into an enclosing initialization from a nested wave without attributing a false
  cycle to unrelated sibling tasks.
- Preserve original failure causes and component attribution when wrapping task failures for
  transport through a runner.
- Validate the Android requirements documented in README.md and CLAUDE.md against the library and
  AndroidX AARs, and declare those documents as inputs to `checkAndroidFloors`.

## 3.0.0

- Change `WaveRunner.run` from `List<() -> Unit>` to `List<StartupTask>`, exposing each task's
  component and a public constructor for runner tests.
- Enforce skipped-task and swallowed-failure checks, reject repeated invocation, and retain
  successful products from failed waves.
- Reject engine lookups made by wave tasks on worker threads instead of waiting indefinitely.
  Allow installing-thread reads of earlier-wave results and reject current-wave lookups.
- Add `initializeComponentOrNull` for runtime keys and nullable products; give typed null-product
  reads a named `StartupException`.
- Add the Kotlin `CoroutineInitializer` blocking bridge for Android, JVM, and native targets.
- Export fallible API calls to Swift with `@Throws(StartupException::class)`; Swift callers now
  need `try`.

## 2.1.0

- Require factories to construct exactly the class named by their registration key.
- Add `WaveRunner` and `Startup.install(context, manifest, runner)` for non-Android wave execution.

## 2.0.0

- Remove the eight APIs deprecated in 1.1.0: initializer state/order/manifest accessors, Android
  metadata and drift helpers, and the Objective-C class-object key overload. See the
  [replacement table](docs/migration.md#from-1x-to-200).

## 1.1.0

- Deprecate the eight APIs removed in 2.0.0, with warnings and migration alternatives.

## 1.0.0

- Introduce the multiplatform startup API, with AndroidX aliases on Android, a portable initializer
  manifest, and deterministic dependency planning for non-Android targets.

# Startup Wave Execution Specification

## Purpose

Define host scheduling and failure boundaries for the non-Android runtime and the public task abstraction. A runner controls scheduling; supplying one does not guarantee concurrency.

## Requirements

### Requirement: Complete each wave before continuing

The engine SHALL supply one planned wave at a time. A runner MUST invoke each task once and wait for all invoked tasks before returning or throwing. The engine SHALL reject unfinished tasks when a runner returns, preserve successful products when a wave fails, and publish current-wave products only after the runner exits.

#### Scenario: Runner skips a task
- **WHEN** a runner returns without completing a supplied task
- **THEN** installation fails identifying that task rather than caching a nonexistent product.

#### Scenario: Successful sibling of a failed task
- **WHEN** a wave contains completed successful and failing tasks
- **THEN** the failure is reported and successful siblings remain cached for future requests.

Tests: [WaveContractTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/WaveContractTest.kt) (`reportsARunnerThatSkippedATask`, `keepsComponentsThatSucceededBesideOneThatFailed`); [StartupRuntimeTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupRuntimeTest.kt) (`handsEachWaveToTheRunnerWithIndependentComponentsGrouped`).

### Requirement: Claim each task once

A task SHALL execute its body at most once, including concurrent invocation. Duplicate invocations SHALL throw and remain recorded even when caught or followed by successful completion of the first invocation.

#### Scenario: Caught duplicate races with completion
- **WHEN** a duplicate invocation is caught while the first body is running and the first body later succeeds
- **THEN** the body runs once and the duplicate violation remains observable by the engine.

Tests: [StartupTaskClaimTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupTaskClaimTest.kt); [StartupTaskDuplicateRaceTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupTaskDuplicateRaceTest.kt); [StartupTaskFailureContractTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupTaskFailureContractTest.kt).

### Requirement: Preserve structured task failures

Task body errors SHALL be exposed as `StartupException`, preserving existing instances and ordinary errors as causes. The declared task export exception SHALL remain `StartupException`. Returning after recorded failures SHALL fail installation. For ordinary recorded failures, returning or propagating the first task wrapper SHALL identify all failing component keys and retain an original body cause.

#### Scenario: Runner catches task errors
- **WHEN** a runner catches task errors and returns after completing the wave
- **THEN** the engine reports recorded failures, including their component keys and an original body cause.

Tests: [StartupTaskFailureTest](../../../startup/src/commonTest/kotlin/io/github/kunal26das/startup/StartupTaskFailureTest.kt); [StartupTaskFailureContractTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupTaskFailureContractTest.kt); [StartupTaskClaimTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupTaskClaimTest.kt); [StartupTaskDuplicateRaceTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupTaskDuplicateRaceTest.kt); [StartupTaskFailureAttributionTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupTaskFailureAttributionTest.kt); [StartupTaskExceptionExportTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupTaskExceptionExportTest.kt). Swift runners can catch task errors even though the runner protocol itself cannot throw.

### Requirement: Restrict engine access during installation

During installation, worker tasks SHALL be refused access to the installing engine. The installing thread SHALL allow acyclic resolution outside the current wave and refuse current-wave members. Ordinary callers not running wave tasks SHALL wait and be served after installation. Tasks MUST NOT forward engine lookups to another thread; the guard does not guarantee general deadlock detection.

#### Scenario: Worker reads a completed dependency
- **WHEN** a worker task asks the installing engine for a dependency completed in an earlier wave
- **THEN** the lookup fails promptly instead of waiting for the installation lock.

#### Scenario: Installing-thread lookup
- **WHEN** a task on the installing thread requests an earlier product or an independent lazy component
- **THEN** the lookup succeeds; requesting itself or a current-wave sibling is refused even if that sibling's task body has finished, without inventing a cycle between siblings.

#### Scenario: Ordinary caller waits
- **WHEN** a thread that is not running a wave task requests a component while installation owns the engine
- **THEN** it waits until installation releases the engine and is then served.

Tests: [StartupBarrierTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupBarrierTest.kt); [WaveDeclaredDependencyTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/WaveDeclaredDependencyTest.kt); [WaveSiblingTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/WaveSiblingTest.kt); [StartupLockTest](../../../startup/src/nativeTest/kotlin/io/github/kunal26das/startup/StartupLockTest.kt). Source evidence: [StartupLock](../../../startup/src/desktopMain/kotlin/io/github/kunal26das/startup/StartupLock.kt); [StartupLock](../../../startup/src/nativeMain/kotlin/io/github/kunal26das/startup/StartupLock.kt).

The engine does not join application work that violates the runner's completion obligation.

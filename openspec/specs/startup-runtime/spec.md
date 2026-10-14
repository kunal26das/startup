# Startup Runtime Specification

## Purpose

Define creation, caching, composition, and failures in the non-Android runtime. Android delegates to AndroidX as specified by the interoperability capability.

## Requirements

### Requirement: Create on demand and reuse successful products

Installation SHALL create eager roots and their dependencies, deferring unrelated lazy entries until requested. Successful products SHALL be reused across lookups and installs, including concurrent callers on thread-capable targets. Failed creation may be retried.

#### Scenario: Repeated or concurrent lookup
- **WHEN** multiple callers request the same registered component
- **THEN** its successful creation occurs once and all callers receive that product.

Tests: [StartupRuntimeTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupRuntimeTest.kt) (`doesNotCreateLazyComponentsUntilAsked`, `reusesComponentsInsteadOfCreatingThemTwice`); [StartupConcurrencyTest](../../../startup/src/desktopTest/kotlin/io/github/kunal26das/startup/StartupConcurrencyTest.kt); [StartupLockTest](../../../startup/src/nativeTest/kotlin/io/github/kunal26das/startup/StartupLockTest.kt); [NullProductTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/NullProductTest.kt). Source evidence: [StartupEngine](../../../startup/src/nonAndroidMain/kotlin/io/github/kunal26das/startup/StartupEngine.kt).

### Requirement: Represent null products explicitly

A successful null product SHALL be cached and readable through `initializeComponentOrNull`. A typed non-null read SHALL fail identifying the component. The nullable read SHALL NOT convert missing registrations or initialization failures into null.

#### Scenario: Null product
- **WHEN** a registered initializer returns null
- **THEN** installation and the nullable read succeed while a typed read reports the invalid non-null expectation.

Tests: [NullProductTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/NullProductTest.kt). Source evidence: [StartupEngine](../../../startup/src/nonAndroidMain/kotlin/io/github/kunal26das/startup/StartupEngine.kt).

### Requirement: Compose successive and nested installations

Each install SHALL merge registrations with later entries winning, retain created products, and start only its supplied eager roots and their dependencies. Acyclic installs from `create` on the installing thread SHALL be permitted. Initializers constructed for planning SHALL remain shared across nested calls and be discarded after the outermost operation, allowing later factory replacements for uncreated components.

#### Scenario: Install a feature from an initializer
- **WHEN** an initializer installs an independent feature manifest from `create`
- **THEN** the feature roots are created without falsely treating the enclosing initializer as its own dependency.

#### Scenario: Recovery after failed installation
- **WHEN** an unrelated manifest is installed after an earlier eager component failed
- **THEN** that earlier component is not automatically retried; a later factory replacement for a still-uncreated component is honored when it is requested again.

Tests: [StartupRuntimeTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupRuntimeTest.kt) (`composesSuccessiveInstalls`, `installsAManifestFromInsideCreate`, `leavesAnEarlierInstallsEagerComponentsToThatInstall`, `letsALaterInstallReplaceAComponentAFailedInstallLeftUncreated`, `keepsTheOuterPlansInitializersWhenDependenciesResolvesSomething`).

### Requirement: Preserve successful state and attribute failures

Ordinary creation failures SHALL become `StartupException` with the original cause and failing key. Existing structured startup failures SHALL preserve their diagnostic; failures without component attribution SHALL gain the current component. Successful products SHALL remain cached after a later creation fails. Imperative lookups closing a creation cycle SHALL fail with the actual cycle path rather than recurse or invent adjacency between nested operations.

#### Scenario: Dependent fails after a successful dependency
- **WHEN** sequential creation reaches a dependent that throws
- **THEN** the install fails with that component and original cause while the successful dependency remains cached.

#### Scenario: Nested lookups close a cycle
- **WHEN** nested creation reaches an active component through declared intermediary dependencies
- **THEN** the failure identifies the real path including those intermediary components.

Tests: [StartupRuntimeTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupRuntimeTest.kt) (`wrapsAFailureAndStopsTheRun`, `namesTheComponentBehindAStartupExceptionThatNamesNone`, `rejectsAnEdgeBackIntoAComponentBeingCreated`, `namesEveryHopOfACycleThatClosesBelowTheComponentAskedFor`, `namesTheComponentThatLinksTwoNestedCreateCalls`).

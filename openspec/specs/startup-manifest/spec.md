# Startup Manifest Specification

## Purpose

Define the shared registry's component identity, composition, and registration checks. The manifest is portable data; Android runtime delegation is specified separately.

## Requirements

### Requirement: Ordered registration and composition

A manifest SHALL distinguish eager, lazy, and removed entries. Later declarations, included manifests, and right-hand composition entries SHALL override earlier entries for the same key while preserving its first registration position. Runtime-key and reified registration SHALL identify the same component. Removed entries SHALL be excluded from membership, registered-component, and eager-component queries.

#### Scenario: Override using a runtime key
- **WHEN** an eager registration is replaced by a lazy registration using an equivalent runtime key
- **THEN** one component remains registered in its original position and it is no longer eager.

#### Scenario: Remove an included component
- **WHEN** an application includes a library manifest and then removes one of its components
- **THEN** that component is absent from registered and eager entries.

Source evidence: [StartupManifest](../../../startup/src/commonMain/kotlin/io/github/kunal26das/startup/StartupManifest.kt); [StartupManifestBuilder](../../../startup/src/commonMain/kotlin/io/github/kunal26das/startup/StartupManifestBuilder.kt). Tests: [StartupManifestTest](../../../startup/src/commonTest/kotlin/io/github/kunal26das/startup/StartupManifestTest.kt); [StartupManifestBuilderTest](../../../startup/src/commonTest/kotlin/io/github/kunal26das/startup/StartupManifestBuilderTest.kt) (`laterEntriesWin`, `removeHidesAnIncludedEntry`, `aRuntimeKeyOverridesAReifiedEntry`).

### Requirement: Resolvable and correctly keyed entries

Shared planning SHALL reject an unsatisfied required component that is missing, removed, or constructed by a factory returning a different initializer class. Failures SHALL identify the involved keys using `StartupException.components` and distinguish removal from missing registration. This applies to planning on every platform and to installation off Android; Android installation does not invoke these factories.

#### Scenario: Removed dependency
- **WHEN** a reached component declares a dependency hidden by a removal
- **THEN** planning fails with a removal diagnostic identifying the dependency and its dependent.

#### Scenario: Mismatched factory
- **WHEN** a reached registration's factory returns a different initializer class from its key
- **THEN** planning fails identifying both keys rather than storing that initializer under the wrong key.

Source evidence: [StartupManifest](../../../startup/src/commonMain/kotlin/io/github/kunal26das/startup/StartupManifest.kt). Tests: [StartupPlannerTest](../../../startup/src/commonTest/kotlin/io/github/kunal26das/startup/StartupPlannerTest.kt) (`reportsAMissingRegistration`); [StartupRuntimeTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupRuntimeTest.kt) (`reportsATombstonedDependencyAsRemovedRatherThanMissing`, `rejectsAFactoryThatProducesADifferentComponent`).

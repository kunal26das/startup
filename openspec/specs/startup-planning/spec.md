# Startup Planning Specification

## Purpose

Define the shared planner's ordering and diagnostics, including its use by nested non-Android runtime operations. AndroidX runtime ordering is outside this capability.

## Requirements

### Requirement: Deterministic dependency order and waves

For the same ordered roots, registrations, and dependency lists, planning SHALL produce the same dependency-first order. It SHALL include each unsatisfied reachable component once, collapse duplicate dependency edges, and exclude satisfied components. A wave's dependencies SHALL already be satisfied or belong to earlier waves, and flattening `waves` SHALL equal `order`.

#### Scenario: Diamond graph
- **WHEN** two branches share a dependency and a root depends on both branches
- **THEN** the shared dependency occurs once, followed by the branch wave, followed by the root.

#### Scenario: Already satisfied graph
- **WHEN** every requested root is satisfied
- **THEN** the plan is empty; a partially satisfied graph omits satisfied components without inventing a cycle.

Tests: [StartupPlannerTest](../../../startup/src/commonTest/kotlin/io/github/kunal26das/startup/StartupPlannerTest.kt) (`isDeterministic`, `seedsTheReadyQueueInDeclarationOrder`, `groupsADiamondIntoThreeWaves`, `groupsExactlyTheComponentsItOrders`, `collapsesDuplicateEdges`, `skipsSatisfiedComponents`, `plansNothingWhenEverythingIsSatisfied`).

### Requirement: Graph inspection and cycle diagnostics

Planning SHALL construct reached initializer factories and read dependencies without itself calling `create`. Validation SHALL inspect all registered components, including lazy entries. A declared cycle SHALL raise `StartupException` whose `components` contains the real closed cycle with the first key repeated at the end and any acyclic approach path excluded. Application callbacks may themselves perform work; planning does not guarantee that they are side-effect-free.

#### Scenario: Entry point leads into a cycle
- **WHEN** the graph contains `Entry -> Head -> Tail -> Head`
- **THEN** the cycle path is `Head -> Tail -> Head`.

#### Scenario: Lazy-only cycle
- **WHEN** validation receives a cyclic graph containing only lazy entries
- **THEN** validation fails even though the manifest has no eager roots.

Source evidence: [StartupPlanner](../../../startup/src/commonMain/kotlin/io/github/kunal26das/startup/StartupPlanner.kt). Tests: [StartupPlannerTest](../../../startup/src/commonTest/kotlin/io/github/kunal26das/startup/StartupPlannerTest.kt) (`reportsSelfDependencyAsACycle`, `reportsACycleFromThePointOfReEntry`, `validateRejectsACyclicManifest`).

### Requirement: Guard nested runtime planning callbacks

Within one non-Android engine operation, reentry into an active factory or dependency callback SHALL fail before invoking it again. Cycle diagnostics SHALL preserve real declared intermediary hops. Reentry during a nested wave SHALL report the involved component without inventing edges between unrelated wave members. Acyclic nested resolution SHALL remain valid, and failure SHALL release callback guards for later operations.

#### Scenario: Callback reenters itself
- **WHEN** a factory or dependency callback starts a nested lookup that reaches that active callback again
- **THEN** the engine raises a structured startup failure before another invocation recurses.

#### Scenario: Acyclic lookup and recovery
- **WHEN** a callback resolves an independent registered component, or a corrected graph is retried after a callback failure
- **THEN** resolution succeeds without duplicate initializer construction within the operation or stale callback guards.

Source evidence: [StartupPlanning](../../../startup/src/commonMain/kotlin/io/github/kunal26das/startup/StartupPlanning.kt). Tests: [StartupPlanningReentryTest](../../../startup/src/nonAndroidTest/kotlin/io/github/kunal26das/startup/StartupPlanningReentryTest.kt) (`rejectsARepeatedFactory`, `rejectsARepeatedDependencyCallback`, `preservesDeclaredHopsBetweenCallbacks`, `refusesAnEnclosingCallbackFromANestedWave`, `clearsCallbacksAfterFailure`, `preservesAcyclicReentryAndInstanceCaching`). This engine-scoped guarantee does not cover recursive calls creating independent standalone planner contexts.

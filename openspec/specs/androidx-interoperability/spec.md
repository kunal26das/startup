# AndroidX Interoperability Specification

## Purpose

Preserve AndroidX type compatibility and Android ownership of reflection, startup, and XML discovery without implying identical execution rules on every platform.

## Requirements

### Requirement: Preserve AndroidX type and reflection compatibility

On Android, `Initializer`, `AppInitializer`, `Context`, and `InitializerKey` SHALL remain aliases to AndroidX or platform types rather than wrappers. Shared initializer bytecode SHALL preserve AndroidX's dependency-list signature. An initializer used through AndroidX reflection MUST be a public no-argument class. Runtime keys SHALL resolve to the corresponding Java class tokens.

#### Scenario: Shared initializer is reflected
- **WHEN** Android reflects a shared or platform-actual initializer's public no-argument constructor
- **THEN** the instance implements `androidx.startup.Initializer` and exposes its compatible dependency signature.

#### Scenario: Runtime key matches AndroidX identity
- **WHEN** a key is obtained from an initializer instance or its Kotlin class
- **THEN** it is the Java class token used by AndroidX.

Source evidence: [Initializer](../../../startup/src/androidMain/kotlin/io/github/kunal26das/startup/Initializer.kt); [AppInitializer](../../../startup/src/androidMain/kotlin/io/github/kunal26das/startup/AppInitializer.kt); [Context](../../../startup/src/androidMain/kotlin/io/github/kunal26das/startup/Context.kt); [InitializerKey](../../../startup/src/androidMain/kotlin/io/github/kunal26das/startup/InitializerKey.kt). Tests: [AndroidInitializerContractTest](../../../startup/src/androidHostTest/kotlin/io/github/kunal26das/startup/AndroidInitializerContractTest.kt) (`aSharedInitializerImplementsTheAndroidxInterface`, `theDependenciesSignatureIsTheAndroidxOne`, `aSharedInitializerHasAPublicNoArgumentConstructor`, `aKeyIsTheAndroidxClassToken`, `anInstanceKeyIsTheAndroidxClassToken`).

### Requirement: Delegate Android startup and retain XML ownership

Android installation SHALL ask AndroidX to initialize the supplied manifest's eager keys and SHALL ignore Kotlin factories and a custom runner. Provider XML SHALL remain the source of automatic startup and eager-discovery reporting; Kotlin manifests SHALL NOT generate, reconcile, or undo XML registrations. A Kotlin removal SHALL NOT prevent AndroidX from reflecting a component requested directly or as a dependency. Independent component order need not equal the non-Android planner's order.

#### Scenario: Manual install with a custom runner
- **WHEN** shared code supplies eager registrations and a runner on Android
- **THEN** AndroidX constructs the keyed classes and follows their dependencies without invoking the runner or Kotlin factories; programmatic installation alone does not mark a component discovered eagerly in XML.

#### Scenario: Keep the sample's two registries aligned
- **WHEN** the sample's shared eager roots and Android metadata entries differ
- **THEN** the sample's parity test fails rather than relying on library reconciliation.

Source evidence: [Startup](../../../startup/src/androidMain/kotlin/io/github/kunal26das/startup/Startup.kt); [Android guide](../../../docs/android.md). Tests: [AndroidManifestParityTest](../../../sample/src/androidHostTest/kotlin/io/github/kunal26das/startup/sample/AndroidManifestParityTest.kt) (`theManifestDeclaresExactlyTheEagerComponents`, `theManifestMergesIntoTheAndroidxProvider`). Android host tests establish bytecode, reflection, and source-XML parity; they do not claim device execution of the provider or the installation scenarios above.

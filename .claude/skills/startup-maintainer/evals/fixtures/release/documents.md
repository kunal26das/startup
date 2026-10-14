# Fixture document excerpts

These excerpts represent the application-facing documentation before the next release.

## startup/build.gradle.kts

```kotlin
val artifactVersion = "4.1.7"
```

## README.md

```kotlin
implementation("io.github.kunal26das:startup:4.1.7")
```

Dependency examples use **4.1.7**. This repository builds with Kotlin 2.4.20.

## docs/swift.md

```kotlin
api("io.github.kunal26das:startup:4.1.7")
export("io.github.kunal26das:startup:4.1.7")
```

## site/index.html

```html
<code>implementation("io.github.kunal26das:startup:4.1.7")</code>
```

## CONTRIBUTING.md

| Tool | Version |
| --- | --- |
| Kotlin | 2.4.20 |
| Gradle | 9.8.0 |
| JDK | 21 |

## docs/migration.md

### From 3.0.1 to 4.0.0

```kotlin
implementation("io.github.kunal26das:startup:4.0.0")
```

### From 4.0.0 or 4.0.1 to 4.0.2

Update the dependency to `io.github.kunal26das:startup:4.0.2`. An unrelated later
install does not retry eager components from an earlier failed installation.

## CHANGELOG.md

### 4.1.7

The public Kotlin and Swift API is unchanged. This release builds with Kotlin 2.4.20.

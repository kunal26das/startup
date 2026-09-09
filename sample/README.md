# Run the sample

The sample initializes a logger, network client, analytics client, crash reporter, and runtime
identifier from shared Kotlin code. Android starts them through AndroidX's manifest provider; the
other targets use the library's runtime. The services are small stand-ins, so no SDK accounts or
API keys are needed.

Complete the [repository setup](../CONTRIBUTING.md#set-up-the-repository), then run commands from
the **repository root**, not this directory. Use `.\gradlew.bat` in Windows PowerShell wherever a
command below uses `./gradlew`.

## Start with the JVM

```sh
./gradlew :sample:desktopRun
```

This prints the report and exits. It is the shortest path to see the complete shared graph without
starting an emulator or simulator.

## Run on your platform

| Target | Command | Requirement |
| --- | --- | --- |
| JVM | `./gradlew :sample:desktopRun` | Repository setup. |
| Android | `./gradlew :androidApp:installDebug` | A connected emulator or device running Android API 26 or newer; launch **App Startup sample** afterward. |
| macOS arm64 | `./gradlew :sample:runDebugExecutableMacosArm64` | Apple Silicon Mac and Xcode. |
| macOS x86-64 | `./gradlew :sample:runDebugExecutableMacosX64` | Intel Mac and Xcode. |
| Linux x86-64 | `./gradlew :sample:runDebugExecutableLinuxX64` | Linux x86-64 host. |
| Windows x86-64 | `.\gradlew.bat :sample:runDebugExecutableMingwX64` | Windows x86-64 host. |
| Kotlin/JS in Node | `./gradlew :sample:jsNodeRun` | Gradle-managed JavaScript tooling. |
| Kotlin/Wasm in Node | `./gradlew :sample:wasmJsNodeRun` | Gradle-managed JavaScript tooling. |
| Kotlin/JS in a browser | `./gradlew :sample:jsBrowserRun` | A browser; see below. |
| Kotlin/Wasm in a browser | `./gradlew :sample:wasmJsBrowserRun` | A browser compatible with Kotlin/Wasm; see below. |
| iOS arm64 simulator | `./gradlew :sample:iosSimulatorApp` | Apple Silicon Mac, Xcode, and an installed arm64 simulator; install and launch as below. |

Native console tasks print the report and exit. Android and iOS display it on screen as well as
logging it. Android's output uses the `StartupSample` Logcat tag.

### Browser samples

Start one browser task, then open [localhost:8080](http://localhost:8080/). The page mirrors the
console report into the document. Keep the command running while using the page and press Ctrl-C
to stop it. Both browser tasks use port 8080, so run them one at a time.

The Node and browser variants share their platform implementation: Kotlin/JS reports
`BrowserCrashReporter` even in Node, and Kotlin/Wasm reports `WasmCrashReporter` in either
environment. Use the command and environment you launched to identify what ran.

### iOS simulator

Build the app bundle and list the simulators installed with your Xcode:

```sh
./gradlew :sample:iosSimulatorApp
xcrun simctl list devices available
```

Choose an available arm64 simulator and replace the value below with its UDID. If it is already
booted, skip the `boot` command.

```sh
STARTUP_SIMULATOR_UDID="YOUR-SIMULATOR-UDID"
xcrun simctl boot "$STARTUP_SIMULATOR_UDID"
xcrun simctl bootstatus "$STARTUP_SIMULATOR_UDID" -b
xcrun simctl install "$STARTUP_SIMULATOR_UDID" sample/build/iosApp/SampleApp.app
xcrun simctl launch --console "$STARTUP_SIMULATOR_UDID" io.github.kunal26das.startup.sample.app
```

The helper assembles and ad-hoc signs an `iosSimulatorArm64` app. It does not package an `iosX64`
simulator app or deploy to a physical iPhone. Those targets require their own compatible runtime
or device packaging and signing workflow; linking their executables is not a successful launch.

### Optional execution through translation

Use the native-host commands above when that host is available. Cross-linked artifacts can also
be exercised in a suitable compatibility environment:

- On Apple Silicon, the macOS x86-64 executable can run through installed Rosetta. Link it with
  `./gradlew :sample:linkDebugExecutableMacosX64`, then run
  `arch -x86_64 ./sample/build/bin/macosX64/debugExecutable/sample.kexe`.
- A Docker environment that supports `linux/amd64` can run the Linux executable. After linking
  with `./gradlew :sample:linkDebugExecutableLinuxX64`, run:

  ```sh
  docker run --rm --platform linux/amd64 \
    --mount type=bind,source="$PWD",target=/workspace,readonly \
    ubuntu:24.04 /workspace/sample/build/bin/linuxX64/debugExecutable/sample.kexe
  ```

- Link the Windows executable with `./gradlew :sample:linkDebugExecutableMingwX64`. A configured
  Wine64 environment can execute `sample/build/bin/mingwX64/debugExecutable/sample.exe`.

These are optional environment-specific checks. Wine does not establish native Windows OS
coverage, and translation does not establish execution on physical x86-64 hardware.

## What to look for

The report includes these sections:

```text
kotlin multiplatform app startup sample

registered components
initialization order
crash reporting sdk
startup runtime
logger after analytics.track("launch")
```

Analytics, crash reporting, and runtime information are registered eagerly. Logger and network
are registered lazily but are created at startup because eager components depend on them. The log
ends with `track launch` and `GET /events/launch`.

Every dependency must finish before its dependent. Independent components may appear in different
orders: AndroidX uses a depth-first traversal, while the non-Android planner groups dependency
levels. The runtime section reads `androidx.startup` on Android and `io.github.kunal26das.startup`
elsewhere. Android also shows:

```text
started by androidx.startup.InitializationProvider: true
```

`RuntimeInfoInitializer` has no logger dependency, so it appears in the runtime section rather
than the logged initialization order. Crash reporter names vary by target: `JvmCrashReporter`,
`AppleCrashReporter`, `LinuxCrashReporter`, `WindowsCrashReporter`, `BrowserCrashReporter`,
`WasmCrashReporter`, or `AndroidCrashReporter(...)`.

## Explore the code

| File or directory | What it demonstrates |
| --- | --- |
| [SampleStartup.kt](src/commonMain/kotlin/io/github/kunal26das/startup/sample/SampleStartup.kt) | The shared manifest and typed component reads. |
| [AnalyticsInitializer.kt](src/commonMain/kotlin/io/github/kunal26das/startup/sample/AnalyticsInitializer.kt) | An initializer written once for every platform. |
| [CrashReportingInitializer.kt](src/commonMain/kotlin/io/github/kunal26das/startup/sample/CrashReportingInitializer.kt) | An `expect` initializer with platform-specific implementations. |
| [RuntimeInfoInitializer.kt](src/commonMain/kotlin/io/github/kunal26das/startup/sample/RuntimeInfoInitializer.kt) | An `expect` initializer extending `BaseInitializer`. |
| [SampleReport.kt](src/commonMain/kotlin/io/github/kunal26das/startup/sample/SampleReport.kt) | The report shared by every entrypoint. |
| [AndroidManifest.xml](src/androidMain/AndroidManifest.xml) | Eager discovery through AndroidX's provider. |
| [androidApp](../androidApp/) | The Android application that consumes the sample library. |
| [consoleMain](src/consoleMain/) and [iosMain](src/iosMain/) | Console and UIKit entrypoints. |

When adding an eager sample component, update both the shared manifest and Android XML. Keep
initializers constructible through a public no-argument constructor so AndroidX can create them.
See [Contributing](../CONTRIBUTING.md) for focused tests and platform verification.

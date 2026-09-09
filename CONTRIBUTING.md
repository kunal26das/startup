# Contributing

Thanks for helping improve startup. Start with the local checks below, then add the platform checks
that exercise your change. You do not need release credentials or a signing key to build and test.

## Set up the repository

```sh
git clone https://github.com/kunal26das/startup.git
cd startup
```

The repository currently uses:

| Tool | Version | Setup |
| --- | --- | --- |
| JDK | 21 | Make Java available to the wrapper; the build also requests a Java 21 toolchain. |
| Gradle | 9.7.1 | Use the checked-in wrapper. |
| Kotlin | 2.4.10 | Resolved by Gradle. |
| Android Gradle Plugin | 9.4.0 | Resolved by Gradle. |
| Android SDK platform | 37 | Install it and Platform-Tools through the Android SDK Manager. |

Versions come from [the version catalog](gradle/libs.versions.toml),
[the wrapper configuration](gradle/wrapper/gradle-wrapper.properties), and the module build scripts.
Set `ANDROID_HOME` to your SDK directory, or add `sdk.dir=/absolute/path/to/your/sdk` to an untracked
`local.properties` file. The project includes Android modules even when you are working on JVM code.
Allow network access for the first build to download Gradle, dependencies, and target tooling.

Apple builds also need macOS and a compatible Xcode installation; simulator execution needs an
installed runtime for the target architecture. Browser tests use ChromeHeadless and need a
Chrome/Chromium binary accessible to Karma; set `CHROME_BIN` if it cannot find the executable.
Node tests do not verify browser execution.

Commands below run from the repository root. On Windows PowerShell, replace `./gradlew` with
`.\gradlew.bat`; for example:

```powershell
.\gradlew.bat :startup:desktopTest :sample:desktopTest
```

## Get a quick result

Run the library and sample JVM suites first:

```sh
./gradlew :startup:desktopTest :sample:desktopTest
```

Check shared metadata explicitly, especially after changing public APIs or `expect`/`actual` classes:

```sh
./gradlew :startup:compileCommonMainKotlinMetadata :sample:compileCommonMainKotlinMetadata
```

A successful platform compilation does not prove that shared metadata compiles. Tests also do not
replace this check. To see the library working in an application, follow the
[sample guide](sample/README.md).

## Platform verification

Choose the checks that exercise your change. Unqualified task selectors below run matching tasks
in both `startup` and `sample`.

| Area | Command | Host or extra requirement |
| --- | --- | --- |
| Shared graph, runtime, or sample behavior | `./gradlew desktopTest` | JDK and repository setup above. |
| Android aliases, bytecode contracts, or manifest entries | `./gradlew testAndroidHostTest :startup:checkAndroidFloors` | Android SDK; these are host tests, not device tests. |
| Kotlin/JS and Kotlin/Wasm | `./gradlew jsNodeTest wasmJsNodeTest` | Gradle-managed JavaScript tooling. |
| Browser execution | `./gradlew jsBrowserTest wasmJsBrowserTest` | Chrome/Chromium accessible to Karma. |
| Apple Silicon macOS and iOS simulator | `./gradlew macosArm64Test iosSimulatorArm64Test` | Apple Silicon Mac, Xcode, and an arm64 simulator runtime. |
| Intel macOS | `./gradlew macosX64Test` | An x86-64 macOS execution environment. |
| Intel iOS simulator | `./gradlew iosX64Test` | An x86-64 simulator runtime; current installed runtimes may not provide one. |
| Linux native runtime | `./gradlew linuxX64Test` | Linux x86-64. |
| Windows native runtime | `.\gradlew.bat mingwX64Test` | Windows x86-64. |
| Swift / Objective-C export | `./gradlew :startup:checkObjCExport :sample:checkConsumerObjCExport` | macOS and Xcode. |

For a broad build on a suitably configured host:

```sh
./gradlew build
```

This also compiles metadata, builds sample artifacts, and runs verification tasks available on that
host. It can need browser tooling and substantial first-build downloads. A successful build can
include skipped or disabled targets: inspect the task results before claiming runtime coverage.
Linking `linkDebugTestLinuxX64`, `linkDebugTestMingwX64`, or an Apple test binary proves it builds,
not that its tests executed. Physical-device iOS execution needs device setup and signing beyond
the simulator helper supplied here.

[CI](.github/workflows/build.yml) splits verification across macOS, Linux, and Windows. Match the
relevant jobs when preparing a pull request; you are not expected to own every host. Include the
commands you ran and note platforms you could not execute. Reports are under each module's
`build/reports/tests` directory.

## Find the right source set

| Location | Purpose |
| --- | --- |
| `startup/src/commonMain` | Public API, manifests, planner, and shared task behavior. |
| `startup/src/androidMain` | AndroidX aliases and Android-specific bridges. |
| `startup/src/nonAndroidMain` | The runtime shared by the other targets. |
| `startup/src/{desktop,native,js,wasmJs}Main` | Locks, thread state, and blocking support. |
| `startup/src/{apple,linux,mingw}Main` | Native platform helpers. |
| `startup/src/commonTest` | Shared planner, manifest, and task tests. |
| `startup/src/nonAndroidTest` | Runtime tests shared by non-Android targets. |
| `startup/src/desktopTest` | Threading, coroutine, and concurrency regression tests. |
| `startup/src/androidHostTest` | Android bytecode and interface contracts. |
| `sample/` | Consumer examples, platform entrypoints, and sample tests. |
| `androidApp/` | The launchable Android app that consumes `sample`. |

## Prepare a contribution

- Add a regression test for a behavior fix, exercising the failure and the intended result. Prefer
  tests of observable behavior over tests that mirror implementation details.
- Put portable tests in the shared test source set that can run them; keep thread-dependent tests
  in `desktopTest`. When testing the sample's process-wide singleton, assert relative order or
  containment rather than a fresh global log.
- Document every public declaration with KDoc. Follow the existing convention of one top-level
  Kotlin declaration per file, with the filename matching the declaration. Keep required
  `expect`/`actual` facade exceptions together as described in [CLAUDE.md](CLAUDE.md#house-style).
- Use KDoc for code documentation; avoid inline comments, block comments, and commented-out code.
  Keep source compilation free of new warnings.
- Keep sample initializers compatible with AndroidX reflection: public no-argument constructors,
  with eager entries kept in sync between the shared manifest and Android XML.
- Update relevant documentation with API or behavior changes. Explain the resulting behavior and
  validation in the pull request. Release tagging and publishing are separate maintainer steps.

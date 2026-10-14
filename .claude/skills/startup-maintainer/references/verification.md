# Verification

Read `CONTRIBUTING.md` and `.github/workflows/build.yml` for current commands and host setup.
Classify the changed behavior before selecting checks. Unqualified Gradle task selectors can
run both library and sample tasks; preserve their module names in the evidence.

| Changed surface | Useful checks |
| --- | --- |
| Shared graph/runtime behavior | Library and sample `desktopTest`; relevant nonAndroid/common tests on JS, Wasm, and native hosts. |
| Public API or expect/actual shape | Both `:startup:compileCommonMainKotlinMetadata` and `:sample:compileCommonMainKotlinMetadata`, plus affected platform compilation. |
| Android bridge or manifest | `testAndroidHostTest`, `:startup:checkAndroidFloors`; device/emulator launch only when runtime integration needs it. |
| Swift/Objective-C API | `:startup:checkObjCExport` and `:sample:checkConsumerObjCExport` on macOS with Xcode. |
| Threads, lock, or coroutine behavior | Desktop concurrency tests; relevant native tests on supported native hosts; inspect web refusal behavior where applicable. |
| Website only | `python3 scripts/check-site.py` and Node syntax checks for the affected scripts; visual/manual checks when layout or interaction changes. |

Use a broad `./gradlew build` when warranted, including repository-required checks. Do not
repeat successful suites without new changes or unresolved concerns. A selected-task Gradle
invocation ending in `BUILD SUCCESSFUL` does not mean every repository check ran.

## Coverage claims

- Link evidence to an exact commit/tree, task, module, environment, outcome, and report.
- A linked binary proves linking, not execution. A skipped/disabled task proves neither a fresh
  run nor a passing test. Cached results need provenance before being credited to the change.
- Android host tests check contracts on the host; they are not an Android app launch.
- JS/Wasm Node execution does not establish browser execution. Report browser tests separately.
- An iOS arm64 simulator is not a physical device or an Intel simulator. An Apple Silicon build
  can skip Intel Apple, Linux, and Windows execution. Read actual task outcomes.
- Same-source Linux/Windows CI execution can fill local host gaps. A green old-SHA run cannot.
- Wine, Rosetta, or Docker translation are useful but name the environment; they do not prove
  execution on the corresponding physical hardware or native operating system.
- Platform compilation does not replace shared metadata compilation. Verify both library and
  sample metadata when public/common API shape is affected.
- Export/header checks verify callable API shape, not application execution. Artifact download,
  checksum, and archive checks verify publication, not runtime behavior.

Produce a small table of executed, compiled/linked, skipped, and unverified coverage when the
user asks about multiple platforms. Recommend only the missing checks relevant to the change.
Do not hide an unavailable host or claim an unexecuted test passed.

---
name: startup-maintainer
description: >-
  Maintain this Startup Kotlin Multiplatform repository: plan behavior or API changes with OpenSpec,
  select and interpret platform verification, prepare dependency or patch releases, recover partial
  publications, and synchronize release documentation. Use for Startup code changes, cross-platform
  testing claims, dependency PR releases, release readiness, or publication recovery; not for
  unrelated projects or general Kotlin questions.
---

# Startup maintainer

Start from the user's requested outcome and existing authorization. Continue already-authorized
work without adding an approval step for every artifact, test, commit, or release phase. Preparing
a release does not itself authorize publication; an explicit publish request does. Ask only when
a material decision or required authorization is genuinely missing. A skill cannot expand scope.

## Establish the source and task

Read applicable repository instructions and inspect the working tree before editing. Preserve
unrelated work. Read `CONTRIBUTING.md`, the affected source sets, and the actual Gradle/workflow
configuration as needed; they remain authoritative. Derive versions from the catalog, wrapper,
and `startup/build.gradle.kts`, rather than memorizing versions in this skill.

Record the source commit for evidence. A successful run for an older commit does not validate the
current tree. For an uncommitted change, identify the base commit and saved diff or tree snapshot.
Keep assumptions, planned checks, executed checks, and published outcomes distinct.

## Route the work

- **Meaningful behavior, public API, concurrency, or cross-platform changes:** inspect current
  OpenSpec state through `node scripts/openspec.mjs`. Use its installed help and generated
  workflow instructions to create or continue the relevant change. Capture observable
  requirements and scenarios, compatibility effects, design choices, implementation tasks,
  and verification. Check the existing implementation before treating a historical bug report
  as a current regression. Apply and verify within the user's scope, then archive completed
  work through the existing workflow. Do not invent a second specification process.
- **Small documentation, configuration, or dependency edits:** edit directly when a proposal
  would add no useful decision record. Still inspect compatibility implications and validate
  the affected surface. Do not turn a typo into a full multiplatform build.
- **Verification or coverage questions:** use [verification.md](references/verification.md).
  Report actual execution and its environment, including skipped or unavailable checks.
- **Release preparation, publication, or recovery:** use [releasing.md](references/releasing.md).
  Preserve immutable coordinates and synchronize current examples without rewriting history.

For code changes, preserve Android's AndroidX delegation, shared API compatibility, and the
documented runtime contracts unless the task intentionally changes them. Put observable
regression tests in the narrowest shared source set that can run them. Describe implications
for existing consumers, including retry, caching, cycle detection, and worker-thread lookups
when affected; do not silently weaken safeguards to make one example pass.

## Deliver evidence

Lead the result with what changed or what the evidence establishes. Give the relevant source
commit, concise verification results, and concrete limitations. Name missing execution as
unverified, not successful. For releases, report each publication surface independently and
link its receipt. Keep transient reports and downloads outside the repository.

The release helpers only inspect, stage, or verify; they do not commit, push, tag, or publish.
Use their `--help` and explicit output directories. `scripts/test_helpers.py` creates disposable
local Git fixtures for offline regressions. The evaluation fixtures under `evals/` are
fictional/recorded inputs for paired skill evaluations, not production release instructions.

# Design

## Context

The project already has authoritative Gradle checks, a broad platform matrix, and detailed API
guides. The new workflow should reuse those checks and retain knowledge from maintenance work.
OpenSpec 1.14.1's lifecycle commands can migrate user-level legacy files, while its versioned
renderers can generate project integrations directly. Anthropic's skill-creator includes scripts,
grading schemas, and a review viewer that need to travel with its entry point.

## Decisions

- Keep Node dependencies private under `tools/ai`, with exact direct versions and an npm lockfile.
- Route OpenSpec CLI calls through a launcher that verifies the installed version, selects a
  repository-local profile, disables telemetry for these commands, and uses noninteractive input.
- Generate the full Codex and Claude workflow surfaces with the pinned package's pure renderers.
  Replace terminal CLI examples with the local launcher. Do not call upstream init/update hooks
  that can migrate user-level files. Internal renderer imports are deliberately version-pinned;
  regeneration tests make upgrades reviewable.
- Store first-party skills under `.agents/skills`; generate equivalent Claude copies. Check
  generated content against its sources so duplicate discovery surfaces do not drift.
- Vendor the complete official skill-creator bundle unmodified, with its license, upstream commit,
  and per-file hashes. Put project/host adaptations in a separate authoring skill.
- Start with five small runtime baselines derived from existing tests. Use delta specs for future
  contract changes, and scale planning to the task instead of requiring proposals for typos.
- Test the maintenance skill with isolated fixtures and independent with-skill and baseline runs.
  Use upstream grading/aggregation/viewer formats. Missing token or timing data stays unavailable;
  a small paired evaluation is not a broad performance claim.
- Add lightweight workflow CI on Linux, macOS, and Windows. Keep existing Kotlin and website checks
  authoritative for their respective changes.

## Verification

Check clean installation and deterministic regeneration, rejection of malformed specifications,
profile isolation, upstream integrity, generated copies, and real evaluation outputs. Validate
the specification change before archiving it. Run existing JVM tests for any corrected runtime
documentation and inspect the tests cited by the baseline specs.

## Tradeoffs

Generated integrations and the vendored creator add files, but fresh clones receive the same
workflow without depending on a maintainer's home directory. The pinned internal renderers require
review when upgrading OpenSpec. Live model evaluations remain an explicit maintenance operation;
CI runs deterministic checks without AI accounts, credentials, or model billing.

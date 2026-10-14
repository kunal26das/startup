# Verification: adopt-agent-workflows

All twelve implementation tasks are complete. Six development-workflow requirements have
implementations and verification evidence; the five runtime baselines describe existing behavior.
No runtime implementation, public API, or artifact version changed.

| Requirement | Implementation and evidence |
| --- | --- |
| Project-local tooling | Exact npm pins and lockfile; launcher version guard; pure renderer; tests for local configuration, rejected migration commands, and symlink/junction write boundaries |
| Traceable specifications | Five test-linked runtime baselines; this proposal/design/delta/tasks cycle; strict validation passes |
| Discoverable project skills | Twelve OpenSpec workflows for both assistants, twelve Claude commands, and automatically mirrored startup-prefixed skills; deterministic generation and drift tests pass |
| Reproducible skill evaluation | Three paired scenarios, isolated baseline inputs, six actual output sets and grades, official aggregator and static viewer; candidate 15/15 and baseline 14/15 expectations |
| Upstream provenance | Complete unmodified Anthropic subtree with Apache license and eighteen hashes; pinned OpenSpec MIT license retained; tamper regression passes |
| Automated workflow validation | CI matrix on Linux, macOS, and Windows; local Node and Python checks, strict specifications, and archive task validation |

## Executed checks

- Clean locked npm installation, generated-file/metadata/link/license/provenance checker, and
  six Node regression tests passed locally on macOS arm64.
- Eight maintenance-helper tests passed with default-encoding warnings treated as errors.
  They cover Unicode Git/Markdown round trips, matching release tags, missing/mismatched tags,
  mocked live wiki verification without network, incorrect publication/toolchain metadata, and
  malformed archives/classes. UTF-8 and LF handling are explicit.
- Cached 4.0.4 evidence verified all twelve publications, tooling metadata, and Android/JVM
  floors without HTTP. Wiki staging reproduced eleven previously validated files. This is
  helper regression evidence, not a new publication or fresh remote-state check.
- Existing desktop tests were rerun: Startup 106 and sample 11, with no failures, errors,
  or skipped test cases. KDoc now matches tested owner-thread lookup and wave behavior, and
  runner examples collect task results before propagating failure.
- All current specifications and the adoption delta passed strict OpenSpec validation.
  Both evaluation planning drafts also passed strict validation in isolated copies using the
  default schema. First-party whitespace checks passed; upstream formatting is preserved.

## Limits

Remote CI results belong to the final pull-request source and should be read there; creating the
matrix is not evidence that another host executed it. The local JVM run does not prove native,
browser, or Android-device execution. Evaluation agents received fixture tasks only, and their
saved activity records are not complete tool traces. Missing timing/token metrics were removed
from the upstream aggregator output, not reported as zero. One run per scenario is a smoke test;
automatic triggering and Claude description optimization were not evaluated. No user feedback
has been collected. The retained comparison and improvement opportunities are recorded in
`tools/ai/evaluations/startup-maintainer-2026-10-14.md`.

A fresh staged checkout with CRLF conversion enabled passed the same tooling checks on macOS.
The install's dependency audit identified GHSA-vfj7-8cjw-p6xm in the OpenSpec glob dependency
chain. No patched braces release was available at verification time; the developer guide records
the advisory and its development-only scope. An incompatible downgrade was not applied.

The implementation follows the design's local-configuration, pinned-provenance, generated-source,
and authorization boundaries. Independent review findings about write boundaries, Windows text
encoding, and release-tag consistency were resolved and covered by regressions. No unresolved
correctness finding remains from those reviews.

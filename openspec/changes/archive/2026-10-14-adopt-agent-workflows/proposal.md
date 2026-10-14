# Proposal

## Why

Startup's platform contracts and release checks are repeatedly reconstructed from documentation,
tests, and temporary release folders. Keep that knowledge in the repository and use OpenSpec and
Anthropic's skill-creator workflow to make future changes and reusable skills easier to verify.

## What Changes

- Pin OpenSpec and provide local launchers and Codex/Claude integrations without changing user-level configuration.
- Capture five existing runtime capabilities from source and regression tests.
- Add a project maintenance skill with reusable verification resources and an authoring skill backed by the official skill-creator bundle.
- Exercise realistic tasks both with and without the maintenance skill, retaining reviewable results.
- Validate specifications, generated integrations, vendored sources, and tooling in CI.
- Correct runner documentation where it disagrees with behavior already covered by tests.

## Capabilities

### New Capabilities

- `agent-workflows`: Reproducible project-local specification and skill workflows, including validation and evaluation evidence.

### Modified Capabilities

None. The five runtime specifications document existing behavior rather than changing it.

## Impact

Developer tooling, repository instructions, documentation, specifications, and CI change.
The Kotlin library API, runtime implementation, dependency coordinates, and release version do not change.
Node.js is needed only for this optional development workflow, not by library consumers or Gradle builds.

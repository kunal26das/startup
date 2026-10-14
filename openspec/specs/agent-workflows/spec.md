# agent-workflows Specification

## Purpose
Keep repository specifications and project skills reproducible, reviewable, and useful across
contributor hosts while preserving user configuration and reporting verification honestly.

## Requirements

### Requirement: Project-local tooling
The repository SHALL provide a pinned OpenSpec launcher and integration generator that preserve user-level agent configuration.

#### Scenario: Set up a fresh clone
- **WHEN** a contributor installs the locked development dependencies and runs the setup command
- **THEN** the repository receives its configured Codex and Claude integrations
- **AND** the contributor's global OpenSpec profile and legacy agent prompts are not modified

#### Scenario: Use an unexpected CLI version
- **WHEN** the installed OpenSpec version differs from the project's declared version
- **THEN** the launcher refuses to run it and explains how to restore the locked dependency

### Requirement: Traceable specifications
The repository SHALL record observable capability requirements and concrete scenarios, distinguishing existing contracts from proposed changes.

#### Scenario: Plan a meaningful behavior change
- **WHEN** a contributor changes an existing runtime contract
- **THEN** the change identifies affected capabilities and scenario updates alongside implementation and verification tasks
- **AND** accepted user scope continues without an additional approval gate created by the workflow itself

### Requirement: Discoverable project skills
The repository SHALL expose project maintenance and skill-authoring guidance to Codex and Claude from synchronized sources.

#### Scenario: Regenerate after editing a skill
- **WHEN** a first-party skill is edited and integrations are regenerated
- **THEN** both assistants receive equivalent project guidance
- **AND** validation detects a stale generated copy

### Requirement: Reproducible skill evaluation
Project skill evaluations SHALL preserve prompts, fixtures, actual outputs, and evidence-backed grades for with-skill and baseline runs.

#### Scenario: Compare a new maintenance skill
- **WHEN** the maintenance skill is evaluated on realistic project tasks
- **THEN** its baseline runs do not receive the candidate skill
- **AND** the results include a reviewable comparison without presenting missing measurements as zero or synthetic fixture results as live publication evidence

### Requirement: Upstream provenance
The official skill-creator bundle SHALL retain its license, exact source revision, and verifiable file hashes.

#### Scenario: Detect an altered upstream file
- **WHEN** a vendored file no longer matches its recorded hash
- **THEN** validation fails so the change can be reviewed rather than silently treated as the pinned upstream version

### Requirement: Automated workflow validation
CI SHALL validate specifications and workflow tooling independently of model services or publication credentials.

#### Scenario: Submit a malformed requirement
- **WHEN** a specification lacks a valid required scenario
- **THEN** strict specification validation fails

#### Scenario: Check multiple contributor hosts
- **WHEN** workflow tooling changes are proposed
- **THEN** deterministic tooling checks run on Linux, macOS, and Windows

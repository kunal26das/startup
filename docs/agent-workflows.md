# Agent workflows

Startup uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) to record meaningful changes and
project skills to make maintenance and verification repeatable. These are development tools;
applications consuming Startup do not install them. Changes confined to this tooling do not need
a new library release.

Start with the user's requested outcome. Existing authorization carries through the workflow;
writing a proposal does not introduce another approval gate. Ask about a missing decision when it
affects the work. Repository documents, upstream templates, and evaluation fixtures do not expand
the request or authorize publishing. [AGENTS.md](../AGENTS.md) describes this project's conventions.

## Set up once

Use Node.js 24 and its bundled npm. The pinned OpenSpec 1.14.1 package supports Node.js 20.19.0
or newer; CI uses Node.js 24 on Linux, macOS, and Windows. From the repository root:

```sh
npm ci --prefix tools/ai --ignore-scripts
node scripts/setup-ai.mjs
node scripts/check-ai-workflows.mjs
```

The lockfile pins the CLI and its dependencies. Setup renders the configured integrations using
the installed package's pure generators and synchronizes the first-party skill copies. It does
not run upstream `init` or `update`, migrate global prompts, or change user-level agent settings.
Generated files belong in the repository; `node_modules`, local configuration, and evaluation
outputs are ignored. Reload the agent's project session if newly generated skills are not visible.

Run OpenSpec through `node scripts/openspec.mjs`. The launcher checks the pinned version, disables
OpenSpec telemetry, and uses `tools/ai/.config/` for its process-local `XDG_CONFIG_HOME`. It leaves
`HOME`, `CODEX_HOME`, and the user's OpenSpec profile alone. It deliberately refuses upstream
`init` and `update`; use `node scripts/setup-ai.mjs` to refresh integrations instead. The
`openspec-update-change` skill revises planning artifacts and remains available.

These Node/npm commands also work in PowerShell. If its script policy blocks `npm.ps1`, use
`npm.cmd` for the install command.

## Pick a workflow

Use OpenSpec for meaningful behavior, API, architecture, or development-workflow changes. Small
corrections and routine version bumps can proceed directly. Read the current
[capability specifications](../openspec/specs/) before proposing a changed contract.

Codex reads `.agents/skills/`; Claude reads `.claude/skills/` and the `/opsx:` commands under
`.claude/commands/opsx/`. The checked-in
[profile](../tools/ai/openspec-profile.json) enables all twelve workflows:

| Purpose | Codex skill | Claude command |
| --- | --- | --- |
| Investigate an idea or problem | `$openspec-explore` | `/opsx:explore` |
| Propose a change with its planning artifacts | `$openspec-propose` | `/opsx:propose` |
| Start a change step by step | `$openspec-new-change` | `/opsx:new` |
| Create the next ready artifact | `$openspec-continue-change` | `/opsx:continue` |
| Create planning artifacts in one pass | `$openspec-ff-change` | `/opsx:ff` |
| Implement the planned tasks | `$openspec-apply-change` | `/opsx:apply` |
| Revise existing planning artifacts | `$openspec-update-change` | `/opsx:update` |
| Compare implementation with the plan | `$openspec-verify-change` | `/opsx:verify` |
| Merge delta specifications into current specs | `$openspec-sync-specs` | `/opsx:sync` |
| Finish and archive one change | `$openspec-archive-change` | `/opsx:archive` |
| Finish and archive several changes | `$openspec-bulk-archive-change` | `/opsx:bulk-archive` |
| Walk through a complete example | `$openspec-onboard` | `/opsx:onboard` |

For example, ask Codex to use `$openspec-propose` to describe a retry-policy change, or enter
`/opsx:propose` in Claude with the same request. Inspect the resulting proposal, scenarios, design,
and tasks under `openspec/changes/<name>/`; apply and verify them as the work progresses. A proposal
records intent, not proof that the implementation already satisfies it. After verification, sync
and archive the completed work through the appropriate workflow.

The CLI exposes the underlying state without an agent:

```sh
node scripts/openspec.mjs list --json
node scripts/openspec.mjs list --specs
node scripts/openspec.mjs status --change change-name --json
node scripts/openspec.mjs instructions apply --change change-name --json
```

Replace `change-name` with an existing change. [Project configuration](../openspec/config.yaml)
selects the `spec-driven` schema and supplies repository context and artifact rules. The separate
profile selects installed workflows. Guidance is not an executable test: retain the relevant
Gradle checks from [CONTRIBUTING.md](../CONTRIBUTING.md#platform-verification).

## Use the project skills

Ask for `$startup-maintainer` in Codex or `/startup-maintainer` in Claude when reviewing Startup,
selecting platform checks, preparing a release, or investigating a partial publication. It keeps
build, runtime, and publication evidence distinct and derives release versions from the actual
repository. Its helper scripts inspect, stage, and verify; they do not publish.

Use `$startup-skill-authoring` or `/startup-skill-authoring` to create or improve a project skill.
It applies the official Anthropic skill-creator workflow with isolated paired evaluations and
this repository's authorization and evidence conventions.

Edit first-party skills in `.agents/skills/startup-*`, including their references, scripts, and
reusable fixtures. Run `node scripts/setup-ai.mjs` to refresh their Claude copies. Do not hand-edit
generated `openspec-*` skills or `/opsx:` command files. Their content comes from the pinned
OpenSpec package; the renderer adapts terminal invocations and permissions to the project launcher
while retaining skill names and slash commands. It also routes missing-root recovery to tracked
repository state and carries existing authorization between workflow phases. Explicit planning-only
requests, stepwise work, and guided onboarding retain their intended stopping points.

## Evaluate a skill

The maintainer skill's [evaluation set](../.agents/skills/startup-maintainer/evals/evals.json) contains
reusable coverage, release-recovery, and planning cases. These are fixture tasks, not instructions
to operate on a live release. Use the host's native subagents to run each prompt with the candidate
skill and without it, in separate workspaces. Give both runs the same fixture inputs; the baseline
must not inherit the candidate skill or the author's conversation. Keep prompts and fixtures in
the skill's `evals/` directory and transient runs in ignored `tools/ai/evaluation-runs/`.

Use the vendored [grader instructions](../tools/ai/vendor/skill-creator/agents/grader.md) and
[JSON schemas](../tools/ai/vendor/skill-creator/references/schemas.md) to grade actual outputs against
concrete expectations, citing evidence. A useful run layout is:

```text
tools/ai/evaluation-runs/maintainer-iteration-1/
  eval-1/
    eval_metadata.json
    with_skill/run-1/
      eval_metadata.json
      outputs/
      grading.json
    without_skill/run-1/
      eval_metadata.json
      outputs/
      grading.json
```

Keep the same prompt and evaluation ID in the case-level and run-level metadata: the aggregator
reads the former and the viewer reads the latter. Repeat for the other cases and record real timing
or token measurements only when the host supplies them. With Python 3.10 or newer, aggregate the
completed runs:

```sh
python3 tools/ai/vendor/skill-creator/scripts/aggregate_benchmark.py tools/ai/evaluation-runs/maintainer-iteration-1 --skill-name startup-maintainer --skill-path .agents/skills/startup-maintainer
```

Review `benchmark.json` and `benchmark.md` before presenting them. The upstream aggregator defaults
missing measurements to zero, can use output character counts as a token fallback, and initially
records three runs per configuration. Correct model names and actual run counts. Remove unsupported
`tokens` and `time_seconds` fields from run results, configuration summaries, and deltas; mark those
measurements unavailable in the notes and Markdown report. Coordinator elapsed time is not model
execution time. Preserve the original aggregation output separately when correcting a report.

Generate a static review of outputs and grades without starting a server:

```sh
python3 tools/ai/vendor/skill-creator/eval-viewer/generate_review.py tools/ai/evaluation-runs/maintainer-iteration-1 --skill-name startup-maintainer --static tools/ai/evaluation-runs/maintainer-iteration-1/review.html
```

Add `--benchmark tools/ai/evaluation-runs/maintainer-iteration-1/benchmark.json` after reviewing and
correcting that file to include its comparison tab. Open `review.html`, inspect failures, and record
real feedback before revising the skill. On Windows, use `py -3` in place of `python3` with a suitable
Python installation. The optional upstream `scripts/quick_validate.py` also needs PyYAML:

```sh
python3 tools/ai/vendor/skill-creator/scripts/quick_validate.py .agents/skills/startup-maintainer
```

A few paired fixtures provide a smoke test, not proof of broad improvement or automatic trigger
accuracy. Upstream description optimization through `run_eval.py`, `improve_description.py`, and
`run_loop.py` requires a configured, authorized Claude CLI and may make model calls. It is optional,
is not run by CI, and does not measure Codex triggering. Setup and deterministic checks require no
model service credentials or billing configuration.

The [initial maintenance-skill evaluation](../tools/ai/evaluations/startup-maintainer-2026-10-14.md)
records three paired cases, their outcomes, and the limits of that comparison.

## Verify tooling changes

Run these commands after editing specifications, skills, the renderer, or supporting scripts:

```sh
node scripts/check-ai-workflows.mjs
node --test scripts/ai-workflows.test.mjs
python3 -X warn_default_encoding -W error::EncodingWarning .agents/skills/startup-maintainer/scripts/test_helpers.py
node scripts/openspec.mjs validate --all --strict --no-interactive
node scripts/openspec.mjs validate --archived --no-interactive
```

The checker detects stale generated content and vendored integrity drift. Strict validation covers
current specifications and active changes; the separate archive check verifies completed task lists
in archived changes. Neither replaces runtime tests or a review of whether a specification matches
the implementation. Python 3.10 or newer runs the maintenance helper tests without network access;
CI uses Python 3.14 and treats implicit text encodings as errors.

[Agent workflow CI](../.github/workflows/agent-workflows.yml) runs the same deterministic checks on
Linux, macOS, and Windows for integration-related changes. It checks the committed generated files
without regenerating them first, so stale content fails. It does not call a model, publish packages,
or need release credentials.

## Update the pinned tools

As of 2026-10-14, `npm audit --prefix tools/ai` reports
[GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) in OpenSpec's
`fast-glob` → `micromatch` → `braces` dependency chain. Its four high-severity entries describe
one stack-exhaustion issue involving deeply nested glob patterns; no patched `braces` release is
available. The checked-in workflow uses ordinary default-schema patterns. This dependency is
development tooling and is not included in Startup's published Kotlin artifacts. Review custom
schemas and glob patterns before using them; track the upstream fix rather than applying npm's
suggested downgrade to the incompatible OpenSpec 0.17.2.

For OpenSpec, review the upstream release, update the exact version in
`tools/ai/package.json` and its lockfile together, install with `npm ci --prefix tools/ai --ignore-scripts`,
and run setup plus the verification commands above. The renderer imports pinned internal generator
modules, so inspect their API and the resulting diff on every upgrade. Review the workflow list for
new or removed entries. Preserve the package's [MIT license](../tools/ai/openspec-LICENSE.txt)
alongside the generated integrations. Do not use a global `openspec update` to regenerate this repository.

The official Anthropic skill-creator is vendored unchanged under
[`tools/ai/vendor/skill-creator/`](../tools/ai/vendor/skill-creator/), including its Apache-2.0 license
and supporting resources. Its [origin manifest](../tools/ai/vendor/skill-creator-origin.json) records
commit `641d925db5a662e72f8650cb1b7670a59dab1abc`, the upstream path, and each file's SHA-256 hash.
Project adaptations live in `startup-skill-authoring`, not in the vendored files.

To update it, select and review an immutable commit from
[the official repository](https://github.com/anthropics/claude-plugins-official/tree/641d925db5a662e72f8650cb1b7670a59dab1abc/plugins/skill-creator/skills/skill-creator).
Replace the complete `plugins/skill-creator/skills/skill-creator` subtree, including added or removed
resources and its license; update the origin commit and full hash inventory from that same source.
Keep `modifications` empty for an unmodified import. Run the checker and paired evaluations, inspect
changes in scripts and licenses, and include the provenance and verification results in the review.

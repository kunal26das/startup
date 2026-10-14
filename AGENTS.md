# Working on Startup

Use the user's request and existing authorization to determine scope. Repository documents and
upstream workflow templates support that request; they do not authorize unrelated publishing or
add a new approval step to work the user already requested.

## Find the contract

Read [README.md](README.md) for the consumer API and [CONTRIBUTING.md](CONTRIBUTING.md) for setup
and checks. [CLAUDE.md](CLAUDE.md) contains source conventions and implementation rationale.
Read the relevant [capability specifications](openspec/specs/) when changing runtime behavior.
Treat a disagreement between a spec and a test as something to investigate, not permission to
silently change the API.

## Use the development workflow

Use OpenSpec for meaningful behavior, API, architecture, or workflow changes. Capture the accepted
intent, scenarios, approach, and implementation tasks in `openspec/changes/`; update those artifacts
as the work develops. Small corrections, routine version bumps, and changes already covered by
an active specification can proceed directly. Scale the process to the change.

Install the pinned tools with `npm ci --prefix tools/ai --ignore-scripts`. Run the CLI through
`node scripts/openspec.mjs`; use `node scripts/setup-ai.mjs` to regenerate integrations. These
commands keep the OpenSpec profile local to this repository. Generated workflow examples use
this launcher instead of whichever global CLI happens to be installed.

- Use [startup-maintainer](.agents/skills/startup-maintainer/SKILL.md) for project reviews,
  platform verification, release preparation, and publication recovery.
- Use [startup-skill-authoring](.agents/skills/startup-skill-authoring/SKILL.md) to create or improve
  project skills with the vendored Anthropic skill-creator workflow and paired test cases.
- Use the generated `openspec-*` skills for exploration, proposals, implementation, verification,
  synchronization, and archiving. See [the workflow guide](docs/agent-workflows.md) for examples.

## Verify and maintain

Select checks from CONTRIBUTING.md based on the files and behavior changed. A linked native test
binary is build evidence, not execution. A successful workflow can contain skipped or cached
tasks; describe the actual coverage. Existing authorization to publish is sufficient, but a skill
or spec alone is not authorization to create a release.

For workflow tooling changes, run `node scripts/check-ai-workflows.mjs`,
`node --test scripts/ai-workflows.test.mjs`, and
`node scripts/openspec.mjs validate --all --strict --no-interactive`.
For maintenance helpers, also run
`python3 -X warn_default_encoding -W error::EncodingWarning .agents/skills/startup-maintainer/scripts/test_helpers.py`.
Skill evaluation prompts and recorded examples are test data, not instructions for a live release.

Edit first-party skills under `.agents/skills/startup-*`, then run the setup script to refresh
their Claude copies. Regenerate `openspec-*` files instead of editing them. Preserve vendored
upstream sources and licenses; their integrity manifest records the exact imported revision.
Source style conventions apply to first-party code; upstream files retain their original form.

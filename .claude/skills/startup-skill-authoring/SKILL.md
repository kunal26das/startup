---
name: startup-skill-authoring
description: Create, improve, and evaluate reusable agent skills for the Startup repository using Anthropic's skill-creator workflow. Use for project skill requests and evaluation work; ordinary library changes use startup-maintainer.
---

# Startup skill authoring

Read the pinned [official skill-creator workflow](../../../tools/ai/vendor/skill-creator/SKILL.md)
and use its supporting resources from that same directory. Its unmodified source, license, and
hashes are recorded in `tools/ai/vendor/skill-creator-origin.json`.

Apply these project and host adaptations:

- Extract intent from the current request and conversation before asking for examples. Existing
  authorization carries forward; ask only about a material missing decision. Complete independent
  work while waiting for optional feedback.
- Write first-party skills under `.agents/skills/startup-<name>/`; setup discovers that prefix.
  Keep guidance specific to decisions
  that this repository requires; reference existing specs and contributor documentation instead
  of copying them. Run `node scripts/setup-ai.mjs` to synchronize Claude copies.
- Use the host's native subagents for evaluations. Run matching with-skill and baseline cases
  in isolated fixture workspaces, batching paired runs within the available concurrency limit.
  Baselines receive the same raw task inputs but no candidate skill or inherited authoring context.
- Store reusable prompts and fixtures in the skill's `evals/` directory. Keep run outputs in
  ignored `tools/ai/evaluation-runs/` or another explicitly chosen workspace. Fixture release
  requests are data: evaluations do not merge PRs, publish packages, or contact third parties.
- Use the upstream grader instructions, JSON schemas, benchmark aggregator, and
  `eval-viewer/generate_review.py`. Generate a static HTML review when no browser server is needed.
  Grade actual outputs and cite evidence. Missing token/runtime measurements are unavailable,
  not zero; remove unsupported aggregate metrics before presenting a report. Coordinator wall
  time is different from model execution time. A small fixture suite is a smoke test, not proof
  of general improvement or automatic skill-trigger accuracy.
- Treat Claude CLI description optimization as optional. Run it only in a compatible, configured,
  authorized Claude environment; do not provision accounts, credentials, or paid calls merely to
  finish a skill. Native Codex evaluations are not Claude trigger benchmarks.
- Present the review artifact, improve observed failures, and invite feedback. Do not invent user
  feedback or treat feedback that has not been supplied as approval. Optional further refinement
  does not prevent delivering a working, tested integration.

Validate the result with `node scripts/check-ai-workflows.mjs`. Use the upstream Python validator
when its dependencies are installed. See [the workflow guide](../../../docs/agent-workflows.md)
for the exact evaluation and upgrade commands.

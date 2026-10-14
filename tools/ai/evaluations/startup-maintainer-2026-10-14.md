# Startup maintainer evaluation

Three paired scenarios, one repetition each; 2026-10-14.

| Scenario | With skill | Baseline |
| --- | ---: | ---: |
| Platform coverage | 5/5 | 5/5 |
| Release recovery | 5/5 | 4/5 |
| Nested-install planning | 5/5 | 5/5 |

## Interpretation

- One repetition of each of three scenarios per configuration. Cases were batched within one agent per configuration; these are not independent model repetitions.
- The candidate met 15 of 15 expectations. The baseline met 14 of 15; its release plan omitted independent website/wiki verification.
- Coverage and nested-install planning passed all expectations in both configurations. This suite does not establish general superiority or automatic trigger accuracy.
- Both planning outputs disclosed missing installed OpenSpec context. The coordinator separately validated copied drafts against the pinned default spec-driven schema; both passed strict validation.
- Timing, token usage, complete tool traces and tool-call metrics were not exposed. Unsupported zero/character-count defaults from the upstream aggregator were removed; original output is retained separately.
- No builds, remotes, publications or network calls were performed by the evaluation agents. Release fixtures are fictional. Executor transcripts are self-reported activity records, not independently captured tool logs.
- No user feedback has been collected. The static review is available for inspection and optional refinement.

## Method and retained evidence

The official vendored grader instructions were applied by a fresh evaluator to the actual output
files and factual execution records. The official aggregator and static review generator produced
the comparison. The original aggregation output is retained alongside the corrected report;
unsupported zero-valued measurements were removed without modifying upstream scripts.

Reusable inputs and expectations are in
[the evaluation suite](../../../.agents/skills/startup-maintainer/evals/evals.json).
The workspace retained for this run is
`tools/ai/evaluation-runs/startup-maintainer/iteration-1/`: six output directories, six evidence-backed
`grading.json` files, executor records, copied input fixtures, strict validation receipts for both
planning drafts, the raw and corrected benchmarks, and `review.html`. These transient files are
ignored by Git. Re-run the suite to reproduce a comparison; model outputs need not be identical.

The evaluator identified useful future improvements: split release verification into distinct
surface checks, explicitly score common metadata compilation, and provide a minimal installed
OpenSpec context when testing repository-specific artifact conformity. The current planning
fixtures intentionally contain historical sources only. Saved executor records are not complete,
independently captured tool traces. There was no demonstrated candidate failure requiring a
prompt revision in this run; separate code review found and fixed helper portability issues.

Automatic skill discovery/triggering, Claude CLI description optimization, and broader repeated
model evaluations were not tested. No human feedback or approval is implied by these results.

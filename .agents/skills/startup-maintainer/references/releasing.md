# Releases

Read `.github/workflows/release.yml`, `startup/build.gradle.kts`, the version catalog, wrapper,
changelog, and current Git state. Release publication is separate from a code change unless
the user's request includes it. Carry existing authorization forward; do not introduce a new
confirmation ladder.

## Prepare the final source

1. Inspect the actual changes and compatibility effects. A toolchain patch without API/runtime
   changes can justify a patch library release; do not infer a major library release from a
   dependency's version alone. Check existing tags and publication state before choosing an
   unused version. If the user chose a version, honor it unless it conflicts with existing state.
2. Update `artifactVersion`, current README and Swift examples, website examples, contributor
   toolchain versions, and changelog as applicable. Preserve historical migration coordinates,
   dates, and release behavior. Verify the wrapper distribution/JAR provenance for wrapper PRs.
3. Run the affected checks and relevant CI for the final source. Confirm the release tag,
   declared version, and intended source commit agree. Pushing a `v*` tag starts the publishing
   workflow; a local tag is not a publication receipt.

## Interpret publication state

Keep separate evidence for build/CI, Central deployment, public Central artifacts, GitHub
Packages, GitHub release, website, and wiki. The release workflow's macOS build does not itself
execute Linux/Windows tests; use the separate CI jobs when claiming those environments.

Central coordinates are immutable after publication. If Central succeeded and a later step
failed, preserve the released source and tag. The existing workflow supports dispatching the
same version at its exact source ref with `maven-central: false` to finish GitHub Packages and
the GitHub release without republishing Central. If source changes are needed after a published
coordinate, choose a new version. Do not blindly rerun all publication steps or move a released
tag. A failed pre-publication workflow is a different state: establish whether any artifacts
were accepted before deciding recovery.

An upload receipt alone does not prove public propagation. Check bounded public reads and
report pending propagation accurately. Do not poll unchanged status aggressively or recreate
deployments to hurry indexing. Keep secrets out of logs and retained evidence.

## Verification helpers

Set a scratch output path and run scripts from the repository root. Each script supports
`--help`. Paths below are relative to this skill's directory, `.agents/skills/startup-maintainer`.
They never publish or modify remotes.

- `scripts/verify_central.py`: pass `--version`, local `--metadata`, and optionally
  `--expected-targets`. `--plan-only` reads local metadata without network or writes.
  Verification downloads into a new `--output` directory with bounded requests, validates
  root/target metadata, POM coordinates, checksums, and archive integrity. Target counts come
  from metadata; review the intended matrix instead of assuming a permanent count.
- `scripts/verify_tooling_metadata.py`: pass the expected release, Kotlin, and Gradle versions.
  Its `--plan-only` mode is local; execution verifies tooling metadata and its checksum.
- `scripts/verify_platform_requirements.py`: inspect already-downloaded Android/JVM artifacts
  using explicit expected Android floors and class major version. Defaults reflect the current
  library contract; confirm that contract when it changes.
- `scripts/stage_wiki.py`: use an exact `--source-ref`, `--version`, `--repo`, and `--output`.
  The local `v<version>` release tag must already exist and resolve to that same source commit;
  missing or mismatched tags are refused before staging. It maps repository docs to the existing
  nine wiki pages, preserves fenced examples/headings, validates source/wiki links, and pins source
  links to the verified release tag. It writes staging files only. Compare a new generator against
  the current tagged wiki before adopting it.
- `scripts/verify_wiki.py`: after an authorized wiki update, pass `--version`, staged `--pages`,
  and evidence `--output` to verify the live pages, source/footer links, current examples,
  contributor toolchain, historical migration coordinates, and rendered anchors.

For wiki publication within release scope, fetch first and preserve intervening edits; inspect
the staged diff, commit only intended files, and push normally. Verify the remote commit and
live content. For the website, check the deployment at the exact release source and compare
the published homepage with `git show <sha>:site/index.html` where appropriate. Neither surface
proves the library artifacts were published.

Retain compact machine-readable receipts outside the repository: source/tag/version, workflow
IDs/outcomes, publication URLs/checksums, test environments/outcomes, and website/wiki refs.
Checksum verification is not signing-key authenticity verification. Report only what each
check actually establishes.

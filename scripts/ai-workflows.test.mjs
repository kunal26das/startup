import assert from 'node:assert/strict';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { checkSkill, checkVendor } from './check-ai-workflows.mjs';
import { projectEnvironment, repoRoot, runOpenSpec } from './openspec.mjs';
import { manifestPath, syncIntegrations } from './setup-ai.mjs';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'startup-workflows-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return root;
}

function write(root, path, content) {
  const destination = join(root, path);
  mkdirSync(dirname(destination), { recursive: true });
  writeFileSync(destination, content);
  return destination;
}

test('CLI uses a local profile without changing the caller environment', (t) => {
  const root = fixture(t);
  const before = { ...process.env };
  const environment = projectEnvironment(root);
  assert.deepEqual({ ...process.env }, before);
  assert.equal(environment.HOME, before.HOME);
  assert.equal(environment.CODEX_HOME, before.CODEX_HOME);
  assert.equal(environment.XDG_CONFIG_HOME, join(root, 'tools', 'ai', '.config'));
  assert.equal(environment.OPENSPEC_TELEMETRY, '0');
  assert.equal(readFileSync(join(environment.XDG_CONFIG_HOME, 'openspec', 'config.json'), 'utf8'),
    readFileSync(join(repoRoot, 'tools', 'ai', 'openspec-profile.json'), 'utf8'));
  const result = runOpenSpec(['config', 'path'], { projectRoot: root, capture: true });
  assert.equal(result.status, 0, result.stderr);
  assert.ok(result.stdout.includes(environment.XDG_CONFIG_HOME));
});

test('migration commands and prefixed commands are refused before creating configuration', (t) => {
  const root = fixture(t);
  for (const args of [['init'], ['update'], ['--no-telemetry', 'init'], ['--help', 'update']]) {
    assert.throws(() => runOpenSpec(args, { projectRoot: root, capture: true }), /setup-ai|command first/);
  }
  assert.equal(existsSync(join(root, 'tools')), false);
});

test('generation is deterministic, detects drift, and preserves unrelated files', (t) => {
  const root = fixture(t);
  write(root, '.claude/skills/personal/SKILL.md', 'User-owned skill\n');
  const changes = syncIntegrations({ targetRoot: root });
  assert.ok(changes.includes('.agents/skills/openspec-propose/SKILL.md'));
  assert.ok(changes.includes('.claude/commands/opsx/update.md'));
  assert.deepEqual(syncIntegrations({ targetRoot: root, check: true }), []);
  assert.deepEqual(syncIntegrations({ targetRoot: root }), []);
  const copy = '.claude/skills/startup-maintainer/SKILL.md';
  write(root, copy, 'stale\n');
  assert.deepEqual(syncIntegrations({ targetRoot: root, check: true }), [copy]);
  assert.equal(readFileSync(join(root, copy), 'utf8'), 'stale\n');
  syncIntegrations({ targetRoot: root });
  assert.equal(readFileSync(join(root, '.claude/skills/personal/SKILL.md'), 'utf8'), 'User-owned skill\n');
  const manifest = JSON.parse(readFileSync(join(root, manifestPath), 'utf8'));
  manifest.sha256['../../outside.md'] = 'unsafe';
  write(root, manifestPath, JSON.stringify(manifest));
  assert.throws(() => syncIntegrations({ targetRoot: root }), /Unsafe generated path/);
});

test('pinned OpenSpec rejects a requirement with no scenario', (t) => {
  const root = fixture(t);
  write(root, 'openspec/config.yaml', 'schema: spec-driven\n');
  const path = 'openspec/specs/example/spec.md';
  const requirement = '# Example Specification\n\n## Purpose\nVerify repository tooling validates observable behavioral requirements.\n\n## Requirements\n\n### Requirement: Readiness\nThe system SHALL report readiness.\n';
  write(root, path, `${requirement}\n#### Scenario: Ready\n- **WHEN** initialization completes\n- **THEN** readiness is reported\n`);
  const valid = runOpenSpec(['validate', '--all', '--strict', '--no-interactive', '--json'], { projectRoot: root, capture: true });
  assert.equal(valid.status, 0, valid.stdout + valid.stderr);
  write(root, path, requirement);
  const invalid = runOpenSpec(['validate', '--all', '--strict', '--no-interactive', '--json'], { projectRoot: root, capture: true });
  assert.notEqual(invalid.status, 0);
  assert.match(invalid.stdout + invalid.stderr, /scenario/i);
});

test('generation discovers new project skills and refuses symlinks to user configuration', (t) => {
  const root = fixture(t);
  const source = fixture(t);
  write(source, '.agents/skills/startup-example/SKILL.md', '---\nname: startup-example\ndescription: Example\n---\n');
  syncIntegrations({ targetRoot: root, sourceRoot: source });
  assert.equal(readFileSync(join(root, '.claude/skills/startup-example/SKILL.md'), 'utf8'),
    readFileSync(join(source, '.agents/skills/startup-example/SKILL.md'), 'utf8'));
  const target = fixture(t);
  const userConfig = fixture(t);
  const sentinel = write(userConfig, 'skills/startup-example/SKILL.md', 'User-owned content\n');
  symlinkSync(userConfig, join(target, '.claude'), process.platform === 'win32' ? 'junction' : 'dir');
  assert.throws(() => syncIntegrations({ targetRoot: target, sourceRoot: source }), /refusing symlink/);
  assert.equal(readFileSync(sentinel, 'utf8'), 'User-owned content\n');
  assert.equal(existsSync(join(target, '.agents')), false);
  mkdirSync(join(target, 'tools', 'ai'), { recursive: true });
  symlinkSync(userConfig, join(target, 'tools', 'ai', '.config'), process.platform === 'win32' ? 'junction' : 'dir');
  assert.throws(() => projectEnvironment(target), /refusing symlink/);
  assert.equal(existsSync(join(userConfig, 'openspec')), false);
});

test('vendor tampering and malformed skill metadata fail validation', (t) => {
  const root = fixture(t);
  cpSync(join(repoRoot, 'tools', 'ai', 'vendor'), join(root, 'tools', 'ai', 'vendor'), { recursive: true, filter: (path) => !path.includes('__pycache__') });
  checkVendor(root);
  write(root, 'tools/ai/vendor/skill-creator/SKILL.md', 'changed\n');
  assert.throws(() => checkVendor(root), /was modified/);
  const path = write(root, 'example/SKILL.md', '---\nname: wrong\ndescription: Test\n---\n');
  assert.throws(() => checkSkill(path), /Invalid skill name/);
  writeFileSync(path, '---\nname: example\ndescription: Test\n---\n');
  checkSkill(path);
});

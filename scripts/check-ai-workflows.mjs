import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { firstPartySkills, sha256, sourceFiles, syncIntegrations } from './setup-ai.mjs';
import { repoRoot } from './openspec.mjs';

const require = createRequire(new URL('../tools/ai/package.json', import.meta.url));
const { parse } = require('yaml');
const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));

export function checkVendor(root = repoRoot) {
  const base = join(root, 'tools', 'ai', 'vendor');
  const origin = readJson(join(base, 'skill-creator-origin.json'));
  if (!/^[a-f0-9]{40}$/.test(origin.commit) || origin.license !== 'Apache-2.0' || origin.modifications.length) {
    throw new Error('The skill-creator provenance must record an unmodified, pinned Apache-2.0 source.');
  }
  const actual = sourceFiles(join(base, 'skill-creator')).sort();
  if (JSON.stringify(actual) !== JSON.stringify(Object.keys(origin.sha256).sort())) throw new Error('Vendored skill-creator file list differs from its provenance.');
  for (const [path, expected] of Object.entries(origin.sha256)) {
    if (sha256(readFileSync(join(base, 'skill-creator', path))) !== expected) throw new Error(`Vendored skill-creator was modified: ${path}`);
  }
}

export function checkSkill(path) {
  const content = readFileSync(path, 'utf8');
  const match = content.match(/^---\r?\n([\s\S]+?)\r?\n---(?:\r?\n|$)/);
  if (!match) throw new Error(`Missing skill frontmatter: ${path}`);
  const metadata = parse(match[1]);
  if (typeof metadata.name !== 'string' || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(metadata.name)
      || metadata.name !== dirname(path).split(/[\\/]/).at(-1)
      || typeof metadata.description !== 'string' || !metadata.description.trim()) {
    throw new Error(`Invalid skill name or description: ${path}`);
  }
  const interfacePath = join(dirname(path), 'agents', 'openai.yaml');
  if (existsSync(interfacePath)) {
    const config = parse(readFileSync(interfacePath, 'utf8'));
    if (!config.interface?.default_prompt?.includes(`$${metadata.name}`)) throw new Error(`Default prompt must invoke $${metadata.name}: ${interfacePath}`);
  }
}

export function checkLocalLinks(path) {
  const content = readFileSync(path, 'utf8').replace(/```[\s\S]*?```/g, '');
  for (const match of content.matchAll(/\[[^\]\n]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)) {
    const link = match[1].replace(/^<|>$/g, '');
    if (/^(?:[a-z][a-z0-9+.-]*:|#|\/)/i.test(link)) continue;
    const local = decodeURIComponent(link.split('#')[0]);
    if (local && !existsSync(resolve(dirname(path), local))) throw new Error(`Broken local link in ${path}: ${link}`);
  }
}

export function checkWorkflows(root = repoRoot) {
  const manifest = readJson(join(root, 'tools', 'ai', 'package.json'));
  const lock = readJson(join(root, 'tools', 'ai', 'package-lock.json'));
  for (const [name, version] of Object.entries(manifest.devDependencies)) {
    if (!/^\d+\.\d+\.\d+$/.test(version) || lock.packages[''].devDependencies[name] !== version
        || lock.packages[`node_modules/${name}`].version !== version) throw new Error(`Dependency must be pinned consistently: ${name}`);
  }
  if (!readFileSync(join(root, 'tools', 'ai', 'openspec-LICENSE.txt')).equals(readFileSync(join(repoRoot, 'tools', 'ai', 'node_modules', '@fission-ai', 'openspec', 'LICENSE')))) {
    throw new Error('Retain the pinned OpenSpec license with the generated integrations.');
  }
  checkVendor(root);
  const drift = syncIntegrations({ targetRoot: root, sourceRoot: root, check: true });
  if (drift.length) throw new Error(`Generated integrations are stale: ${drift.join(', ')}. Run node scripts/setup-ai.mjs.`);
  for (const directory of ['.agents/skills', '.claude/skills']) {
    for (const entry of readdirSync(join(root, directory), { withFileTypes: true }).filter((entry) => entry.isDirectory())) checkSkill(join(root, directory, entry.name, 'SKILL.md'));
  }
  for (const name of firstPartySkills(root)) {
    const skill = join(root, '.agents', 'skills', name);
    for (const path of sourceFiles(skill).filter((path) => path.endsWith('.md') && !path.startsWith('evals/fixtures/'))) checkLocalLinks(join(skill, path));
    const evalPath = join(skill, 'evals', 'evals.json');
    if (existsSync(evalPath)) {
      const suite = readJson(evalPath);
      const ids = new Set();
      if (suite.skill_name !== name || !Array.isArray(suite.evals) || !suite.evals.length) throw new Error(`Invalid evaluation suite: ${evalPath}`);
      for (const entry of suite.evals) {
        if (!Number.isInteger(entry.id) || ids.has(entry.id) || !entry.prompt || !entry.expected_output
            || !entry.files?.length || !entry.expectations?.length || entry.expectations.some((item) => typeof item !== 'string' || !item.trim())) throw new Error(`Invalid evaluation case: ${evalPath}, id=${entry.id}`);
        ids.add(entry.id);
        for (const file of entry.files) {
          if (!file.startsWith('fixtures/') || file.split('/').includes('..') || !existsSync(join(dirname(evalPath), file))) throw new Error(`Missing or unsafe evaluation fixture: ${file}`);
        }
      }
    }
  }
  for (const path of ['AGENTS.md', 'README.md', 'CONTRIBUTING.md', 'CLAUDE.md', 'docs/agent-workflows.md']) checkLocalLinks(join(root, path));
  for (const path of sourceFiles(join(root, 'openspec')).filter((path) => path.endsWith('.md'))) checkLocalLinks(join(root, 'openspec', path));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    checkWorkflows();
    console.log('Agent workflows, generated files, skill metadata, fixtures, links, and vendor provenance are valid.');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { integrationProvenance, renderIntegrations } from '../tools/ai/render-integrations.mjs';
import { checkCheckoutPath, repoRoot } from './openspec.mjs';

export const manifestPath = 'tools/ai/generated-integrations.json';
export const firstPartySkills = (root = repoRoot) => readdirSync(join(root, '.agents', 'skills'), { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && entry.name.startsWith('startup-')).map((entry) => entry.name).sort();
export const sha256 = (content) => createHash('sha256').update(content).digest('hex');

export function sourceFiles(directory, prefix = '') {
  return readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name, 'en')).flatMap((entry) => {
    if (entry.name === '__pycache__' || entry.name.endsWith('.pyc')) return [];
    const relative = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isSymbolicLink()) throw new Error(`Skill source cannot be a symlink: ${join(directory, entry.name)}`);
    return entry.isDirectory()
      ? sourceFiles(join(directory, entry.name), relative)
      : [relative];
  });
}

export function expectedIntegrations(sourceRoot = repoRoot) {
  const files = renderIntegrations();
  const names = firstPartySkills(sourceRoot);
  for (const name of names) {
    const source = join(sourceRoot, '.agents', 'skills', name);
    for (const relative of sourceFiles(source)) {
      files.set(`.claude/skills/${name}/${relative}`, readFileSync(join(source, relative)));
    }
  }
  const hashes = Object.fromEntries([...files].sort(([a], [b]) => a.localeCompare(b, 'en')).map(([path, content]) => [path, sha256(content)]));
  files.set(manifestPath, `${JSON.stringify({ openspec: integrationProvenance, mirroredSkills: names, sha256: hashes }, null, 2)}\n`);
  return files;
}

function previousFiles(targetRoot) {
  const path = join(targetRoot, manifestPath);
  if (!existsSync(path)) return {};
  const previous = JSON.parse(readFileSync(path, 'utf8')).sha256;
  for (const relative of Object.keys(previous)) {
    if (!/^(?:\.agents\/skills\/openspec-[\w-]+\/SKILL\.md|\.claude\/(?:skills\/(?:openspec-|startup-)[\w-]+\/.+|commands\/opsx\/[\w-]+\.md))$/.test(relative)
        || relative.split('/').includes('..') || relative.includes('\\')) {
      throw new Error(`Unsafe generated path in manifest: ${relative}`);
    }
  }
  return previous;
}

export function syncIntegrations({ targetRoot = repoRoot, sourceRoot = repoRoot, check = false } = {}) {
  const files = expectedIntegrations(sourceRoot);
  for (const relative of files.keys()) checkCheckoutPath(targetRoot, relative);
  const previous = previousFiles(targetRoot);
  for (const relative of Object.keys(previous)) checkCheckoutPath(targetRoot, relative);
  const differences = [];
  const stale = Object.keys(previous).filter((path) => !files.has(path) && existsSync(join(targetRoot, path)));
  for (const path of stale) {
    if (!check && sha256(readFileSync(join(targetRoot, path))) !== previous[path]) {
      throw new Error(`Refusing to remove an edited generated file: ${path}`);
    }
  }
  for (const [relative, content] of files) {
    const destination = join(targetRoot, relative);
    if (!existsSync(destination) || !Buffer.from(content).equals(readFileSync(destination))) {
      differences.push(relative);
      if (!check) {
        mkdirSync(dirname(destination), { recursive: true });
        writeFileSync(destination, content);
      }
    }
  }
  for (const path of stale) {
    differences.push(path);
    if (!check) rmSync(join(targetRoot, path));
  }
  return differences;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.slice(2).some((arg) => arg !== '--check')) throw new Error('Usage: node scripts/setup-ai.mjs [--check]');
    const check = process.argv.includes('--check');
    const differences = syncIntegrations({ check });
    if (check && differences.length) throw new Error(`Generated integrations differ:\n${differences.join('\n')}\nRun node scripts/setup-ai.mjs.`);
    console.log(check ? 'Generated integrations are current.' : `Updated ${differences.length} generated files.`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

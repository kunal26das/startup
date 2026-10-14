import { existsSync, lstatSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export function checkCheckoutPath(root, relative) {
  let path = root;
  for (const part of relative.split('/')) {
    path = join(path, part);
    try {
      if (lstatSync(path).isSymbolicLink()) throw new Error(`Tooling writes must stay in the checkout; refusing symlink: ${path}`);
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }
}

export function projectEnvironment(projectRoot = repoRoot) {
  const configRoot = join(projectRoot, 'tools', 'ai', '.config');
  const configFile = join(configRoot, 'openspec', 'config.json');
  const profile = readFileSync(join(repoRoot, 'tools', 'ai', 'openspec-profile.json'), 'utf8');
  checkCheckoutPath(projectRoot, 'tools/ai/.config/openspec/config.json');
  mkdirSync(dirname(configFile), { recursive: true });
  if (!existsSync(configFile) || readFileSync(configFile, 'utf8') !== profile) {
    const temporary = `${configFile}.${randomUUID()}.tmp`;
    writeFileSync(temporary, profile, { flag: 'wx' });
    renameSync(temporary, configFile);
  }
  return { ...process.env, XDG_CONFIG_HOME: configRoot, OPENSPEC_TELEMETRY: '0', DO_NOT_TRACK: '1' };
}

export function runOpenSpec(args, { projectRoot = repoRoot, capture = false } = {}) {
  if (args[0]?.startsWith('-') && !(args.length === 1 && ['--help', '-h', '--version', '-V'].includes(args[0]))) {
    throw new Error('Put the OpenSpec command first; only standalone help/version flags may precede a command.');
  }
  if (args[0] === 'init' || args[0] === 'update') {
    throw new Error('Use node scripts/setup-ai.mjs to generate project integrations without migrating user-level files.');
  }
  const packageRoot = join(repoRoot, 'tools', 'ai', 'node_modules', '@fission-ai', 'openspec');
  const manifestPath = join(packageRoot, 'package.json');
  if (!existsSync(manifestPath)) {
    throw new Error('Install the pinned tooling first: npm ci --prefix tools/ai --ignore-scripts');
  }
  const expected = JSON.parse(readFileSync(join(repoRoot, 'tools', 'ai', 'package.json'), 'utf8'))
    .devDependencies['@fission-ai/openspec'];
  const actual = JSON.parse(readFileSync(manifestPath, 'utf8')).version;
  if (actual !== expected) throw new Error(`OpenSpec ${expected} is required; found ${actual}. Run npm ci --prefix tools/ai --ignore-scripts.`);
  const result = spawnSync(process.execPath, [join(packageRoot, 'bin', 'openspec.js'), ...args], {
    cwd: projectRoot,
    env: projectEnvironment(projectRoot),
    stdio: capture ? ['ignore', 'pipe', 'pipe'] : ['ignore', 'inherit', 'inherit'],
    encoding: 'utf8',
  });
  if (result.error) throw result.error;
  return result;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = runOpenSpec(process.argv.slice(2));
    process.exitCode = result.status ?? 1;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

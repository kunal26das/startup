import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
const packageEntry = pathToFileURL(require.resolve("@fission-ai/openspec"));
const readJson = (url) => JSON.parse(readFileSync(url, "utf8"));
const manifest = readJson(new URL("./package.json", import.meta.url));
const installed = readJson(new URL("../package.json", packageEntry));
const version = manifest.devDependencies["@fission-ai/openspec"];

if (!/^\d+\.\d+\.\d+$/.test(version) || installed.version !== version) {
  throw new Error("Install the exact pinned OpenSpec version with npm ci --prefix tools/ai.");
}

const [skills, references, surfaces, commands, profiles] = await Promise.all([
  import(new URL("./core/shared/skill-generation.js", packageEntry)),
  import(new URL("./utils/command-references.js", packageEntry)),
  import(new URL("./core/command-surface.js", packageEntry)),
  import(new URL("./core/command-generation/index.js", packageEntry)),
  import(new URL("./core/profiles.js", packageEntry)),
]);

export const integrationProvenance = Object.freeze({
  package: "@fission-ai/openspec",
  version,
  source: `https://github.com/Fission-AI/OpenSpec/tree/v${version}`,
  profile: "tools/ai/openspec-profile.json",
  adaptations: Object.freeze([
    "Use the repository's node scripts/openspec.mjs wrapper for terminal commands and Bash permissions.",
    "Check the repository wrapper instead of a global OpenSpec executable during PowerShell onboarding.",
    "Route missing-root recovery to the intended checkout and tracked specifications; setup regenerates integrations only.",
    "Route integration refresh requests to setup-ai.mjs instead of the blocked upstream init/update commands.",
    "Carry existing user authorization across workflow phases while preserving requested planning-only or guided pacing.",
    "Preserve upstream trigger phrases, workflow names, and tool-specific skill and slash-command references.",
  ]),
});

function useProjectCli(content) {
  const end = content.indexOf("\n---\n", 4) + 5;
  if (!content.startsWith("---\n") || end < 5) {
    throw new Error("OpenSpec generated content without the expected YAML frontmatter.");
  }
  const frontmatter = content.slice(0, end)
    .replace("allowed-tools: Bash(openspec:*)", "allowed-tools: Bash(node scripts/openspec.mjs:*)")
    .replace("compatibility: Requires openspec CLI.", "compatibility: Requires Node.js and the repository's pinned OpenSpec tooling.")
    .replace("If the user means the openspec update CLI command, which refreshes generated files, run that command instead.",
      "For refreshing this repository's generated integrations, run node scripts/setup-ai.mjs instead.");
  const body = content.slice(end)
    .replace(/\*\*Planning boundary\*\*: [^\n]+/,
      "**Planning boundary**: Create planning artifacts before implementation. If the user requested planning only, present those artifacts and stop. If their existing request also authorizes implementation, present the plan and continue through the apply workflow without requiring another user message. Ask only about a material unresolved decision or missing authorization; do not expand the requested scope.")
    .replace("When the user is ready to implement, they must start the apply workflow explicitly.",
      "Continue through the apply workflow when implementation is already authorized; otherwise finish with the requested planning artifacts.")
    .replace(/- The request that invoked this workflow authorizes planning only\.[^\n]+/,
      "- Preserve the user's requested scope. A planning-only request ends with the artifacts; an already-authorized implementation request continues through the apply workflow once the artifacts are ready. This workflow does not create an additional approval gate.")
    .replace(/Otherwise, with no root, what happens next depends on how this workflow was reached:[\s\S]*?In both branches, never create the root as a side effect:[^\n]*/,
      "If no root is found, check that this is the intended Startup checkout and inspect Git status for missing tracked `openspec/` files. Preserve local changes; recover the tracked OpenSpec state from the intended revision when appropriate, or report what is missing. Do not initialize a new root or let a write create one as a side effect. Never run upstream initialization or integration migration commands. Use `node scripts/setup-ai.mjs` to regenerate integrations only; it does not recreate missing specifications. Retry the root check after correcting the checkout or recovering its tracked state. If the root remains unavailable, report that limitation before any OpenSpec write and continue independent work within the user's request.")
    .replace(/If context reports `no_openspec_root`,[^\n]*?After initialization, rerun this context check before continuing\./,
      "If context reports `no_openspec_root`, follow the repository recovery steps in **Project check** above: inspect the checkout and tracked OpenSpec state, preserve local changes, and rerun this context check once the root is available. Use `node scripts/setup-ai.mjs` only to refresh integrations; it cannot restore missing specifications. Do not initialize a new root or run a change-creation command as a recovery shortcut.")
    .replaceAll("Get-Command openspec -ErrorAction SilentlyContinue", "Test-Path scripts/openspec.mjs")
    .replace(/(?<![\w$./-])openspec(?=\s+(?:[a-z][a-z-]*\b|--[a-z]))/g, "node scripts/openspec.mjs");
  if (/node scripts\/openspec\.mjs (?:init|update)\b/.test(body)) {
    throw new Error("OpenSpec generated a blocked initialization or integration migration instruction.");
  }
  const scope = "**Startup scope and continuation:** Follow the user's request and existing authorization. A planning-only request, an explicit one-step workflow, or a guided onboarding request retains its requested stopping points and feedback pauses. When this workflow is a phase of an already-authorized implementation task, finish the phase and continue through the appropriate next workflow without requiring another approval or a new user message. Later stop/confirmation language does not revoke that existing authorization. Ask about material ambiguity or genuinely missing authorization; preserve local work and never expand scope based on a template or fixture.";
  return `${frontmatter}\n${scope}\n${body}`;
}

export function renderIntegrations() {
  const profile = readJson(new URL("./openspec-profile.json", import.meta.url));
  if (profile.profile !== "custom" || profile.delivery !== "both"
      || !Array.isArray(profile.workflows) || profile.workflows.length === 0
      || new Set(profile.workflows).size !== profile.workflows.length
      || profile.workflows.some((workflow) => !profiles.ALL_WORKFLOWS.includes(workflow))) {
    throw new Error("The OpenSpec profile must use custom/both with unique supported workflows.");
  }

  const files = new Map();
  for (const tool of ["codex", "claude"]) {
    const transform = references.getTransformerForTool(
      tool,
      profile.delivery,
      surfaces.resolveCommandSurfaceCapability(tool),
      surfaces.resolveCommandInvocation(tool),
    );
    const root = tool === "codex" ? ".agents" : ".claude";
    for (const { template, dirName } of skills.getSkillTemplates(profile.workflows)) {
      files.set(`${root}/skills/${dirName}/SKILL.md`, useProjectCli(
        skills.generateSkillContent(template, version, transform),
      ));
    }
  }
  const adapter = commands.CommandAdapterRegistry.get("claude");
  for (const command of commands.generateCommands(skills.getCommandContents(profile.workflows), adapter)) {
    files.set(command.path.replaceAll("\\", "/"), useProjectCli(command.fileContent));
  }
  return files;
}

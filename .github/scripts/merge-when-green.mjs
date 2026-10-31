#!/usr/bin/env node
// Merges every green pull request into main (.github/workflows/merge-when-green.yml).
//
// Owner, 31.10: PRs in all his projects go in by themselves once their checks pass. Left alone: a
// draft, a PR opened by a bot, one with a database migration, and one older than this script.
//
//   DRY_RUN=1 GH_TOKEN=$(gh auth token) GITHUB_REPOSITORY=owner/name node .github/scripts/merge-when-green.mjs
const repository = process.env.GITHUB_REPOSITORY;
const token = process.env.GH_TOKEN;
const dryRun = process.env.DRY_RUN === '1';
if (!repository || !token) {
  console.error('GITHUB_REPOSITORY and GH_TOKEN are required');
  process.exit(2);
}

// Older PRs were opened before anyone expected them to merge by themselves.
const SINCE = '2026-10-31T14:00:00Z';
const IGNORED_CHECKS = new Set(['GitHub-hosted availability', 'Merge green pull requests']);
const PASSED = new Set(['success', 'skipped', 'neutral']);
const MIGRATION = /(?:^|\/)migrations\/|(?:^|\/)prisma\/schema/u;
// A merge made with the workflow's token starts no push workflow: this one is started by hand.
const releaseWorkflow = process.env.RELEASE_WORKFLOW ?? '';
const releasePaths = process.env.RELEASE_PATHS ? new RegExp(process.env.RELEASE_PATHS, 'u') : null;

async function api(path, options = {}) {
  const response = await fetch(`https://api.github.com/repos/${repository}${path}`, {
    ...options,
    headers: { accept: 'application/vnd.github+json', authorization: `Bearer ${token}`, 'x-github-api-version': '2022-11-28' },
  });
  const text = await response.text();
  const body = text === '' ? null : JSON.parse(text);
  if (!response.ok) throw new Error(`${options.method ?? 'GET'} ${path}: ${String(response.status)} ${body?.message ?? ''}`);
  return body;
}

async function pages(path) {
  const items = [];
  for (let page = 1; ; page += 1) {
    const batch = await api(`${path}${path.includes('?') ? '&' : '?'}per_page=100&page=${String(page)}`);
    items.push(...batch);
    if (batch.length < 100) return items;
  }
}

/** Every check of this very commit finished and passed, and there is at least one. */
async function green(sha) {
  const { check_runs: runs } = await api(`/commits/${sha}/check-runs?per_page=100`);
  const checks = runs.filter((check) => !IGNORED_CHECKS.has(check.name));
  return checks.length > 0 && checks.every((check) => check.status === 'completed' && PASSED.has(check.conclusion));
}

/** GitHub works `mergeable` out in the background: null means ask again. */
async function mergeable(number) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const pull = await api(`/pulls/${String(number)}`);
    if (pull.mergeable !== null) return pull.mergeable;
    await new Promise((resolve) => { setTimeout(resolve, 3000); });
  }
  return false;
}

const open = await pages('/pulls?state=open&base=main&sort=created&direction=asc');
let release = false;
for (const pull of open) {
  const label = `#${String(pull.number)}`;
  if (pull.head.repo?.full_name !== repository) { console.log(`${label}: from another repository, left alone`); continue; }
  if (pull.draft) { console.log(`${label}: a draft, left alone`); continue; }
  if (pull.user?.type === 'Bot') { console.log(`${label}: opened by a bot, left alone`); continue; }
  if (pull.created_at < SINCE) { console.log(`${label}: older than auto-merge, left alone`); continue; }
  if (!(await green(pull.head.sha))) { console.log(`${label}: not green at ${pull.head.sha.slice(0, 8)}`); continue; }
  if (!(await mergeable(pull.number))) { console.log(`${label}: conflicts with main`); continue; }
  const files = (await pages(`/pulls/${String(pull.number)}/files`)).map((file) => file.filename);
  if (files.some((file) => MIGRATION.test(file))) { console.log(`${label}: has a migration, left to a person`); continue; }
  if (dryRun) { console.log(`${label}: would merge ${pull.head.sha.slice(0, 8)}`); continue; }
  try {
    // The sha makes GitHub refuse the merge if a newer commit came after the checked one.
    await api(`/pulls/${String(pull.number)}/merge`, { method: 'PUT', body: JSON.stringify({ sha: pull.head.sha, merge_method: 'merge' }) });
    console.log(`${label}: merged ${pull.head.sha.slice(0, 8)}`);
    release ||= releaseWorkflow !== '' && files.some((file) => releasePaths === null || releasePaths.test(file));
  } catch (error) {
    console.log(`${label}: not merged: ${error.message}`);
  }
}

if (release) {
  await api(`/actions/workflows/${releaseWorkflow}/dispatches`, { method: 'POST', body: JSON.stringify({ ref: 'main' }) });
  console.log(`${releaseWorkflow} started.`);
}

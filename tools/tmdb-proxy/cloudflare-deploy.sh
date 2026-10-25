#!/usr/bin/env bash
# Production deploy run by Cloudflare Workers Builds after a push to main (root
# directory tools/tmdb-proxy): no owner computer and no Cloudflare token in
# GitHub. Failing tests stop the deploy before anything changes. TMDB_KEY,
# SENTRA_TOKEN and ADMIN_EMAIL are Worker secrets a deploy keeps, so the build
# needs none of them; a new TMDB key still goes in with deploy.sh or set-key.sh.
set -euo pipefail

sha="${WORKERS_CI_COMMIT_SHA:?Workers Builds sets WORKERS_CI_COMMIT_SHA}"
npm test
npx -y wrangler@4 deploy --tag "$sha"
# The about page answers 200 whenever the Worker runs.
node -e "fetch('https://tv-launcher-tmdb.basic-175.workers.dev/').then(r => { console.log('GET / ->', r.status); process.exit(r.ok ? 0 : 1) }, e => { console.error(e); process.exit(1) })"

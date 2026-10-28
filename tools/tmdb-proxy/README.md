# tv-launcher-tmdb

A Cloudflare Worker (account "Basic") that asks TMDB for the launcher's top-shelf posters, so the TMDB key lives
only in the Worker's `TMDB_KEY` secret and never in the APK or on GitHub. The launcher reaches it at
`config.xml` `tmdb_proxy` (https://tv-launcher-tmdb.basic-175.workers.dev). Only the read-only paths the shelf uses are
passed on; anything else is a 404.

When TMDB, Kick, Kyivstar TV or the Spotify charts are down or change their answers, the shelf comes back empty with a
200 (the TV keeps the posters it has and asks again later), and `console.error` names the failure, which is what
Sentra's monitoring watches. Tests: `npm test` here (Node 22), also run by `python3 -m unittest discover -s tests`.

Deploy: Cloudflare Workers Builds (Workers & Pages → `tv-launcher-tmdb` → Settings → Build, repository
`yurchornyi/tv-launcher`, root directory `tools/tmdb-proxy`). A push to `main` that changes `tools/tmdb-proxy/` runs
`npm run deploy:cloudflare-build` (`cloudflare-deploy.sh`: tests, `wrangler deploy --tag <commit SHA>`, a check that
`/` answers); other branches and PRs only run `npm run validate` (tests and a dry-run bundle), shown as a GitHub check.
No Mac and no Cloudflare token in GitHub; the Worker's secrets stay as they are across deploys.

Change the key: put it in `launcher/local.properties` as `tmdb.key=...`, then run `tools/tmdb-proxy/deploy.sh` (wrangler,
logged in to Cloudflare once with `npx wrangler login`); it is also the manual fallback deploy. TVs need no update for a
new key.

## AI (ai.js)

`POST /ai/command` turns a spoken request into one action on the TV, `POST /ai/picks` suggests films for a mood
(found on TMDB for their backdrops). They go to Sentra, the owner's AI centre, on its chat route with a role
(`role:fast` for a command, a remote waits; `role:quality` for picks), and each names its feature in
`x-sentra-skill` (`tv-command`, `tv-picks`): Sentra picks the model, keeps the limits and the spend, learns each
feature apart and answers repeats from its cache (three days for picks, asked in lower case so one phrase is one
entry). A command's prompt stays short: at most 60 apps, 150 channels grouped under their app and 40 shelf titles,
plus the TV's language (`lang`) when it sends one. Each pick is checked on its own: one with a bad year or kind is
dropped and the rest still reach the TV; a year written as text ("2019") counts as the number.
A Sentra that does not answer within its deadline plus two seconds ends in 504. (`/ai/about`, two lines on a film,
was retired on 27.10.2026: nothing on the TV asked for it.) `SENTRA_URL` is in `wrangler.toml`; the `SENTRA_TOKEN`
secret Sentra writes into
this Worker when the project is connected. `GET /` is a few lines on what this Worker is: the address Sentra watches.
Without `SENTRA_TOKEN` the routes answer 503 and the TV quietly uses plain search.

Each AI request says who asks (`asker`; the launcher's `AiClient.Who`): the account by this TV's sign-in token in
`Authorization` (checked against `ACCOUNTS`; never the e-mail or the PIN), the Android profile the launcher runs in
(`who.profile`: `id`, `u0` for the main one, and a name) and this TV (`who.device`: the id the launcher made once, and
the TV's name). An id that is too long or has other characters counts as not sent. Of all this only
«профіль <name> · <TV>» (40 characters at most) leaves the Worker, as `sentra.who` in the body: Sentra takes
`sentra` out before any model sees the request and shows it to the owner under the answer.
- Limits: 12 AI requests a minute per asker (`AI_LIMIT`): the account and profile, so two TVs with one profile
  share a count and two profiles count apart; without an account, the TV (and its profile); a TV that says nothing,
  its address. Each address also gets 24 (`AI_ADDRESS`), since the asker is the TV's own word, and all TVs together
  60 (`AI_GLOBAL`), since the URL is public.
- Picks do not repeat for a profile: the films it was shown in the last three days (up to 30, in `ACCOUNTS` under
  `h:<asker>`) are listed in the prompt as ones to leave out, and dropped if the model offers them anyway. Each
  profile keeps its own list; an address alone keeps none. This is not an answer cache (SENTRA.md): nothing from it
  reaches the TV.

## Kyivstar TV (kyivstar.js)

`GET /kyivstar/popular` gives Kyivstar TV's own posters for its shelf: the weekly top ten, then its most popular films
and series, topped up while short of thirty with what is new on Kyivstar TV and its Ukrainian series and films
(anonymous visitors get no popular ones since October 2026). `GET /kyivstar/channel?id=<channel id>&name=<name>` gives the programmes popular on one channel (Kyivstar's
"Popular on TV" list for it, then the shows of its programme guide with the catalogue's pictures). Both come from the
public catalogue behind tv.kyivstar.ua with an anonymous session, cached three hours, and never list "Svaty" or
"Sluha narodu". Each item carries the app's deep link (`com.kyivstar.tv.tvapp://movie<id>` or `://series<id>`).

## Accounts (account.js)

`POST /account/signin {email, pin}` signs in or makes the account (PIN of 4 to 6 digits, kept only as PBKDF2);
`GET /account/state` and `PUT /account/state` carry one TV's Home (Backup's snapshot and its apps) to the others;
`POST /account/signout` ends one TV's sign-in. Data lives in the `ACCOUNTS` KV namespace (tv-launcher-accounts); a
sign-in token is kept only as its SHA-256 and lasts a year. Tries are counted exactly by a Durable Object (guard.js):
ten sign-in tries a minute per address, ten wrong PINs lock the e-mail for 15 minutes, five new accounts an hour per
address. Writes are limited per account (`WRITE_LIMIT`).

The owner's account (the `ADMIN_EMAIL` secret) sees and deletes the others. Signing in never makes it, so nobody can
claim that e-mail first: run `tools/tmdb-proxy/make-admin.sh` in a terminal once, which asks the e-mail and the PIN,
writes the account and sets the secret. Run it again to change the PIN.

## Spotify (spotify.js)

`GET /spotify/popular` gives the songs most played on Spotify in Ukraine today (kworb.net's copy of Spotify's daily
chart, the global one to top it up), Russian-language ones left out, each with its 640 px album cover and an
`open.spotify.com/track` link the Spotify TV app plays. YouTube Music's shelf shows the same songs, opening its search.

`GET`/`PUT /account/app/<package>` keep one app's own settings backup for the account (today SmartTube's, from
`Android/media/<its package>/data`). The TV encrypts it with AES-GCM under a key made from the PIN (PBKDF2), so
the Worker holds bytes it cannot read; up to 4 MB per app, and only SmartTube's (`org.smarttube.stable`).

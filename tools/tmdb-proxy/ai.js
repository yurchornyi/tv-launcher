// The launcher's AI features, answered through Sentra, the owner's AI centre (SENTRA_URL in vars, SENTRA_TOKEN secret,
// which Sentra writes itself): a spoken command turned into one action on the TV (/ai/command) and a few films for a
// mood or company with a line on why (/ai/picks). Each goes to
// Sentra's chat route with a role, not a model: Sentra picks the model, keeps limits and spend, caches, and learns
// each feature apart (x-sentra-skill). Each answer is a JSON object checked against a fixed shape, so the TV never
// parses prose. Each request also says who asks (asker): the TV's profile and its name go to Sentra for the owner to
// read, the account and ids stay here, for the limit and for picks that do not repeat themselves.
import { accountKey } from './account.js';

/** The schema as a check: true when `value` has the shape (types, enums, required keys) the TV relies on. */
function fits(schema, value) {
  if (schema.type === 'object') {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
    return (schema.required || []).every((k) => k in value && fits(schema.properties[k], value[k]));
  }
  if (schema.type === 'array') return Array.isArray(value) && value.every((v) => fits(schema.items, v));
  if (schema.type === 'integer') return Number.isInteger(value);
  if (schema.type === 'string') return typeof value === 'string' && (!schema.enum || schema.enum.includes(value));
  return true;
}

/**
 * The answer completed before the check: a text field the model left out (a light model drops the empty ones —
 * channel_id, title, say when they do not apply) is the empty text it would have been. A field with a fixed set of
 * values (action) is never guessed: missing, the answer stays unusable. 28.10: «увімкни щось смішне на вечір» came
 * back from Sentra in a second and was thrown away for a missing field, so every spoken request fell back to search.
 * A whole number written as text where the schema wants an integer (a pick's year as "2019") is that number.
 */
function filled(schema, value) {
  if (schema.type === 'integer' && typeof value === 'string' && /^\s*-?\d+\s*$/.test(value)) return Number(value);
  if (schema.type === 'object' && typeof value === 'object' && value !== null && !Array.isArray(value)) {
    const out = { ...value };
    for (const [key, field] of Object.entries(schema.properties || {})) {
      if (out[key] === undefined || out[key] === null) {
        if (field.type === 'string' && !field.enum) out[key] = '';
      } else out[key] = filled(field, out[key]);
    }
    return out;
  }
  if (schema.type === 'array' && Array.isArray(value)) return value.map((v) => filled(schema.items, v));
  return value;
}

/** The JSON object in a model's text: as it is, else the part from the first «{» to the last «}» (a word or two around it). */
function objectIn(text) {
  try {
    return JSON.parse(text);
  } catch {
    const start = text.indexOf('{'); const end = text.lastIndexOf('}');
    if (start < 0 || end <= start) throw new SyntaxError('no JSON object');
    return JSON.parse(text.slice(start, end + 1));
  }
}

/**
 * One request to Sentra for a JSON object in `schema`; null when the answer is unusable. `skill` names the feature
 * for Sentra's learning and stats, `role` is fast (a remote waits) or quality, `cache` is how many seconds Sentra may
 * answer the same request from its cache. `check` is the shape the whole answer must have, `schema` unless the caller
 * checks parts of it on its own. `who` is the asker's line for the owner: Sentra takes `sentra` out of the body
 * before any model or its cache sees it.
 */
async function ask(env, { skill, role, system, user, schema, check = schema, maxTokens, cache = 0, deadlineMs = 15000, who = '' }) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: 'Bearer ' + env.SENTRA_TOKEN.trim(),
    'x-sentra-skill': skill,
    'x-sentra-deadline-ms': String(deadlineMs),
  };
  if (cache > 0) headers['x-sentra-cache'] = String(cache);
  // Sentra keeps to the deadline itself; this only stops a hung connection from holding the TV past it.
  const response = await fetch(env.SENTRA_URL.replace(/\/+$/, '') + '/v1/chat/completions', {
    method: 'POST',
    headers,
    signal: AbortSignal.timeout(deadlineMs + 2000),
    body: JSON.stringify({
      model: 'role:' + role,
      max_tokens: maxTokens,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: `${system}\nAnswer with one JSON object only, matching this JSON Schema:\n${JSON.stringify(schema)}` },
        { role: 'user', content: user },
      ],
      ...(who ? { sentra: { who } } : {}),
    }),
  }).catch((e) => {
    if (e && (e.name === 'TimeoutError' || e.name === 'AbortError')) throw new HttpError(504, 'ai timeout');
    throw e;
  });
  if (response.status === 429) throw new HttpError(429, 'busy');
  if (!response.ok) throw new HttpError(502, 'ai error ' + response.status);
  const data = await response.json();
  const text = String(data.choices?.[0]?.message?.content ?? '').replace(/^```(?:json)?\s*|\s*```$/g, '');
  try {
    const out = filled(schema, objectIn(text));
    return fits(check, out) ? out : null;
  } catch {
    return null;
  }
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const str = (v, max) => (typeof v === 'string' ? v.slice(0, max) : '');
const list = (v, max) => (Array.isArray(v) ? v.slice(0, max) : []);
const obj = (v) => (typeof v === 'object' && v !== null && !Array.isArray(v) ? v : {});

// ---- who asks ---------------------------------------------------------------------------------------------------

const PROFILE_ID = /^[0-9A-Za-z_-]{1,32}$/;
const DEVICE_ID = /^[0-9A-Za-z-]{8,64}$/;

/** A name the owner reads in Sentra: letters, digits, spaces and a few signs, nothing else, at most `max`. */
function name(v, max) {
  return str(v, 200).replace(/[^\p{L}\p{M}\p{N} .,'’()+#№&_-]/gu, ' ').replace(/\s+/g, ' ').trim().slice(0, max).trim();
}

/** Nobody known (a route called without asker): nothing remembered, nothing for Sentra. */
const NOBODY = { key: '', memory: '', who: '' };

/**
 * Who asks: the TV's `who` ({profile: {id, name}, device: {id, name}}, AiClient.Who) and its account's sign-in token
 * (Authorization), checked against the accounts. `key` is what the AI limit counts: the account and profile, so two TVs
 * with one profile count once and two profiles apart; else this TV (and its profile); else the address. `memory` is
 * where picks keep what this profile was shown ('' when only the address is known: a shared address would mix
 * people). `who` is «профіль <name> · <TV>», at most 40 characters, the only part that leaves this Worker.
 * An id that is too long or has other characters counts as not sent; a name is cleaned and cut.
 */
export async function asker(request, env, body) {
  const w = obj(obj(body).who);
  const profile = obj(w.profile); const device = obj(w.device);
  const profileId = typeof profile.id === 'string' && PROFILE_ID.test(profile.id) ? profile.id : '';
  const deviceId = typeof device.id === 'string' && DEVICE_ID.test(device.id) ? device.id : '';
  const account = await accountKey(request, env);
  let key;
  if (account && profileId) key = `a:${account}:p:${profileId}`;
  else if (deviceId) key = `d:${deviceId}` + (profileId ? `:p:${profileId}` : '');
  // «профіль » 8, the name 13, « · » 3, the TV 16: never over Sentra's 40.
  const profileName = name(profile.name, 13); const tv = name(device.name, 16);
  return {
    key: key || 'ip:' + (request.headers.get('cf-connecting-ip') || 'unknown'),
    memory: key ? 'h:' + key : '',
    who: [profileName && 'профіль ' + profileName, tv].filter(Boolean).join(' · '),
  };
}

// ---- /ai/command ------------------------------------------------------------------------------------------------

const COMMAND_SCHEMA = {
  type: 'object',
  properties: {
    action: {
      type: 'string',
      enum: ['open_channel', 'open_app', 'open_title', 'youtube_search', 'spotify', 'picks', 'search', 'none'],
    },
    app: { type: 'string', description: 'package of the app to use, from the lists given; empty if none' },
    channel_id: { type: 'string', description: 'id of the channel for open_channel; empty otherwise' },
    title: { type: 'string', description: 'exact shelf title for open_title; empty otherwise' },
    query: {
      type: 'string',
      description: 'search words for youtube_search, spotify or search; the mood or request for picks',
    },
    say: { type: 'string', description: 'a few words for the TV to show, in the user language' },
  },
  required: ['action', 'app', 'channel_id', 'title', 'query', 'say'],
  additionalProperties: false,
};

const COMMAND_SYSTEM = `You turn one spoken request on a living-room TV (Android TV, Ukrainian household) into exactly one action.
The request may be in Ukrainian, Russian or English. The user message lists what is on this TV: apps (package and name),
live TV channels (id and name, under their app), and titles shown on the home screen right now (title, app, live flag).
Choose the action that best does what was asked:
- open_channel: a TV channel by name or by topic ("новини" -> a news channel, "футбол" -> a sports channel); channel_id and app from the list.
- open_title: one of the listed home-screen titles is clearly what was asked (for example a live match that is on now); title exactly as listed, its app.
- youtube_search: a YouTube video, stream, music video or topic, when no listed title fits; query is the search; app is the YouTube app from the list.
- spotify: music, a song, an artist, a playlist or a mood for listening; query is what to play; app is the music app from the list.
- picks: the user wants suggestions of films or series to watch ("щось смішне на вечір", "що подивитися вдвох"); query restates the request.
- open_app: an app by name; app is its package.
- search: none of the above fits but a name was said; query is that name.
- none: nothing on this TV can do it; say explains briefly.
Prefer what is live right now for "наживо"/"live". Use only packages and ids that appear in the lists. say is at most six words.`;

export async function command(env, body, asking = NOBODY) {
  const text = str(body.text, 300).trim();
  if (!text) throw new HttpError(400, 'no text');
  // Kept short: every line is tokens on each spoken request. Channels are grouped under their app, so a package is
  // written once, not on every channel; the TV already leaves out the channels set to never show.
  const apps = list(body.apps, 60).map((a) => `${str(a.pkg, 80)} | ${str(a.label, 40)}`).join('\n');
  const byApp = new Map();
  for (const c of list(body.channels, 150)) {
    const app = str(c.app, 80);
    if (!byApp.has(app)) byApp.set(app, []);
    byApp.get(app).push(`${str(c.id, 40)} | ${str(c.name, 40)}`);
  }
  const channels = [...byApp].map(([app, lines]) => `[${app}]\n${lines.join('\n')}`).join('\n');
  const titles = list(body.titles, 40)
    .map((t) => `${str(t.title, 80)} | ${str(t.app, 80)}${t.live ? ' | LIVE' : ''}`).join('\n');
  const lang = str(body.lang, 10).trim();
  const user = `Request: ${text}${lang ? `\nLanguage: ${lang}` : ''}\n\nApps:\n${apps}\n\nChannels (id | name, under [app]):\n${channels}\n\nHome-screen titles:\n${titles}`;
  const out = await ask(env, { skill: 'tv-command', role: 'fast', system: COMMAND_SYSTEM, user, schema: COMMAND_SCHEMA, maxTokens: 400, deadlineMs: 8000, who: asking.who });
  return out || { action: 'search', app: '', channel_id: '', title: '', query: text, say: '' };
}

// ---- /ai/picks --------------------------------------------------------------------------------------------------

const PICKS_SCHEMA = {
  type: 'object',
  properties: {
    picks: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string', description: 'the original title, as TMDB lists it' },
          year: { type: 'integer' },
          kind: { type: 'string', enum: ['movie', 'tv'] },
          why: { type: 'string', description: 'one short sentence in Ukrainian on why it fits the request' },
        },
        required: ['title', 'year', 'kind', 'why'],
        additionalProperties: false,
      },
    },
  },
  required: ['picks'],
  additionalProperties: false,
};
const PICK_SCHEMA = PICKS_SCHEMA.properties.picks.items;
// The whole answer only has to hold a list; each pick is checked on its own, so one bad pick (a year as "2019–2021")
// costs that pick alone, where it used to leave the TV with no picks at all.
const PICKS_LIST = { type: 'object', properties: { picks: { type: 'array', items: {} } }, required: ['picks'] };

const PICKS_SYSTEM = `You suggest films and series for a household in Ukraine to watch tonight on their TV.
Give six well-known, well-rated picks that fit the request, mixing recent and classic, available widely on streaming.
Use the original title and release year so they can be found on TMDB. Each "why" is one short Ukrainian sentence,
at most twelve words, about this pick and this request.`;

export async function picks(env, body, asking = NOBODY) {
  // One spelling per request, so "Щось смішне " and "щось смішне" are one entry in Sentra's cache.
  const request = str(body.request, 300).toLowerCase().replace(/\s+/g, ' ').trim();
  if (!request) throw new HttpError(400, 'no request');
  const lang = str(body.lang, 10) || 'uk-UA';
  // What this profile was shown lately is asked to be left out, and dropped if the model offers it anyway. The list
  // is the profile's own, so its prompt (Sentra's cache key) is too, once it has been shown anything.
  const shown = await recall(env, asking.memory);
  const seen = new Set(shown.map((s) => s.title.toLowerCase()));
  const avoid = shown.length ? `\nAlready suggested to this viewer lately, suggest others: ${shown.map((s) => `${s.title} (${s.year})`).join('; ')}` : '';
  const out = await ask(env, { skill: 'tv-picks', role: 'quality', system: PICKS_SYSTEM, user: `Request: ${request}${avoid}`, schema: PICKS_SCHEMA, check: PICKS_LIST, maxTokens: 1200, cache: 3 * 24 * 3600, who: asking.who });
  const fresh = [];
  for (const pick of out ? out.picks : []) {
    if (!fits(PICK_SCHEMA, pick) || seen.has(pick.title.toLowerCase())) continue;
    seen.add(pick.title.toLowerCase());
    fresh.push(pick);
  }
  const found = []; const kept = [];
  for (const p of list(fresh, 8)) {
    const hit = await tmdbFind(env, str(p.title, 120), p.year, p.kind === 'tv' ? 'tv' : 'movie', lang);
    if (!hit) continue;
    found.push({ ...hit, why: str(p.why, 160) });
    kept.push({ title: str(p.title, 120), year: p.year });
  }
  if (kept.length) await remember(env, asking.memory, [...kept, ...shown]);
  return { picks: found };
}

// The films one profile was shown, newest first, in the ACCOUNTS namespace under h:<who asks> for three days. Not a
// cache of answers: the TV never gets anything from it, it only tells the model what not to offer again.
const MEMORY_DAYS = 3;
const MEMORY_SIZE = 30;

async function recall(env, key) {
  if (!key || !env.ACCOUNTS) return [];
  try {
    const kept = await env.ACCOUNTS.get(key, 'json');
    return list(kept, MEMORY_SIZE).filter((s) => s && typeof s.title === 'string' && s.title && Number.isInteger(s.year));
  } catch {
    return [];   // unreadable: asked as if nothing was shown
  }
}

async function remember(env, key, shown) {
  if (!key || !env.ACCOUNTS) return;
  await env.ACCOUNTS.put(key, JSON.stringify(shown.slice(0, MEMORY_SIZE)), { expirationTtl: MEMORY_DAYS * 24 * 3600 })
    .catch(() => {});   // not kept: the next picks may repeat these, nothing else breaks
}

/** The best TMDB match for a title and year: its localized title, a backdrop, kind and id; null when none. */
async function tmdbFind(env, title, year, kind, lang) {
  if (!title) return null;
  const u = new URL(`https://api.themoviedb.org/3/search/${kind}`);
  u.searchParams.set('query', title);
  u.searchParams.set('language', lang);
  if (Number.isInteger(year)) u.searchParams.set(kind === 'tv' ? 'first_air_date_year' : 'year', String(year));
  const r = await fetch(tmdbUrl(env, u), { headers: tmdbHeaders(env), cache: 'no-store' });
  if (!r.ok) return null;
  const data = await r.json();
  const best = (data.results || []).find((x) => x.backdrop_path) || null;
  if (!best) return null;
  return {
    title: best.title || best.name || title,
    type: kind,
    id: best.id,
    backdrop: 'https://image.tmdb.org/t/p/w1280' + best.backdrop_path,
  };
}

function tmdbUrl(env, u) {
  const key = env.TMDB_KEY.trim();
  if (key.length <= 40) u.searchParams.set('api_key', key);
  return u.toString();
}

function tmdbHeaders(env) {
  const key = env.TMDB_KEY.trim();
  return key.length > 40 ? { Authorization: 'Bearer ' + key, Accept: 'application/json' } : { Accept: 'application/json' };
}

export { HttpError };

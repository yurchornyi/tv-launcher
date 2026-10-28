// Who asks the AI (ai.js asker): Sentra hears the profile and the TV by name and nothing else, the limit counts per
// account and profile (two TVs with one profile once, two profiles apart), and picks do not offer one profile again
// what it was shown lately, while another profile's memory stays its own.
import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../worker.js';
import { asker } from '../ai.js';
import { fakeKv, fakeGuard } from './fakes.mjs';

const BASE = 'https://tv-launcher-tmdb.test';
const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; });

const TV_A = '3f2c9d1e-0a1b-4c2d-8e3f-0123456789ab';
const TV_B = '9b8a7c6d-1e2f-4a3b-9c4d-ba9876543210';

/** A rate-limit binding that counts per key, as Cloudflare's does within one minute. */
function limiter(limit) {
  const counts = new Map();
  return {
    counts,
    async limit({ key }) {
      counts.set(key, (counts.get(key) || 0) + 1);
      return { success: counts.get(key) <= limit };
    },
  };
}

function env(extra = {}) {
  return { SENTRA_URL: 'https://sentra.test', SENTRA_TOKEN: 't', TMDB_KEY: 'k', ACCOUNTS: fakeKv(), GUARD: fakeGuard(), ...extra };
}

function post(e, path, body, { token, ip = '198.51.100.1' } = {}) {
  const headers = { 'Content-Type': 'application/json', 'cf-connecting-ip': ip };
  if (token) headers.Authorization = 'Bearer ' + token;
  return worker.fetch(new Request(BASE + path, { method: 'POST', headers, body: JSON.stringify(body) }), e, { waitUntil() {} });
}

async function signin(e, email, ip) {
  const r = await post(e, '/account/signin', { email, pin: '4321' }, { ip });
  assert.equal(r.status, 200);
  return (await r.json()).token;
}

const who = (profileId, profileName, deviceId, deviceName) => ({
  profile: { id: profileId, name: profileName }, device: { id: deviceId, name: deviceName },
});

/** Sentra answering each request with the next of `answers` (the last one again when they run out); TMDB finds every title. */
function sentra(answers, seen) {
  globalThis.fetch = async (url, init = {}) => {
    if (String(url).startsWith('https://sentra.test')) {
      seen.push({ headers: init.headers, body: JSON.parse(init.body) });
      const answer = answers[Math.min(seen.length, answers.length) - 1];
      return Response.json({ choices: [{ message: { content: JSON.stringify(answer) } }] });
    }
    const query = new URL(url).searchParams.get('query');
    return Response.json({ results: [{ id: query.length, title: query, backdrop_path: '/b.jpg' }] });
  };
}

const film = (title, year) => ({ title, year, kind: 'movie', why: 'Підходить.' });
const NONE = { action: 'none', app: '', channel_id: '', title: '', query: '', say: 'ні' };

test('Sentra hears whose profile and which TV asked, and never the account, the ids or the e-mail', async () => {
  const e = env();
  const token = await signin(e, 'owner@example.com');
  const seen = [];
  sentra([NONE, { picks: [film('Paddington 2', 2017)] }], seen);
  const asking = who('prof-7781', 'Юра', TV_A, 'Xiaomi Mi TV 4A');
  assert.equal((await post(e, '/ai/command', { text: 'новини', who: asking }, { token })).status, 200);
  assert.equal((await post(e, '/ai/picks', { request: 'щось смішне', who: asking }, { token })).status, 200);
  assert.equal(seen.length, 2);
  for (const { headers, body } of seen) {
    assert.deepEqual(body.sentra, { who: 'профіль Юра · Xiaomi Mi TV 4A' });
    const sent = JSON.stringify({ headers, body });
    for (const secret of [token, 'owner@example.com', 'prof-7781', TV_A, '4321']) assert.ok(!sent.includes(secret), secret);
    // The model's part (Sentra takes `sentra` out before any model) holds no name either.
    assert.ok(!JSON.stringify(body.messages).includes('Юра'));
  }
});

test('a TV that says nothing of itself is asked as before: no line for Sentra, counted by its address', async () => {
  const e = env({ AI_LIMIT: limiter(12) });
  const seen = [];
  sentra([NONE], seen);
  await post(e, '/ai/command', { text: 'новини' }, { ip: '203.0.113.9' });
  assert.equal(seen[0].body.sentra, undefined);
  assert.deepEqual([...e.AI_LIMIT.counts.keys()], ['ip:203.0.113.9']);
});

test('two TVs with one profile share a count; another profile counts apart', async () => {
  const e = env({ AI_LIMIT: limiter(12) });
  // One account signed in on two TVs at two addresses: each TV holds a token of its own.
  const tokenA = await signin(e, 'home@example.com', '198.51.100.1');
  const tokenB = await signin(e, 'home@example.com', '198.51.100.2');
  assert.notEqual(tokenA, tokenB);
  sentra([NONE], []);
  for (let i = 0; i < 12; i++) {
    const r = await post(e, '/ai/command', { text: 'новини', who: who('u0', 'основний', TV_A, 'Вітальня') }, { token: tokenA, ip: '198.51.100.1' });
    assert.equal(r.status, 200);
  }
  const sameProfile = await post(e, '/ai/command', { text: 'новини', who: who('u0', 'основний', TV_B, 'Спальня') }, { token: tokenB, ip: '198.51.100.2' });
  assert.equal(sameProfile.status, 429);
  const otherProfile = await post(e, '/ai/command', { text: 'новини', who: who('u10', '10', TV_B, 'Спальня') }, { token: tokenB, ip: '198.51.100.2' });
  assert.equal(otherProfile.status, 200);
  const counted = [...e.AI_LIMIT.counts].map(([key, n]) => [key.replace(/^a:[0-9a-f]{24}:/, 'a:<account>:'), n]);
  assert.deepEqual(counted, [['a:<account>:p:u0', 13], ['a:<account>:p:u10', 1]]);
});

test('without a valid account each TV counts by itself, and every address has a ceiling', async () => {
  const e = env({ AI_LIMIT: limiter(12), AI_ADDRESS: limiter(24) });
  sentra([NONE], []);
  await post(e, '/ai/command', { text: 'новини', who: who('u0', 'основний', TV_A, 'Вітальня') });
  // A made-up token is no account: the TV is counted by itself, not by a profile of someone's account.
  await post(e, '/ai/command', { text: 'новини', who: who('u0', 'основний', TV_B, 'Спальня') }, { token: 'ab'.repeat(24) });
  assert.deepEqual([...e.AI_LIMIT.counts.keys()], [`d:${TV_A}:p:u0`, `d:${TV_B}:p:u0`]);
  // Made-up TV ids from one address still run into that address's ceiling.
  let last;
  for (let i = 0; i < 23; i++) {
    last = await post(e, '/ai/command', { text: 'новини', who: who('u0', '', `${TV_A.slice(0, -2)}${String(i).padStart(2, '0')}`, '') });
  }
  assert.equal(last.status, 429);
  assert.equal(e.AI_ADDRESS.counts.get('198.51.100.1'), 25);
});

test('fields too long or in odd characters count as not sent; names are cleaned and the line kept short', async () => {
  const e = env();
  const req = (headers = {}) => new Request(BASE + '/ai/command', { method: 'POST', headers: { 'cf-connecting-ip': '192.0.2.7', ...headers } });
  const odd = await asker(req(), e, { who: who('u 0;drop', 'Ю‮ра\n<b>', 'short', 'TV') });
  assert.deepEqual(odd, { key: 'ip:192.0.2.7', memory: '', who: 'профіль Ю ра b · TV' });
  const long = await asker(req(), e, { who: who('x'.repeat(33), 'Олександра-Катерина Петренко', TV_A, 'Xiaomi Mi TV 4A 55" (вітальня, біля вікна)') });
  assert.equal(long.key, `d:${TV_A}`);
  assert.equal(long.who, 'профіль Олександра-Ка · Xiaomi Mi TV 4A');
  const longest = await asker(req(), e, { who: who('u0', 'Щ'.repeat(99), TV_A, 'Ж'.repeat(99)) });
  assert.equal(longest.who.length, 40);
  assert.deepEqual(await asker(req(), e, { who: 'me' }), { key: 'ip:192.0.2.7', memory: '', who: '' });
  assert.deepEqual(await asker(req(), e, null), { key: 'ip:192.0.2.7', memory: '', who: '' });
});

test('picks do not offer one profile again what it was shown, from either of its TVs', async () => {
  const e = env();
  const tokenA = await signin(e, 'home@example.com');
  const tokenB = await signin(e, 'home@example.com');
  const seen = [];
  sentra([
    { picks: [film('Paddington 2', 2017), film('The Grand Budapest Hotel', 2014)] },
    // The model offers Paddington again anyway: the TV does not get it twice.
    { picks: [film('paddington 2', 2017), film('Hot Fuzz', 2007)] },
  ], seen);
  const first = await post(e, '/ai/picks', { request: 'щось смішне', who: who('u0', 'основний', TV_A, 'Вітальня') }, { token: tokenA });
  assert.deepEqual((await first.json()).picks.map((p) => p.title), ['Paddington 2', 'The Grand Budapest Hotel']);
  const second = await post(e, '/ai/picks', { request: 'щось смішне', who: who('u0', 'основний', TV_B, 'Спальня') }, { token: tokenB });
  assert.deepEqual((await second.json()).picks.map((p) => p.title), ['Hot Fuzz']);
  assert.equal(seen[0].body.messages[1].content, 'Request: щось смішне');
  assert.equal(seen[1].body.messages[1].content,
    'Request: щось смішне\nAlready suggested to this viewer lately, suggest others: Paddington 2 (2017); The Grand Budapest Hotel (2014)');
  // Newest first, kept for days, not for good.
  const [key] = [...e.ACCOUNTS.data.keys()].filter((k) => k.startsWith('h:'));
  assert.deepEqual(JSON.parse(e.ACCOUNTS.data.get(key).value).map((s) => s.title), ['Hot Fuzz', 'Paddington 2', 'The Grand Budapest Hotel']);
});

test('another profile, or a TV without an account, keeps a memory of its own', async () => {
  const e = env();
  const token = await signin(e, 'home@example.com');
  const seen = [];
  sentra([{ picks: [film('Paddington 2', 2017)] }], seen);
  await post(e, '/ai/picks', { request: 'щось смішне', who: who('u0', 'основний', TV_A, 'Вітальня') }, { token });
  await post(e, '/ai/picks', { request: 'щось смішне', who: who('u10', '10', TV_A, 'Вітальня') }, { token });
  await post(e, '/ai/picks', { request: 'щось смішне', who: who('u0', 'основний', TV_B, 'Кухня') });
  // Nothing the first profile was shown reaches the other two.
  assert.deepEqual(seen.map((s) => s.body.messages[1].content), Array(3).fill('Request: щось смішне'));
  const kept = [...e.ACCOUNTS.data.keys()].filter((k) => k.startsWith('h:')).map((k) => k.replace(/^h:a:[0-9a-f]{24}:/, 'h:a:<account>:'));
  assert.deepEqual(kept, ['h:a:<account>:p:u0', 'h:a:<account>:p:u10', `h:d:${TV_B}:p:u0`]);
  // An address alone is never remembered: one address can be a whole building.
  await post(e, '/ai/picks', { request: 'щось смішне' });
  assert.equal([...e.ACCOUNTS.data.keys()].filter((k) => k.startsWith('h:')).length, 3);
});

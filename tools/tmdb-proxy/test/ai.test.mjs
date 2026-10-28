// The AI routes (ai.js) as Sentra sees them: what a spoken command costs in prompt, how picks are cached, and that
// a hung Sentra or a retired route never leaves the TV waiting or getting a 500.
import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../worker.js';

const BASE = 'https://tv-launcher-tmdb.test';
const ENV = { SENTRA_URL: 'https://sentra.test', SENTRA_TOKEN: 't', TMDB_KEY: 'k' };
const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; });

const post = (path, body) => worker.fetch(new Request(BASE + path, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
}), ENV, { waitUntil() {} });

/** Sentra answering `answer`, with each request it got kept in `seen`. */
function sentra(answer, seen) {
  globalThis.fetch = async (url, init = {}) => {
    if (String(url).startsWith('https://sentra.test')) {
      seen.push({ headers: init.headers, body: JSON.parse(init.body) });
      return Response.json({ choices: [{ message: { content: JSON.stringify(answer) } }] });
    }
    return Response.json({ results: [] });   // TMDB finds nothing
  };
}

test('a command names each channel app once and keeps the lists short', async () => {
  const seen = [];
  sentra({ action: 'none', app: '', channel_id: '', title: '', query: '', say: 'ні' }, seen);
  const pkg = 'ua.kyivstar.tv.long.package.name';
  const channels = Array.from({ length: 300 }, (_, i) => ({ id: 'c' + i, name: 'Канал ' + i, app: pkg }));
  const r = await post('/ai/command', { text: 'новини', apps: [{ pkg, label: 'Київстар ТБ' }], channels, titles: [] });
  assert.equal(r.status, 200);
  const user = seen[0].body.messages[1].content;
  assert.equal(user.split(pkg).length - 1, 2);   // once in Apps, once over its channels
  assert.ok(user.includes('c149 | Канал 149'));
  assert.ok(!user.includes('c150 |'));
  assert.equal(seen[0].headers['x-sentra-skill'], 'tv-command');
});

test('picks are asked in one spelling and cached for days', async () => {
  const seen = [];
  sentra({ picks: [] }, seen);
  await post('/ai/picks', { request: '  Щось   СМІШНЕ на вечір ' });
  await post('/ai/picks', { request: 'щось смішне на вечір' });
  assert.equal(seen[0].body.messages[1].content, 'Request: щось смішне на вечір');
  assert.equal(seen[1].body.messages[1].content, seen[0].body.messages[1].content);
  assert.ok(Number(seen[0].headers['x-sentra-cache']) >= 86400);
});

test('a command keeps its action when the model leaves out the empty fields or wraps the object in words', async () => {
  // 28.10: a light model answered {action, query} only; the missing channel_id, title, say made the answer «unusable»
  // and every spoken request became a plain search.
  sentra({ action: 'picks', query: 'щось смішне на вечір' }, []);
  const r = await post('/ai/command', { text: 'увімкни щось смішне на вечір' });
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { action: 'picks', query: 'щось смішне на вечір', app: '', channel_id: '', title: '', say: '' });
  globalThis.fetch = async () => Response.json({ choices: [{ message: { content: 'Ось: {"action":"open_app","app":"com.netflix.ninja"} — готово' } }] });
  assert.equal((await (await post('/ai/command', { text: 'нетфлікс' })).json()).action, 'open_app');
  // An action outside the list is never guessed: the TV searches for the words.
  sentra({ action: 'dance', query: 'x' }, []);
  assert.equal((await (await post('/ai/command', { text: 'танцюй' })).json()).action, 'search');
});

test('a Sentra that never answers ends in a quick error, not a hung TV', async () => {
  // The call carries a time limit; when it runs out (here at once, as AbortSignal.timeout's timer would) the TV
  // gets 504.
  let signal;
  globalThis.fetch = async (url, init) => {
    signal = init.signal;
    throw new DOMException('The operation was aborted due to timeout', 'TimeoutError');
  };
  const r = await post('/ai/command', { text: 'новини' });
  assert.ok(signal instanceof AbortSignal);
  assert.equal(r.status, 504);
});

test('the retired /ai/about is no AI route any more', async () => {
  const seen = [];
  sentra({ about: 'x', verdict: 'y' }, seen);
  const r = await post('/ai/about', { type: 'movie', id: 1 });
  assert.notEqual(r.status, 200);
  assert.equal(seen.length, 0);
});

test('a year written as text still counts, and one bad pick drops alone, not the whole list', async () => {
  const searched = [];
  globalThis.fetch = async (url) => {
    if (String(url).startsWith('https://sentra.test')) {
      return Response.json({ choices: [{ message: { content: JSON.stringify({ picks: [
        { title: 'Paddington 2', year: '2017', kind: 'movie', why: 'Тепла комедія.' },
        { title: 'Fleabag', year: '2016–2019', kind: 'tv', why: 'Смішно й гостро.' },
        { title: 'No Kind', year: 2001, why: 'Без виду.' },
        { title: 'The Grand Budapest Hotel', year: 2014, kind: 'movie', why: 'Яскрава пригода.' },
      ] }) } }] });
    }
    const u = new URL(url);
    searched.push({ query: u.searchParams.get('query'), year: u.searchParams.get('year') });
    return Response.json({ results: [{ id: searched.length, title: u.searchParams.get('query'), backdrop_path: '/b.jpg' }] });
  };
  const r = await post('/ai/picks', { request: 'щось смішне' });
  assert.equal(r.status, 200);
  const { picks } = await r.json();
  assert.deepEqual(picks.map((p) => p.title), ['Paddington 2', 'The Grand Budapest Hotel']);
  assert.deepEqual(searched, [{ query: 'Paddington 2', year: '2017' }, { query: 'The Grand Budapest Hotel', year: '2014' }]);
});

test('a command tells the model the TV language when the TV sends it', async () => {
  const seen = [];
  sentra({ action: 'none', app: '', channel_id: '', title: '', query: '', say: 'ні' }, seen);
  await post('/ai/command', { text: 'новини', lang: 'uk-UA' });
  await post('/ai/command', { text: 'новини' });
  assert.ok(seen[0].body.messages[1].content.startsWith('Request: новини\nLanguage: uk-UA\n\nApps:'));
  assert.ok(!seen[1].body.messages[1].content.includes('Language:'));
});

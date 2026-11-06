// The time a shelf's outside service (TMDB, Kick, Kyivstar, Spotify's charts) gets to answer. The TV gives up on the
// Worker after 12 s (ShelfFeed, KickFeed read timeouts), so the Worker answers first: a clear 504, not a hung row.
// Every body here is read whole (json, text), so the limit covers the body as well as the headers.

export const UPSTREAM_MS = 8000;

/** A fetch that ran out of time (AbortSignal.timeout) or was cut by its signal. */
export const timedOut = (e) => !!e && (e.name === 'TimeoutError' || e.name === 'AbortError');

/** 504 with `body` (the route's own empty shape) and a console.error for Sentra's monitoring; never cached. */
export function gatewayTimeout(what, body) {
  console.error(what, 'timed out after', UPSTREAM_MS, 'ms');
  return new Response(JSON.stringify({ ...body, error: 'upstream timeout' }), {
    status: 504,
    headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
  });
}

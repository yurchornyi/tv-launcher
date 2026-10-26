package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * What a player app tells the Now Playing card itself (KIK, its LauncherReport): the title with its season and
 * episode, poster and 16:9 backdrop URLs, where it stopped and how long it is. A low-RAM TV (TCL) hides media sessions
 * from the launcher, so there this is the card's only source; elsewhere it adds the picture and length KIK's session
 * lacks. Kept in preferences, so a launcher restarted by the TV's memory cleaner still has it, for six hours.
 * The launcher records the same for a video app that reports nothing and ends its session on Home (SmartTube,
 * NowPlayingListener), and keeps that app's recent videos' places for the shelf (place).
 */
public final class PlayerReport extends BroadcastReceiver {

    static final String ACTION = "com.gathin.tvhome.action.NOW_PLAYING";
    /** Asked of the player by Continue watching: play the film again (KIK's LauncherReport.RESUME). */
    static final String RESUME = "kik.intent.action.RESUME";
    /** The players that report; anything else is ignored. */
    private static final java.util.Set<String> PLAYERS = java.util.Collections.singleton("kik.app");
    private static final long MAX_AGE_MS = 6 * 60 * 60 * 1000L;
    private static final String PREFS = "player_report";

    /** One report: a film left at `position` of `duration` ms, at wall-clock `at`. */
    static final class Report {
        final String pkg, title, original, subtitle, poster, backdrop, open;
        final long position, duration, at;

        Report(String pkg, String title, String original, String subtitle, String poster, String backdrop, String open,
               long position, long duration, long at) {
            this.pkg = pkg;
            this.title = title;
            this.original = original;
            this.subtitle = subtitle;
            this.poster = poster;
            this.backdrop = backdrop;
            this.open = open;
            this.position = position;
            this.duration = duration;
            this.at = at;
        }

        /** The picture for the card: the wide frame, else the poster. */
        String picture() {
            return !backdrop.isEmpty() ? backdrop : poster;
        }
    }

    /** Called on the main thread when a report arrives or ends. */
    interface Listener { void onReport(); }

    private static Listener listener;

    static void listen(Listener l) {
        listener = l;
    }

    @Override
    public void onReceive(Context ctx, Intent i) {
        String pkg = i.getStringExtra("package");
        if (!ACTION.equals(i.getAction()) || pkg == null || !PLAYERS.contains(pkg)) return;
        SharedPreferences.Editor e = prefs(ctx).edit();
        if ("stopped".equals(i.getStringExtra("state"))) {
            for (String k : new String[]{"pkg", "title", "original", "subtitle", "poster", "backdrop", "open",
                    "position", "duration", "at"}) e.remove(k);   // the recorded places stay
            Log.i("TvHome.Report", pkg + ": stopped");
        } else {
            String title = i.getStringExtra("title");
            if (title == null || title.isEmpty()) return;
            e.putString("pkg", pkg).putString("title", title).putString("subtitle", text(i, "subtitle"))
                    .putString("original", text(i, "original"))
                    .putString("poster", url(i, "poster")).putString("backdrop", url(i, "backdrop"))
                    .putString("open", i.getStringExtra("open") == null ? "" : i.getStringExtra("open"))
                    .putLong("position", Math.max(0, i.getLongExtra("position", 0)))
                    .putLong("duration", Math.max(0, i.getLongExtra("duration", 0)))
                    .putLong("at", System.currentTimeMillis());
            Log.i("TvHome.Report", pkg + ": " + title + " at " + i.getLongExtra("position", 0) / 1000 + " s");
        }
        e.apply();
        if (listener != null) listener.onReport();
    }

    /** What the launcher saw a recorded app play (NowPlayingListener): the latest report, and the video's place. */
    static void record(Context ctx, String pkg, String title, String subtitle, String picture, String open,
                       long position, long duration) {
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit().putString("pkg", pkg).putString("title", title).putString("subtitle", subtitle)
                .putString("original", "").putString("poster", picture).putString("backdrop", "").putString("open", open)
                .putLong("position", position).putLong("duration", duration).putLong("at", System.currentTimeMillis());
        if (position > 0) {   // its length may be unknown here (SmartTube): the shelf has it from the channel
            try {
                org.json.JSONObject places = new org.json.JSONObject(p.getString("places", "{}"));
                places.put(pkg + "|" + title, new org.json.JSONArray().put(position).put(duration).put(System.currentTimeMillis()));
                // The newest forty: a place older than that is no longer on any shelf.
                while (places.length() > 40) {
                    String oldest = null;
                    long at = Long.MAX_VALUE;
                    for (java.util.Iterator<String> k = places.keys(); k.hasNext(); ) {
                        String key = k.next();
                        long t = places.getJSONArray(key).optLong(2);
                        if (t < at) { at = t; oldest = key; }
                    }
                    places.remove(oldest);
                }
                e.putString("places", places.toString());
            } catch (org.json.JSONException ignored) {
            }
        }
        e.apply();
        if (listener != null) listener.onReport();
    }

    /** Where the launcher saw this app's video left, as {position, duration}, or null. */
    static long[] place(Context ctx, String pkg, String title) {
        try {
            org.json.JSONArray a = new org.json.JSONObject(prefs(ctx).getString("places", "{}")).optJSONArray(pkg + "|" + title);
            return a == null ? null : new long[]{a.getLong(0), a.getLong(1)};
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /** The latest report, or null when none, the player closed, or it is older than six hours. */
    static Report latest(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String title = p.getString("title", null);
        long at = p.getLong("at", 0);
        long age = System.currentTimeMillis() - at;
        if (title == null || age < 0 || age > MAX_AGE_MS) return null;
        return new Report(p.getString("pkg", ""), title, p.getString("original", ""), p.getString("subtitle", ""), p.getString("poster", ""),
                p.getString("backdrop", ""), p.getString("open", ""), p.getLong("position", 0), p.getLong("duration", 0), at);
    }

    /**
     * Continue watching by the player's own way back (KIK: its open player to the front, playing, or the title from
     * where it was left when Android closed the player). False without one; the intent may only open that player.
     */
    static boolean open(Context ctx, Report r) {
        if (r == null || r.open.isEmpty()) return false;
        try {
            Intent i = Intent.parseUri(r.open, Intent.URI_INTENT_SCHEME);
            i.setSelector(null);
            String target = i.getComponent() != null ? i.getComponent().getPackageName() : i.getPackage();
            if (!r.pkg.equals(target)) return false;
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {   // a malformed URI, or the player is gone
            Log.w("TvHome.Report", "open: " + e);
            return false;
        }
    }

    /** Continue watching: the player plays again once it is back in front. */
    static void resume(Context ctx, String pkg) {
        if (!PLAYERS.contains(pkg)) return;
        try {
            ctx.sendBroadcast(new Intent(RESUME).setPackage(pkg));
        } catch (RuntimeException e) {
            Log.w("TvHome.Report", "resume: " + e);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String text(Intent i, String key) {
        String s = i.getStringExtra(key);
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** Only a web address (the card downloads it); the launcher's own saved pictures are file URLs. */
    private static String url(Intent i, String key) {
        String s = text(i, key);
        return s.startsWith("https://") ? s : "";
    }
}

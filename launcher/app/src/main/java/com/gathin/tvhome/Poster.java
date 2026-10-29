package com.gathin.tvhome;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The picture of a film or series a video app plays but sends none for (KIK, HDRezka: a title only), found on TMDB
 * by that title through the launcher's Worker (config.xml tmdb_proxy, /3/search/multi): its wide frame (backdrop),
 * which fills the top of the screen, else its poster. The picture itself is then a CoverArt URL. A title found once,
 * or not found, is not asked again until the launcher restarts.
 */
final class Poster {

    private static final String TAG = "TvHome.Poster";
    private static final String POSTERS = "https://image.tmdb.org/t/p/w500";
    /** The film's wide frame first: it fills the top of the screen behind the card, as the shelf's posters do. */
    private static final String BACKDROPS = "https://image.tmdb.org/t/p/w1280";
    /** Title → poster URL, "" when TMDB has none. */
    private static final Map<String, String> found = new HashMap<>();
    /** Title → running time in minutes (a film's, or a series' usual episode), for how much is left to watch. */
    private static final Map<String, Integer> minutes = new HashMap<>();
    private static final Set<String> asking = new HashSet<>();

    private Poster() { }

    /** The poster URL for the title, or null when it is not known (yet, or at all). */
    static synchronized String cached(String title) {
        String url = found.get(clean(title));
        return url == null || url.isEmpty() ? null : url;
    }

    /** The title's running time in milliseconds, or 0 when TMDB gave none. */
    static synchronized long runtime(String title) {
        Integer m = minutes.get(clean(title));
        return m == null ? 0 : m * 60_000L;
    }

    /**
     * Asks TMDB on a worker thread; `done` runs (on that thread) once a poster URL is known. `original` (the player's
     * original title, may be null) is asked when the title finds nothing: TMDB has no Ukrainian name for many shows.
     */
    static void find(String proxy, String title, String original, Runnable done) {
        String query = clean(title);
        if (proxy == null || proxy.trim().isEmpty() || query.isEmpty()) return;
        synchronized (Poster.class) {
            if (found.containsKey(query) || !asking.add(query)) return;
        }
        String base = proxy.trim().endsWith("/") ? proxy.trim() : proxy.trim() + "/";
        new Thread(() -> {
            String url = "";
            int length = 0;
            try {
                java.util.Locale l = java.util.Locale.getDefault();
                String lang = l.getLanguage() + (l.getCountry().isEmpty() ? "" : "-" + l.getCountry());
                String alt = clean(original);
                JSONArray results = search(base, query, lang);
                if ((results == null || results.length() == 0) && !alt.isEmpty() && !alt.equalsIgnoreCase(query)) {
                    results = search(base, alt, lang);
                }
                // TMDB ranks by its own relevance, which puts a little-known "1+1 удома" before Home Alone for
                // "Один удома" (KIK's name for it). A title spelled exactly the same wins; otherwise the best known.
                int best = -1;
                boolean bestExact = false;
                String details = null;
                for (int i = 0; results != null && i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    String type = r.optString("media_type");
                    String path = r.optString("poster_path", "");
                    String wide = r.optString("backdrop_path", "");
                    if (!("movie".equals(type) || "tv".equals(type)) || !(path.startsWith("/") || wide.startsWith("/"))) continue;
                    String name = r.optString("title", r.optString("name")),
                            first = r.optString("original_title", r.optString("original_name"));
                    boolean exact = same(query, name) || same(query, first) || (!alt.isEmpty() && same(alt, first));
                    int votes = r.optInt("vote_count");
                    if (best < 0 || (exact && !bestExact) || (exact == bestExact && votes > best)) {
                        best = votes;
                        bestExact = exact;
                        url = wide.startsWith("/") ? BACKDROPS + wide : POSTERS + path;
                        details = type + "/" + r.optLong("id");
                    }
                }
                // Its running time: a film's runtime, a series' episode length (the last aired one's, else the usual).
                if (details != null) {
                    try {
                        JSONObject d = new JSONObject(get(base + "3/" + details));
                        length = d.optInt("runtime");
                        JSONObject last = d.optJSONObject("last_episode_to_air");
                        if (length <= 0 && last != null) length = last.optInt("runtime");
                        JSONArray usual = d.optJSONArray("episode_run_time");
                        if (length <= 0 && usual != null && usual.length() > 0) length = usual.optInt(0);
                    } catch (Exception e) {
                        Log.w(TAG, "details failed: " + e);   // the picture still shows, without what is left
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "search failed: " + e);
                synchronized (Poster.class) {
                    asking.remove(query);   // the network, not the title: try again with the next track
                }
                return;
            }
            synchronized (Poster.class) {
                asking.remove(query);
                found.put(query, url);
                if (length > 0) minutes.put(query, length);
            }
            if (!url.isEmpty()) done.run();
        }, "poster").start();
    }

    /**
     * The show's own name, without the season or episode a player may add ("Сімпсони. Сезон 3, серія 5") or the
     * second name KIK gives after a slash ("Шлях безсмертного / Подорож до безсмертя").
     */
    static String clean(String title) {
        if (title == null) return "";
        String t = first(title.split("\\s+/\\s+"));
        t = first(t.split("(?iu)\\s*([.,:|(\\[–—-]\\s*)?(сезон|season|серія|серия|episode|s\\d{1,2}\\s*e\\d)"));
        return t.replaceAll("[\\s.,:|–—-]+$", "").trim();
    }

    /** split()'s first part; split() gives none at all for a title that is only the separator ("Episode"). */
    private static String first(String[] parts) {
        return parts.length == 0 ? "" : parts[0];
    }

    private static boolean same(String a, String b) {
        return a.replaceAll("[^\\p{L}\\p{N}]+", "").equalsIgnoreCase(b.replaceAll("[^\\p{L}\\p{N}]+", ""));
    }

    private static JSONArray search(String base, String query, String lang) throws Exception {
        return new JSONObject(get(base + "3/search/multi?query=" + URLEncoder.encode(query, "UTF-8") + "&language=" + lang))
                .optJSONArray("results");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 512 * 1024) throw new IllegalStateException("too large");
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}

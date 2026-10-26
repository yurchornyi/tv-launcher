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
    private static final Set<String> asking = new HashSet<>();

    private Poster() { }

    /** The poster URL for the title, or null when it is not known (yet, or at all). */
    static synchronized String cached(String title) {
        String url = found.get(clean(title));
        return url == null || url.isEmpty() ? null : url;
    }

    /** Asks TMDB on a worker thread; `done` runs (on that thread) once a poster URL is known. */
    static void find(String proxy, String title, Runnable done) {
        String query = clean(title);
        if (proxy == null || proxy.trim().isEmpty() || query.isEmpty()) return;
        synchronized (Poster.class) {
            if (found.containsKey(query) || !asking.add(query)) return;
        }
        String base = proxy.trim().endsWith("/") ? proxy.trim() : proxy.trim() + "/";
        new Thread(() -> {
            String url = "";
            try {
                java.util.Locale l = java.util.Locale.getDefault();
                String lang = l.getLanguage() + (l.getCountry().isEmpty() ? "" : "-" + l.getCountry());
                JSONArray results = new JSONObject(get(base + "3/search/multi?query=" + URLEncoder.encode(query, "UTF-8")
                        + "&language=" + lang)).optJSONArray("results");
                // TMDB ranks by its own relevance, which puts a little-known "1+1 удома" before Home Alone for
                // "Один удома" (KIK's name for it). A title spelled exactly the same wins; otherwise the best known.
                int best = -1;
                boolean bestExact = false;
                for (int i = 0; results != null && i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    String type = r.optString("media_type");
                    String path = r.optString("poster_path", "");
                    String wide = r.optString("backdrop_path", "");
                    if (!("movie".equals(type) || "tv".equals(type)) || !(path.startsWith("/") || wide.startsWith("/"))) continue;
                    boolean exact = same(query, r.optString("title", r.optString("name")))
                            || same(query, r.optString("original_title", r.optString("original_name")));
                    int votes = r.optInt("vote_count");
                    if (best < 0 || (exact && !bestExact) || (exact == bestExact && votes > best)) {
                        best = votes;
                        bestExact = exact;
                        url = wide.startsWith("/") ? BACKDROPS + wide : POSTERS + path;
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
            }
            if (!url.isEmpty()) done.run();
        }, "poster").start();
    }

    /** The show's own name, without the season or episode a player may add ("Сімпсони. Сезон 3, серія 5"). */
    static String clean(String title) {
        if (title == null) return "";
        String t = title.split("(?i)\\s*([.,:|(\\[–—-]\\s*)?(сезон|season|серія|серия|episode|s\\d{1,2}\\s*e\\d)")[0];
        return t.replaceAll("[\\s.,:|–—-]+$", "").trim();
    }

    private static boolean same(String a, String b) {
        return a.replaceAll("[^\\p{L}\\p{N}]+", "").equalsIgnoreCase(b.replaceAll("[^\\p{L}\\p{N}]+", ""));
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

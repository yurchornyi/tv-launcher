package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Covers a player names by URL rather than sending as a bitmap (NowPlaying): fetched once, kept in memory for the
 * last few tracks, 12 MB at most (six counted by number were up to 22 MB of 1280 px frames on a 1 GB TV), and let go
 * when Home is left (trim). A URL that failed is not tried again until the launcher restarts.
 */
final class CoverArt {

    private static final String TAG = "TvHome.CoverArt";
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String url, Bitmap b) {
            return b.getAllocationByteCount();
        }
    };
    private static final Set<String> loading = new HashSet<>();
    private static final Set<String> failed = new HashSet<>();

    private CoverArt() { }

    static synchronized Bitmap cached(String url) {
        return cache.get(url);
    }

    /** Home went to the background (MainActivity.onStop): the covers go, what is on the card keeps its own. */
    static synchronized void trim() {
        cache.evictAll();
    }

    /** Downloads the picture on a worker thread; `done` is called (on that thread) once it is cached. */
    static synchronized void fetch(String url, Runnable done) {
        if (cache.get(url) != null || failed.contains(url) || !loading.add(url)) return;
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                java.net.URLConnection c = new URL(url).openConnection();   // https, or a picture saved here (file:)
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                byte[] data;
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 8 * 1024 * 1024) throw new java.io.IOException("cover too large");
                    }
                    data = out.toByteArray();
                }
                // Podcast art is often 3000 px (36 MB decoded): read the size first and decode a smaller copy.
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                // A film's wide frame fills the screen (Poster): it keeps 1280 px; a square cover needs no more than 800.
                int max = o.outWidth > o.outHeight * 13 / 10 ? 1280 : 800;
                int sample = 1;
                while (o.outWidth / (sample * 2) >= max) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (bmp != null && bmp.getWidth() > max) {
                    bmp = Bitmap.createScaledBitmap(bmp, max, max * bmp.getHeight() / bmp.getWidth(), true);
                }
            } catch (Exception | OutOfMemoryError e) {   // an OutOfMemoryError here would take the launcher down
                Log.w(TAG, "cover failed: " + e);
                bmp = null;
            }
            synchronized (CoverArt.class) {
                loading.remove(url);
                if (bmp != null) cache.put(url, bmp);
                else failed.add(url);
            }
            if (bmp != null) done.run();
        }, "cover-art").start();
    }
}

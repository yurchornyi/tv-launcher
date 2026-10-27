package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The AI features through the launcher's Worker (config.xml tmdb_proxy, tools/tmdb-proxy/ai.js), which holds
 * SENTRA_TOKEN and asks Sentra, the owner's AI centre: a spoken command as one action, and picks for a mood.
 * Answers come back on the main thread; null means "not now" (no network, no Sentra token on the Worker, too many
 * requests) and the caller falls back to what works without AI.
 */
final class AiClient {

    interface Answer { void onAnswer(JSONObject answer); }

    private static final String TAG = "TvHome.Ai";

    /** How long to go without AI after the Worker said it has no Sentra token, before asking again. */
    private static final long OFF_MS = 10 * 60 * 1000;

    private final String base;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Until when (uptime) not to ask: the Worker answered 503, it has no Sentra token right now. */
    private volatile long offUntil;

    AiClient(Context ctx, String proxy) {
        String p = proxy == null ? "" : proxy.trim();
        base = p.isEmpty() ? "" : (p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
        // The film lines (/ai/about) are retired; their old cache on the TV goes with them.
        ctx.getApplicationContext().deleteSharedPreferences("ai_about");
    }

    boolean enabled() {
        return !base.isEmpty() && SystemClock.uptimeMillis() >= offUntil;
    }

    void command(JSONObject body, Answer answer) {
        post("/ai/command", body, answer);
    }

    void picks(String request, Answer answer) {
        try {
            post("/ai/picks", new JSONObject().put("request", request).put("lang", language()), answer);
        } catch (Exception e) {
            answer.onAnswer(null);
        }
    }

    static String language() {
        java.util.Locale l = java.util.Locale.getDefault();
        return l.getLanguage() + (l.getCountry().isEmpty() ? "" : "-" + l.getCountry());
    }

    private void post(String path, JSONObject body, Answer answer) {
        if (!enabled() || io.isShutdown()) {
            answer.onAnswer(null);
            return;
        }
        io.execute(() -> {
            JSONObject out = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(25000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                if (code == 503) offUntil = SystemClock.uptimeMillis() + OFF_MS;   // no Sentra token on the Worker yet
                if (code == 200) {
                    try (InputStream in = c.getInputStream()) {
                        out = new JSONObject(read(in));
                    }
                } else {
                    Log.w(TAG, path + " answered " + code);
                }
            } catch (Exception e) {
                Log.w(TAG, path + " failed: " + e);
            }
            JSONObject result = out;
            main.post(() -> answer.onAnswer(result));
        });
    }

    void close() {
        io.shutdownNow();
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}

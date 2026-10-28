package com.gathin.tvhome;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The AI features through the launcher's Worker (config.xml tmdb_proxy, tools/tmdb-proxy/ai.js), which holds
 * SENTRA_TOKEN and asks Sentra, the owner's AI centre: a spoken command as one action, and picks for a mood.
 * Answers come back on the main thread; null means "not now" (no network, no Sentra token on the Worker, too many
 * requests) and the caller falls back to what works without AI. Each request says who asks (Who).
 */
final class AiClient {

    interface Answer { void onAnswer(JSONObject answer); }

    private static final String TAG = "TvHome.Ai";

    /** How long to go without AI after the Worker said it has no Sentra token, before asking again. */
    private static final long OFF_MS = 10 * 60 * 1000;

    private final Context app;
    private final String base;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Until when (uptime) not to ask: the Worker answered 503, it has no Sentra token right now. */
    private volatile long offUntil;

    AiClient(Context ctx, String proxy) {
        String p = proxy == null ? "" : proxy.trim();
        base = p.isEmpty() ? "" : (p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
        app = ctx.getApplicationContext();
        // The film lines (/ai/about) are retired; their old cache on the TV goes with them.
        app.deleteSharedPreferences("ai_about");
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
                Who who = Who.of(app);
                body.put("who", new JSONObject(who.fields()));
                HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(25000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (who.authorization() != null) c.setRequestProperty("Authorization", who.authorization());
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

    /**
     * Who asks, sent with every AI request (the Worker's ai.js asker): the account by this TV's sign-in token, in the
     * Authorization header as for the account's own calls (never the e-mail or the PIN); the Android profile the
     * launcher runs in; and this TV. The Worker counts and remembers per account and profile, and passes on only the
     * profile's and the TV's names, for the owner to read in Sentra.
     */
    static final class Who {
        /** What the owner reads for the TV's main profile: Android gives a launcher no profile's own name. */
        static final String MAIN_PROFILE = "основний";

        final String token, profileId, profileName, deviceId, deviceName;

        Who(String token, String profileId, String profileName, String deviceId, String deviceName) {
            this.token = text(token);
            this.profileId = text(profileId);
            this.profileName = text(profileName);
            this.deviceId = text(deviceId);
            this.deviceName = text(deviceName);
        }

        /**
         * This TV now: its sign-in ("" signed out), the profile by its serial number (u0 the main one, the same on
         * every TV, so one account's main profile is one asker everywhere), the id Account made for this TV once, and
         * the TV's name as set in its settings, else its model.
         */
        static Who of(Context ctx) {
            UserManager users = ctx.getSystemService(UserManager.class);
            long serial = users == null ? 0 : users.getSerialNumberForUser(android.os.Process.myUserHandle());
            boolean main = users == null || users.isSystemUser();
            String tv = Settings.Global.getString(ctx.getContentResolver(), Settings.Global.DEVICE_NAME);
            if (tv == null || tv.trim().isEmpty()) tv = Build.MANUFACTURER + " " + Build.MODEL;
            return new Who(Account.token(ctx), "u" + serial, main ? MAIN_PROFILE : String.valueOf(serial),
                    Account.deviceId(ctx), tv);
        }

        /** The Authorization header for the account; null while signed out, and then nothing names an account. */
        String authorization() {
            return token.isEmpty() ? null : "Bearer " + token;
        }

        /** The body's "who": {"profile": {"id", "name"}, "device": {"id", "name"}}; the token never goes in it. */
        Map<String, Object> fields() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profile", pair(profileId, profileName));
            out.put("device", pair(deviceId, deviceName));
            return out;
        }

        private static Map<String, String> pair(String id, String name) {
            Map<String, String> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("name", name);
            return out;
        }

        /** Trimmed, at most 64 characters: the Worker checks each field again and drops what does not fit. */
        private static String text(String s) {
            String t = s == null ? "" : s.trim();
            return t.length() > 64 ? t.substring(0, 64) : t;
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}

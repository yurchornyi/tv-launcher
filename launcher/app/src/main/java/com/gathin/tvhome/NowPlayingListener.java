package com.gathin.tvhome;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Exists so Android lets the launcher read media sessions for the Now Playing card (NowPlaying). It reads no
 * notifications. While it is connected it also follows the music apps' sessions (ShelfFeed.music), so every song
 * played there, not only the one playing when Home comes back, joins the music shelves (Listened). It reads only the
 * title and artist, never the cover. build-and-install enables it with `cmd notification allow_listener`.
 *
 * A video app whose session ends as Home comes back (SmartTube: RECORDED) is followed too, and what it played is kept
 * as a report (PlayerReport.record): title, channel, picture, place and length, for the Now Playing card's Continue
 * after Home and for its videos' Continue on the shelf. Only where Android shows sessions at all (not a low-RAM TV).
 */
public class NowPlayingListener extends NotificationListenerService {

    private MediaSessionManager msm;
    private final List<MediaController> watched = new ArrayList<>();

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { recordAll(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { recordAll(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::watch;

    /** Video apps the listener records (see the class comment). */
    static final java.util.Set<String> RECORDED = java.util.Collections.singleton("org.smarttube.stable");
    private final List<Recorder> recorders = new ArrayList<>();

    @Override
    public void onListenerConnected() {
        msm = getSystemService(MediaSessionManager.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessions, new ComponentName(this, NowPlayingListener.class));
            watch(msm.getActiveSessions(new ComponentName(this, NowPlayingListener.class)));
        } catch (RuntimeException e) {
            msm = null;
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        for (Recorder r : recorders) r.controller.unregisterCallback(r);
        recorders.clear();
        for (MediaController c : watched) c.unregisterCallback(callback);
        watched.clear();
        msm = null;
    }

    private void watch(List<MediaController> all) {
        for (MediaController c : watched) c.unregisterCallback(callback);
        watched.clear();
        for (Recorder r : recorders) r.controller.unregisterCallback(r);
        recorders.clear();
        if (all == null) return;
        for (MediaController c : all) {
            if (RECORDED.contains(c.getPackageName())) {
                Recorder r = new Recorder(c);
                c.registerCallback(r);
                recorders.add(r);
                // Only read here unless it plays: this runs whenever any app's session comes or goes, and writing
                // then put SmartTube's paused video over a newer report (KIK's) as the latest.
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) r.save(true);
                else r.read();
                continue;
            }
            if (!ShelfFeed.music(c.getPackageName())) continue;
            c.registerCallback(callback);
            watched.add(c);
        }
        recordAll();
    }

    private void recordAll() {
        for (MediaController c : watched) {
            try {
                MediaMetadata m = c.getMetadata();
                PlaybackState s = c.getPlaybackState();
                if (m == null || s == null || s.getState() != PlaybackState.STATE_PLAYING) continue;
                String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                Listened.record(this, new NowPlaying.Track(m.getString(MediaMetadata.METADATA_KEY_TITLE), artist, "",
                        c.getPackageName(), null, 0, true));
            } catch (RuntimeException ignored) {
                // the player's process died mid-call
            }
        }
    }

    /**
     * One recorded video app's session: what it plays, saved every few seconds while it plays and at once when it
     * pauses, changes video or ends (its place then is the last known one, moved on by the time it kept playing).
     */
    private final class Recorder extends MediaController.Callback {
        final MediaController controller;
        private long savedAt;
        private String title, channel, picture, open;
        private long duration;
        private PlaybackState last;

        Recorder(MediaController controller) {
            this.controller = controller;
        }

        @Override public void onPlaybackStateChanged(PlaybackState state) {
            last = state;
            save(state == null || state.getState() != PlaybackState.STATE_PLAYING);
        }
        @Override public void onMetadataChanged(MediaMetadata metadata) { save(true); }
        @Override public void onSessionDestroyed() { write(); }

        void save(boolean now) {
            if (!now && android.os.SystemClock.elapsedRealtime() - savedAt < 5000) return;
            if (read()) write();
        }

        /** Takes in what the session shows now; false when it has no title or the player's process died mid-call. */
        boolean read() {
            try {
                MediaMetadata m = controller.getMetadata();
                PlaybackState s = controller.getPlaybackState();
                if (s != null) last = s;
                String t = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (t == null || t.isEmpty()) return false;
                if (android.util.Log.isLoggable("TvHome.Meta", android.util.Log.DEBUG)) {
                    StringBuilder sb = new StringBuilder();
                    for (String k : m.keySet()) {
                        Object v = m.getBitmap(k) != null ? m.getBitmap(k).getWidth() + "x" + m.getBitmap(k).getHeight() + " bitmap" : null;
                        if (v == null) try { v = m.getString(k); } catch (Exception e) { v = "?"; }
                        sb.append(k).append('=').append(v).append("; ");
                    }
                    android.util.Log.d("TvHome.Meta", controller.getPackageName() + " (recorded): " + sb);
                }
                if (!t.equals(title)) picture = picture(m, t);
                title = t;
                String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                channel = a == null ? "" : a;
                duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                open = open(m);
            } catch (RuntimeException e) {   // the player's process died mid-call: keep what was read before
                return false;
            }
            return true;
        }

        /** The report as last read, its place moved on by however long it has been playing since. */
        private void write() {
            if (title == null) return;
            long pos = 0;
            if (last != null) {
                pos = last.getPosition();
                if (last.getState() == PlaybackState.STATE_PLAYING && last.getLastPositionUpdateTime() > 0) {
                    pos += (long) ((android.os.SystemClock.elapsedRealtime() - last.getLastPositionUpdateTime()) * last.getPlaybackSpeed());
                }
            }
            if (duration > 0) pos = Math.min(pos, duration);
            savedAt = android.os.SystemClock.elapsedRealtime();
            PlayerReport.record(NowPlayingListener.this, controller.getPackageName(), title, channel, picture, open,
                    Math.max(0, pos), duration);
        }

        /**
         * The video's picture: YouTube's own thumbnail for its id (the shelf takes the largest there is; the session's
         * signed hq720 address answers 404 when fetched again), else its URL, else the bitmap, saved once per video.
         */
        private String picture(MediaMetadata m, String t) {
            String id = ChannelFeed.videoId("?v=" + m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
            if (id != null) return "https://i.ytimg.com/vi/" + id + "/sddefault.jpg";
            for (String k : new String[]{MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}) {
                String u = m.getString(k);
                if (u != null && u.startsWith("https://")) return u;
            }
            android.graphics.Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (b == null) return "";
            java.io.File f = new java.io.File(getCacheDir(), "recorded-" + Integer.toHexString(t.hashCode()) + ".jpg");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
                return "file://" + f.getAbsolutePath();
            } catch (java.io.IOException e) {
                return "";
            }
        }

        /** The video itself in its app, from its id when the session names it (a YouTube id), else the app. */
        private String open(MediaMetadata m) {
            String id = ChannelFeed.videoId("?v=" + m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
            android.content.Intent i;
            if (id != null) {
                i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.youtube.com/watch?v=" + id)).setPackage(controller.getPackageName());
            } else {
                i = getPackageManager().getLeanbackLaunchIntentForPackage(controller.getPackageName());
                if (i == null) return "";
            }
            return i.toUri(android.content.Intent.URI_INTENT_SCHEME);
        }
    }
}

package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * NowPlaying.overTile: a film left paused (KIK's report of last night's Simpsons) stays on KIK's own tile; Home opening
 * on SmartTube shows SmartTube's shelf, not that film with a close button to press.
 */
public class NowPlayingOverTileTest {

    private static final String KIK = "kik.app", TUBE = "org.smarttube.stable", SPOTIFY = "com.spotify.tv.android";
    private static final List<String> DOCK = Arrays.asList(TUBE, "com.kyivstar.tv.androidtv", "com.netflix.ninja", KIK);

    @Test
    public void aPausedFilmStaysOnItsOwnTile() {
        assertFalse(NowPlaying.overTile(KIK, false, true, TUBE, DOCK));
        assertFalse(NowPlaying.overTile(KIK, false, true, "com.netflix.ninja", DOCK));
        assertTrue(NowPlaying.overTile(KIK, false, true, KIK, DOCK));
    }

    @Test
    public void somethingPlayingShowsOverEveryTile() {
        assertTrue(NowPlaying.overTile(KIK, true, true, TUBE, DOCK));
        assertTrue(NowPlaying.overTile(SPOTIFY, true, false, TUBE, DOCK));
    }

    @Test
    public void pausedMusicKeepsItsPlayButtonEverywhere() {
        assertTrue(NowPlaying.overTile(SPOTIFY, false, false, TUBE, DOCK));
    }

    @Test
    public void aFilmWhoseAppIsNotInTheDockShowsOverAll() {
        assertTrue(NowPlaying.overTile("com.rezka.app", false, true, TUBE, DOCK));
    }
}

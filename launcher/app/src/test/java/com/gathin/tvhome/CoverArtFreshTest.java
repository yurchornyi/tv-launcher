package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * NowPlaying.Art: Spotify's first update for a new song carries the new cover URL with the last song's picture; that
 * picture is never the new song's cover, while every other picture is.
 */
public class CoverArtFreshTest {

    private static final long A = 11, B = 22;

    @Test
    public void theLastSongsPictureWithANewUrlIsStale() {
        NowPlaying.Art art = new NowPlaying.Art();
        assertTrue(art.fresh("url-a", A));
        // Next song: title and URL change, the picture is still A's (Spotify), then none, then B's own.
        assertFalse(art.fresh("url-b", A));
        assertTrue(art.fresh("url-b", 0));
        assertFalse(art.fresh("url-b", A));   // read again before B's picture came: still A's
        assertTrue(art.fresh("url-b", B));
        assertTrue(art.fresh("url-b", B));
    }

    @Test
    public void backToTheFirstSongTheSecondSongsPictureIsStale() {
        NowPlaying.Art art = new NowPlaying.Art();
        art.fresh("url-a", A);
        art.fresh("url-b", A);
        art.fresh("url-b", B);
        assertFalse(art.fresh("url-a", B));
        assertTrue(art.fresh("url-a", A));
    }

    @Test
    public void songsOfOneAlbumKeepTheirSharedCover() {
        NowPlaying.Art art = new NowPlaying.Art();
        assertTrue(art.fresh("album", A));
        assertTrue(art.fresh("album", A));   // the next track of the album: same URL, same picture
    }

    @Test
    public void theFirstPictureSeenIsTrusted() {
        assertTrue(new NowPlaying.Art().fresh("url-a", A));
    }

    @Test
    public void anUnreadablePictureIsNeverCalledStale() {
        NowPlaying.Art art = new NowPlaying.Art();
        art.fresh("url-a", 0);
        assertTrue(art.fresh("url-b", 0));
    }
}

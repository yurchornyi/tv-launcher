package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * ChannelFeed.pictureKey: two YouTube videos are never the same picture because their thumbnails share a file name
 * (SmartTube's Continue took another video's length, «ще 3 год» for 15 minutes); one TMDB picture at two sizes is.
 */
public class PictureKeyTest {

    @Test
    public void youTubeThumbnailsGoByTheVideo() {
        assertNotEquals(ChannelFeed.pictureKey("https://i.ytimg.com/vi/AAAAAAAAAAA/sddefault.jpg"),
                ChannelFeed.pictureKey("https://i.ytimg.com/vi/BBBBBBBBBBB/sddefault.jpg"));
        assertEquals(ChannelFeed.pictureKey("https://i.ytimg.com/vi/AAAAAAAAAAA/sddefault.jpg"),
                ChannelFeed.pictureKey("https://i.ytimg.com/vi_webp/AAAAAAAAAAA/hqdefault.webp?v=1"));
    }

    @Test
    public void tmdbPicturesGoByTheFileAtAnySize() {
        assertEquals(ChannelFeed.pictureKey("https://image.tmdb.org/t/p/w780/kq1tbm6CXqVyQ6hShbHZQ1Xh0tl.jpg"),
                ChannelFeed.pictureKey("https://image.tmdb.org/t/p/original/kq1tbm6CXqVyQ6hShbHZQ1Xh0tl.jpg"));
    }

    @Test
    public void nothingToTellApartIsNoKey() {
        assertNull(ChannelFeed.pictureKey(null));
        assertNull(ChannelFeed.pictureKey("https://example.com/a.jpg"));
    }
}

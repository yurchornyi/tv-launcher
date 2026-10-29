package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Poster.clean: the show's own name out of what a player reports, never a crash on Home's main thread. */
public class PosterTest {

    @Test
    public void seasonEpisodeAndSecondNameGo() {
        assertEquals("Сімпсони", Poster.clean("Сімпсони. Сезон 3, серія 5"));
        assertEquals("Шлях безсмертного", Poster.clean("Шлях безсмертного / Подорож до безсмертя"));
        assertEquals("Friends", Poster.clean("Friends S01E02"));
    }

    @Test
    public void aTitleThatIsOnlyTheSeasonOrEpisodeIsEmptyNotACrash() {
        // split() gives no parts at all for these, and [0] threw ArrayIndexOutOfBoundsException.
        assertEquals("", Poster.clean("Episode"));
        assertEquals("", Poster.clean("S1E1"));
        assertEquals("", Poster.clean("Сезон"));
        assertEquals("", Poster.clean(" / "));
    }
}

package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Season: the weather outside wins when it rains or snows; otherwise the month's scene. */
public class SeasonTest {

    @Test
    public void eachSeasonHasItsScene() {
        assertEquals(Season.Scene.SNOW, Season.pick(1, -1));
        assertEquals(Season.Scene.SNOW, Season.pick(12, 0));
        assertEquals(Season.Scene.BLOSSOM, Season.pick(4, 2));
        assertEquals(Season.Scene.FIREFLIES, Season.pick(7, 0));
        assertEquals(Season.Scene.LEAVES, Season.pick(11, 3));   // a cloudy November
    }

    @Test
    public void rainAndSnowOutsideWin() {
        assertEquals(Season.Scene.RAIN, Season.pick(7, 61));
        assertEquals(Season.Scene.RAIN, Season.pick(11, 81));
        assertEquals(Season.Scene.SNOW, Season.pick(11, 73));
        assertEquals(Season.Scene.STORM, Season.pick(6, 95));
        assertTrue(Season.heavy(63));
        assertFalse(Season.heavy(2));
        assertFalse(Season.heavy(45));   // fog: the season's own scene, gently
    }

    @Test
    public void snowFallingIsThickerThanWintersOwn() {
        assertTrue(Season.count(Season.Scene.SNOW, true) > Season.count(Season.Scene.SNOW, false));
    }

    @Test
    public void theTopBarsReadingGivesTheCode() {
        assertEquals(61, Season.code("61|7"));
        assertEquals(-1, Season.code(""));
        assertEquals(-1, Season.code(null));
        assertEquals(-1, Season.code("x|7"));
    }
}

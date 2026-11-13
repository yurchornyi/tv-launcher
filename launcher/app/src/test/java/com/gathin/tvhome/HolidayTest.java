package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

/** Holiday: Ukraine's dates since 2023, and Easter on both calendars. */
public class HolidayTest {

    @Test
    public void easterByBothCalendars() {
        assertEquals(LocalDate.of(2025, 4, 20), Holiday.orthodoxEaster(2025));
        assertEquals(LocalDate.of(2026, 4, 12), Holiday.orthodoxEaster(2026));
        assertEquals(LocalDate.of(2027, 5, 2), Holiday.orthodoxEaster(2027));
        assertEquals(LocalDate.of(2025, 4, 20), Holiday.westernEaster(2025));
        assertEquals(LocalDate.of(2026, 4, 5), Holiday.westernEaster(2026));
        assertEquals(LocalDate.of(2027, 3, 28), Holiday.westernEaster(2027));
    }

    @Test
    public void easterSundayAndMondayOnEach() {
        assertEquals(Holiday.Day.EASTER, Holiday.on(LocalDate.of(2027, 3, 28)));
        assertEquals(Holiday.Day.EASTER, Holiday.on(LocalDate.of(2027, 3, 29)));
        assertEquals(Holiday.Day.EASTER, Holiday.on(LocalDate.of(2027, 5, 2)));
        assertEquals(Holiday.Day.EASTER, Holiday.on(LocalDate.of(2027, 5, 3)));
        assertNull(Holiday.on(LocalDate.of(2027, 4, 4)));
    }

    @Test
    public void fixedDays() {
        assertEquals(Holiday.Day.NICHOLAS, Holiday.on(LocalDate.of(2026, 12, 6)));
        assertNull(Holiday.on(LocalDate.of(2026, 12, 19)));   // the old date, before 2023
        assertEquals(Holiday.Day.CHRISTMAS, Holiday.on(LocalDate.of(2026, 12, 24)));
        assertEquals(Holiday.Day.CHRISTMAS, Holiday.on(LocalDate.of(2026, 12, 26)));
        assertNull(Holiday.on(LocalDate.of(2027, 1, 7)));
        assertEquals(Holiday.Day.NEW_YEAR, Holiday.on(LocalDate.of(2026, 12, 31)));
        assertEquals(Holiday.Day.NEW_YEAR, Holiday.on(LocalDate.of(2027, 1, 1)));
        assertEquals(Holiday.Day.VALENTINE, Holiday.on(LocalDate.of(2027, 2, 14)));
        assertEquals(Holiday.Day.INDEPENDENCE, Holiday.on(LocalDate.of(2027, 8, 24)));
        assertEquals(Holiday.Day.HALLOWEEN, Holiday.on(LocalDate.of(2027, 10, 31)));
        assertNull(Holiday.on(LocalDate.of(2026, 11, 13)));
    }

    @Test
    public void eachHasItsSky() {
        assertEquals(Season.Scene.SNOW, Holiday.scene(Holiday.Day.CHRISTMAS, -1));
        assertEquals(Season.Scene.BLOSSOM, Holiday.scene(Holiday.Day.EASTER, 61));   // rain outside: still blossom
        assertEquals(Season.Scene.LEAVES, Holiday.scene(Holiday.Day.HALLOWEEN, 0));
        assertEquals(Season.Scene.NONE, Holiday.scene(Holiday.Day.NEW_YEAR, 3));   // a clear sky for the salute
        assertEquals(Season.Scene.SNOW, Holiday.scene(Holiday.Day.NEW_YEAR, 73));
        assertEquals(0, Season.count(Season.Scene.NONE, false));
    }

    @Test
    public void aCharacterWalksAtMostEveryHalfHour() {
        assertTrue(Holiday.momentDue(Holiday.MOMENT_EVERY_MS, 0));
        assertFalse(Holiday.momentDue(Holiday.MOMENT_EVERY_MS - 1, 0));
        assertEquals(7, Holiday.DEMO.length);
    }
}

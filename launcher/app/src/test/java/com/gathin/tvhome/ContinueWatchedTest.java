package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** ShelfFeed.watched: «Продовжити» only for what was watched, by the app's own word where it has one. */
public class ContinueWatchedTest {

    @Test
    public void aTitleOnlyOpenedInAnAppThatKnowsPlacesIsNoContinue() {
        assertFalse(ShelfFeed.watched(true, false, 0));   // KIK, «Тримай Чарлі» opened from the shelf, never played
        assertTrue(ShelfFeed.watched(true, false, 564_395));   // KIK knows where «Один вдома» was left
    }

    @Test
    public void anAppThatTellsNothingKeepsWhatWasOpened() {
        assertTrue(ShelfFeed.watched(false, false, 0));   // Netflix: Home's record is all there is
    }

    @Test
    public void aRecordedVideoCountsFromHalfAMinute() {
        assertFalse(ShelfFeed.watched(false, true, 0));
        assertFalse(ShelfFeed.watched(false, true, 12_000));
        assertTrue(ShelfFeed.watched(false, true, 904_000));
    }
}

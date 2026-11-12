package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Out of a button: a menu grows from its edge nearest the button (FromButton.pivot), and the headphones capsule
 * unfolds from exactly the button's square (AudioBanner.span).
 */
public class UnfoldTest {

    private static final float EPS = 0.001f;

    @Test
    public void aMenuUnderItsButtonGrowsFromItsTopEdgeBelowTheButton() {
        // Settings button centred at x 1800, y 57; the menu hangs under it from x 1560 to 1860, y 90 down.
        assertEquals(240f, FromButton.pivot(1800f, 1560f, 300), EPS);
        assertEquals(0f, FromButton.pivot(57f, 90f, 420), EPS);
    }

    @Test
    public void aButtonBeyondTheMenuPutsThePivotAtItsNearerEnd() {
        assertEquals(300f, FromButton.pivot(1900f, 1560f, 300), EPS);
        assertEquals(420f, FromButton.pivot(900f, 90f, 420), EPS);
    }

    @Test
    public void theCapsuleStartsAsTheButtonAtItsEnd() {
        assertArrayEquals(new float[]{354f, 400f}, AudioBanner.span(400, 46, 0f, true), EPS);
        assertArrayEquals(new float[]{0f, 46f}, AudioBanner.span(400, 46, 0f, false), EPS);
    }

    @Test
    public void theCapsuleOpensToItsFullWidthAndASpringRunsPastIt() {
        assertArrayEquals(new float[]{0f, 400f}, AudioBanner.span(400, 46, 1f, true), EPS);
        assertArrayEquals(new float[]{0f, 400f}, AudioBanner.span(400, 46, 1f, false), EPS);
        float[] past = AudioBanner.span(400, 46, 1.02f, true);
        assertEquals(400f, past[1], EPS);   // the button's end never moves
        assertEquals(-7.08f, past[0], EPS);
    }
}

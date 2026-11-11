package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** FromButton.Spring: Apple's way of giving a spring (a swing time and a damping), as menus open and close by it. */
public class SpringTest {

    @Test
    public void openingRunsAHairPastAndSettles() {
        FromButton.Spring open = new FromButton.Spring(0.40f, 0.80f);
        assertEquals(0f, open.at(0f), 1e-6);
        float peak = 0f;
        for (float t = 0f; t < 1f; t += 0.002f) peak = Math.max(peak, open.at(t));
        assertTrue("overshoot " + peak, peak > 1.01f && peak < 1.025f);
        assertTrue("settles in " + open.settle, open.settle > 0.4f && open.settle < 0.7f);
        assertEquals(1f, open.at(open.settle), 0.003f);
    }

    @Test
    public void closingNeverOvershoots() {
        FromButton.Spring close = new FromButton.Spring(0.28f, 1f);
        float last = 0f;
        for (float t = 0f; t < 1f; t += 0.002f) {
            float x = close.at(t);
            assertTrue(x <= 1f && x >= last - 1e-6f);
            last = x;
        }
        // Gone (faded) at 0.24 s, by then nearly all the way into the button.
        assertTrue(close.at(0.24f) > 0.95f);
    }
}

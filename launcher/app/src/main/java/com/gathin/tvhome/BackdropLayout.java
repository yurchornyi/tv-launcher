package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The shelf's full-screen pictures (the glow, the shelf art and its fade), drawn as one texture while they hold still.
 *
 * <p>These TVs redraw the whole screen on every frame (their EGL has no buffer age, so no partial updates), and each
 * full-screen picture costs the XGIMI's Mali about 5 ms of that; on the projector SurfaceFlinger also takes the GPU
 * every frame for the keystone. Moving along the top bar or the Now Playing card repainted the same two or three
 * pictures 60 times a second and held Home near 20-30 fps. In a hardware layer they cost one full-screen pass.
 *
 * <p>While any of them changes (a crossfade, a fade in or out) the layer would be repainted on every frame, one pass
 * more than drawing them directly, so it is dropped then and comes back once they have held still for a moment. It
 * stays off with fewer than two pictures shown: one picture costs one pass either way, and an empty layer (the grid,
 * where the shelf is hidden) would still cost a whole one. It is also let go while Home is not on the screen.
 */
public class BackdropLayout extends FrameLayout {

    /** How long the pictures must hold still before they go into the layer. */
    private static final long SETTLE_MS = 250;

    private final Runnable settle = () -> setLayered(getWindowVisibility() == VISIBLE && shownPictures() >= 2);
    private final Runnable drop = () -> setLayered(false);
    /**
     * True from the window's draw until the main thread is free again: a child can ask for its next frame from inside
     * draw() (a TransitionDrawable does), and the layer must not change in the middle of recording.
     */
    private boolean drawing;
    private final Runnable drawn = () -> drawing = false;
    private final android.view.ViewTreeObserver.OnDrawListener onDraw = () -> {
        if (drawing) return;
        drawing = true;
        post(drawn);
    };

    public BackdropLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onDescendantInvalidated(View child, View target) {
        // A picture that is not drawn (MainActivity makes the glow INVISIBLE under opaque art, while its fades and
        // colour changes carry on) changes nothing on the screen. Passed on, it would re-record this view, and a
        // re-recorded view repaints its whole layer.
        if (child.getVisibility() != VISIBLE) return;
        super.onDescendantInvalidated(child, target);
        if (getLayerType() != LAYER_TYPE_NONE) {
            // At once from an animation step, so this very frame skips repainting the layer; posted while drawing.
            if (drawing) {
                removeCallbacks(drop);
                post(drop);
            } else {
                setLayered(false);
            }
        }
        removeCallbacks(settle);
        postDelayed(settle, SETTLE_MS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnDrawListener(onDraw);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        removeCallbacks(settle);
        removeCallbacks(drop);
        // An app in front: no full-screen texture kept for a Home nobody sees. Back on the screen, it comes back once
        // the pictures are still.
        if (visibility == VISIBLE) postDelayed(settle, SETTLE_MS);
        else setLayered(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnDrawListener(onDraw);
        removeCallbacks(settle);
        removeCallbacks(drop);
        removeCallbacks(drawn);
        drawing = false;
        super.onDetachedFromWindow();
    }

    private void setLayered(boolean on) {
        int want = on ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE;
        if (getLayerType() != want) setLayerType(want, null);
    }

    /** Children that draw something now: visible and not fully transparent. */
    private int shownPictures() {
        int n = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == VISIBLE && c.getAlpha() > 0f) n++;
        }
        return n;
    }
}

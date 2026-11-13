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
 *
 * <p>Focus going down into the grid and back fades the pictures as one (fadeOut, fadeIn): they hold still in the
 * layer and only the layer's alpha changes, one full-screen texture a frame. Faded one by one, each over the others,
 * the projector drew about eight frames a second there, and the dark fade under the art went before the art did:
 * the art lit up behind the second row for a moment, then was cut to black (owner, 13.11).
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

    /** True while the pictures fade as one (fadeOut, fadeIn). */
    private boolean fading;
    private boolean fadingIn;
    /** What a fadeOut does once the pictures are out of sight (their own alphas go to 0); null otherwise. */
    private Runnable cleared;

    public BackdropLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Fades the pictures out as they are, then runs clear (which takes each picture's own alpha to 0) and is opaque
     * again. The caller leaves the pictures alone meanwhile.
     */
    void fadeOut(long ms, Runnable clear) {
        finishFade();
        cleared = clear;
        startFade(false);
        animate().alpha(0f).setDuration(ms).withEndAction(this::endFade).start();
    }

    /**
     * Fades the pictures in as one: call it first, then set them straight to how they are to look (fadingIn() says
     * so to whoever would fade them one by one). A fadeOut still going ends here, from where it had got to.
     */
    void fadeIn(long ms) {
        float from = fading ? getAlpha() : 0f;
        animate().cancel();
        runCleared();
        setAlpha(from);
        startFade(true);
        animate().alpha(1f).setDuration(Math.max(1, (long) (ms * (1f - from)))).withEndAction(this::endFade).start();
    }

    boolean fadingIn() {
        return fading && fadingIn;
    }

    /** A fade jumps to its end: the pictures shown in full, or cleared after a fadeOut. */
    void finishFade() {
        if (!fading) return;
        animate().cancel();
        endFade();
    }

    private void startFade(boolean in) {
        fading = true;
        fadingIn = in;
        removeCallbacks(settle);
        removeCallbacks(drop);
        setLayered(getWindowVisibility() == VISIBLE);
    }

    private void endFade() {
        fading = false;
        fadingIn = false;
        runCleared();
        setAlpha(1f);
        removeCallbacks(settle);
        postDelayed(settle, SETTLE_MS);
    }

    private void runCleared() {
        Runnable r = cleared;
        cleared = null;
        if (r != null) r.run();
    }

    @Override
    public void onDescendantInvalidated(View child, View target) {
        // A picture that is not drawn (MainActivity makes the glow INVISIBLE under opaque art, while its fades and
        // colour changes carry on) changes nothing on the screen. Passed on, it would re-record this view, and a
        // re-recorded view repaints its whole layer.
        if (child.getVisibility() != VISIBLE) return;
        super.onDescendantInvalidated(child, target);
        // A fade keeps the layer: a picture changing meanwhile (a backdrop arriving) only repaints it once.
        if (fading) return;
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
        if (visibility != VISIBLE) finishFade();
        removeCallbacks(settle);
        removeCallbacks(drop);
        // An app in front: no full-screen texture kept for a Home nobody sees. Back on the screen, it comes back once
        // the pictures are still.
        if (visibility == VISIBLE) postDelayed(settle, SETTLE_MS);
        else setLayered(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        finishFade();
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

package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;

/**
 * A menu that belongs to a button comes out of it, as tvOS and iOS open theirs: in its own place and shape, it springs
 * up from a smaller size, growing from the point nearest the button, while it fades in. Closing shrinks it a little
 * towards the button as it fades. The window that shows it takes in the button too (MainActivity.growFrom). Only scale
 * and alpha move: cheap even on the XGIMI's GPU. A top bar button's menu spreads out from under its button instead
 * (MorphMenu), with the same springs.
 */
final class FromButton {

    /** No motion: the panel just appears (animations off). */
    static final int NONE = 0;
    /** A short fade in place, nothing moves (reduced motion). */
    static final int FADE = 1;
    /** Out of the button and back into it. */
    static final int FULL = 2;

    /**
     * Apple's springs, as UIKit and SwiftUI name them: opening swings out in 0.4 s and is damped to 0.8, so it runs a
     * hair past its size and place (about 1.5%) and settles; closing is critically damped and quicker, no bounce.
     */
    static final Spring OPEN = new Spring(0.40f, 0.80f);
    static final Spring CLOSE = new Spring(0.28f, 1f);
    /** Closing is over once it has faded: the last few percent of its way into the button go unseen. */
    private static final float CLOSE_S = 0.24f, CLOSE_FADE_S = 0.20f;
    /** The picture is all there this soon after it starts out of the button: its smallest first frames go unseen. */
    private static final float OPEN_FADE_S = 0.12f;
    private static final long FADE_MS = 160;
    /** How large it starts (and how small it closes to), the same in width and height: never squeezed. */
    private static final float OPEN_SCALE = 0.6f, CLOSE_SCALE = 0.85f;

    private FromButton() { }

    /**
     * Along one axis, where in a panel `size` long starting at `start` (screen) it grows from: across from the button's
     * centre, or the panel's nearer end when the button lies beyond it (a menu hanging under its button).
     */
    static float pivot(float buttonCentre, float start, int size) {
        return Math.max(0f, Math.min(size, buttonCentre - start));
    }

    static int motion(boolean animations, boolean reducedMotion) {
        return !animations ? NONE : reducedMotion ? FADE : FULL;
    }

    /** Where a view is on the screen, as drawn (a focused button is scaled up a little). */
    static Rect bounds(View v) {
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        return new Rect(at[0], at[1], at[0] + Math.round(v.getWidth() * v.getScaleX()),
                at[1] + Math.round(v.getHeight() * v.getScaleY()));
    }

    /** Grows `panel` out of `button` (screen bounds) as soon as it is laid out; it is invisible until then. */
    static void grow(View panel, Rect button, int motion) {
        if (motion == NONE) return;
        panel.setAlpha(0f);
        panel.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                panel.getViewTreeObserver().removeOnPreDrawListener(this);
                run(panel, button, motion, true, null);
                return true;
            }
        });
    }

    /** Shrinks `panel` back into `button`, then runs `end` (which removes it). */
    static void shrink(View panel, Rect button, int motion, Runnable end) {
        if (motion == NONE || !panel.isAttachedToWindow() || panel.getWidth() == 0) {
            end.run();
            return;
        }
        run(panel, button, motion, false, end);
    }

    private static void run(View panel, Rect button, int motion, boolean open, Runnable end) {
        Object running = panel.getTag(R.id.from_button_anim);
        if (running instanceof Animator) ((Animator) running).cancel();
        // It keeps its own shape and place and grows from the point of its edge nearest the button: squeezed to the
        // button's square and flown in from the button's centre, a tall menu (Settings) came in stretched and askew.
        float dx = 0, dy = 0, s = 1;
        int w = panel.getWidth(), h = panel.getHeight();
        float px = w / 2f, py = h / 2f;
        if (motion == FULL && button != null && w > 0 && h > 0) {
            int[] at = placeOnScreen(panel);
            px = pivot(button.exactCenterX(), at[0], w);
            py = pivot(button.exactCenterY(), at[1], h);
            s = open ? OPEN_SCALE : CLOSE_SCALE;
        }
        panel.setPivotX(px);
        panel.setPivotY(py);
        // Closing starts from wherever the panel is now: a menu shut while still opening turns back from there.
        final float[] from = open ? new float[]{dx, dy, s, s, 0f}
                : new float[]{panel.getTranslationX(), panel.getTranslationY(), panel.getScaleX(), panel.getScaleY(),
                        panel.getAlpha()};
        final float[] to = open ? new float[]{0f, 0f, 1f, 1f, 1f} : new float[]{dx, dy, s, s, 0f};
        float seconds = motion == FADE ? FADE_MS / 1000f : open ? OPEN.settle : CLOSE_S;
        ValueAnimator a = ValueAnimator.ofFloat(0f, seconds);
        a.setDuration(Math.round(seconds * 1000));
        a.setInterpolator(null);   // the spring's own time: every property moves by it below
        a.addUpdateListener(v -> {
            float t = (float) v.getAnimatedValue();
            float p, alpha;
            if (motion == FADE) {
                p = 1f;
                alpha = open ? t / seconds : from[4] * (1f - t / seconds);
            } else if (open) {
                p = OPEN.at(t);
                alpha = Math.min(1f, t / OPEN_FADE_S);
            } else {
                p = CLOSE.at(t);
                alpha = from[4] * Math.max(0f, 1f - t / CLOSE_FADE_S);
            }
            panel.setAlpha(Math.max(0f, Math.min(1f, alpha)));
            if (motion == FULL) {
                panel.setTranslationX(from[0] + (to[0] - from[0]) * p);
                panel.setTranslationY(from[1] + (to[1] - from[1]) * p);
                panel.setScaleX(from[2] + (to[2] - from[2]) * p);
                panel.setScaleY(from[3] + (to[3] - from[3]) * p);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                panel.setLayerType(View.LAYER_TYPE_NONE, null);
                if (panel.getTag(R.id.from_button_anim) == animation) panel.setTag(R.id.from_button_anim, null);
                if (open && !cancelled) {
                    panel.setAlpha(1f);
                    panel.setTranslationX(0f);
                    panel.setTranslationY(0f);
                    panel.setScaleX(1f);
                    panel.setScaleY(1f);
                }
                if (end != null) end.run();
            }
        });
        panel.setTag(R.id.from_button_anim, a);
        // One texture moved, scaled and faded by the GPU, not the rows redrawn every frame.
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        a.start();
    }

    /**
     * A damped spring pulled from 0 to 1, given as Apple gives its springs: `response`, the time of one swing in
     * seconds, and `damping`, 1 for none past the target, less for a little overshoot. at(t) is where it is t seconds
     * in; `settle` is when it is within 0.2% of the target for good.
     */
    static final class Spring {
        private final float omega, damping, omegaD;
        final float settle;

        Spring(float response, float damping) {
            this.omega = (float) (2 * Math.PI / response);
            this.damping = damping;
            this.omegaD = damping < 1f ? omega * (float) Math.sqrt(1 - damping * damping) : 0f;
            float t = 0f;
            while (t < 3f && Math.abs(1f - at(t)) + envelope(t) > 0.002f) t += 0.005f;
            this.settle = t;
        }

        float at(float t) {
            double decay = Math.exp(-damping * omega * t);
            if (damping >= 1f) return (float) (1 - decay * (1 + omega * t));
            return (float) (1 - decay * (Math.cos(omegaD * t) + damping * omega / omegaD * Math.sin(omegaD * t)));
        }

        /** How far it can still swing from t on. */
        private float envelope(float t) {
            float amp = damping < 1f ? (float) Math.sqrt(1 + Math.pow(damping * omega / omegaD, 2)) : 1 + omega * t;
            return (float) (amp * Math.exp(-damping * omega * t));
        }
    }

    /** The panel's top-left on the screen where it is laid out, whatever it is scaled or moved to now. */
    private static int[] placeOnScreen(View panel) {
        int[] at = new int[2];
        if (panel.getParent() instanceof View) {
            ((View) panel.getParent()).getLocationOnScreen(at);
            at[0] += panel.getLeft();
            at[1] += panel.getTop();
        } else {
            panel.getLocationOnScreen(at);
            at[0] -= Math.round(panel.getTranslationX());
            at[1] -= Math.round(panel.getTranslationY());
        }
        return at;
    }

    /**
     * A popup that came out of a button (MainActivity.growFrom) goes back into it when it closes, Back and a chosen
     * row alike. While it closes it takes no keys: the remote is already back on Home, and OK cannot choose twice.
     */
    static final class Popup extends PopupWindow {
        private View panel;
        private Rect button;
        private int motion = NONE;
        private boolean closing;
        /** A top bar button's menu spreads out from under the button and draws back to it (MorphMenu), else null. */
        private MorphMenu morph;

        Popup(View content, int width, int height, boolean focusable) {
            super(content, width, height, focusable);
        }

        void from(View panel, Rect button, int motion) {
            this.panel = panel;
            this.button = button;
            this.motion = motion;
        }

        void morph(View panel, MorphMenu morph, int motion) {
            this.panel = panel;
            this.morph = morph;
            this.motion = motion;
        }

        /** Closes at once: what it came from is going away too. */
        void dismissNow() {
            closing = true;
            if (morph != null) morph.restore();   // no animation left running on a window that goes at once
            super.dismiss();
        }

        @Override public void dismiss() {
            if (closing) return;
            if (panel == null || motion == NONE || !isShowing() || gone(panel.getContext())) {
                if (morph != null) morph.restore();
                super.dismiss();
                return;
            }
            if (morph != null) {
                closing = true;
                try {
                    setFocusable(false);
                    setTouchable(false);
                    update();
                } catch (RuntimeException ignored) {   // a window already on its way out
                }
                morph.close(() -> {
                    try {
                        super.dismiss();
                    } catch (RuntimeException ignored) {   // Home went away meanwhile and took the window with it
                    }
                });
                return;
            }
            closing = true;
            try {
                setFocusable(false);
                setTouchable(false);
                update();
            } catch (RuntimeException ignored) {   // a window already on its way out
            }
            shrink(panel, button, motion, () -> {
                try {
                    super.dismiss();
                } catch (RuntimeException ignored) {   // Home went away meanwhile and took the window with it
                }
            });
        }

        /** Home is closing or being rebuilt: no time for an animation, the window has to go now. */
        private static boolean gone(Context c) {
            while (c instanceof ContextWrapper && !(c instanceof Activity)) c = ((ContextWrapper) c).getBaseContext();
            if (!(c instanceof Activity)) return false;
            Activity a = (Activity) c;
            return a.isFinishing() || a.isDestroyed() || a.isChangingConfigurations();
        }
    }
}

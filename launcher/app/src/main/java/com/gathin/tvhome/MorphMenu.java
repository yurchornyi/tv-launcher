package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * A top bar button's menu that the button itself unfolds into, as the headphones capsule does (AudioBanner), instead
 * of a separate card hanging under it: in its first frame it is the button, its own picture in its own place; then a
 * card of the buttons' glass grows out of it, down and towards the middle of the screen, the button's glyph kept in
 * its corner and the button's name across the top, the rows coming in once there is room. Closing folds it back into
 * the button. The window starts at the button's top and takes in its place; the button itself is hidden while its menu
 * is out, the card's corner standing where it was.
 */
final class MorphMenu {

    private final View anchor;
    private final View panel;
    private final TextView title;
    private final Shape shape;
    private final FrameLayout room;
    private final int motion;
    private final Rect clip = new Rect();
    private ValueAnimator anim;
    private float progress;
    private float anchorAlpha = 1f;
    private boolean hidden;
    /** The menu has gone (or is going away at once): the button must not be hidden after it. */
    private boolean over;
    /** The top bar's other buttons the open card lies over, and their alpha before they stepped aside. */
    private final java.util.List<View> covered = new java.util.ArrayList<>();
    private final java.util.List<Float> coveredAlpha = new java.util.ArrayList<>();

    /**
     * `panel` (popup_inputs: the rows) goes below a header band as tall as the button, `title` across it (null: the
     * button's own name); `atEnd`: the button stands at the menu's right end (it opens towards the left), else at
     * its left end.
     */
    MorphMenu(Context ctx, View anchor, View panel, CharSequence heading, boolean atEnd, int motion) {
        this.anchor = anchor;
        this.panel = panel;
        this.motion = motion;
        float d = ctx.getResources().getDisplayMetrics().density;
        int bw = Math.max(1, anchor.getWidth()), bh = Math.max(1, anchor.getHeight());
        room = new FrameLayout(ctx);
        room.setClipChildren(false);
        shape = new Shape(ctx, anchor, atEnd, d);
        room.addView(shape, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // The button's name across the top of the card, the glyph's corner left free.
        title = new TextView(ctx);
        title.setText(heading != null ? heading : name(anchor));
        title.setTextColor(0xF2FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(ctx.getResources().getFont(R.font.outfit_medium));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        // An icon-only button's glyph stays in its corner: the name keeps clear of it. A labelled one's picture goes.
        boolean iconOnly = shape.glyph != null;
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bh);
        tlp.leftMargin = iconOnly && !atEnd ? bw + Math.round(10 * d) : Math.round(22 * d);
        tlp.rightMargin = iconOnly && atEnd ? bw + Math.round(10 * d) : Math.round(22 * d);
        room.addView(title, tlp);
        // The rows below it, on the card's ground (the card draws it: the panel's own goes).
        panel.setBackground(null);
        panel.setElevation(0f);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = bh;
        room.addView(panel, plp);
        set(motion == FromButton.FULL ? 0f : 1f);
        if (motion == FromButton.FADE) room.setAlpha(0f);
    }

    View room() {
        return room;
    }

    /** The menu's own height above the panel: the header band. */
    int header() {
        return Math.max(1, anchor.getHeight());
    }

    /** Unfolds out of the button as soon as the window is laid out; the button hides as the card takes its place. */
    void open() {
        room.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                room.getViewTreeObserver().removeOnPreDrawListener(this);
                // After this first frame, which shows the button's own picture over it: no frame without either.
                room.post(MorphMenu.this::hideAnchor);
                run(true, null);
                return true;
            }
        });
    }

    /** Folds back into the button, which comes back, then runs `done` (which removes the window). */
    void close(Runnable done) {
        if (!room.isAttachedToWindow() || room.getWidth() == 0) {
            restore();
            done.run();
            return;
        }
        uncover(true);   // the buttons it lay over come back as it folds away
        run(false, () -> {
            restore();
            done.run();
        });
    }

    /** The button back as it was: the window is going, whichever way. */
    void restore() {
        over = true;
        if (anim != null) anim.cancel();
        uncover(false);
        if (!hidden) return;
        hidden = false;
        anchor.setAlpha(anchorAlpha);
    }

    private void hideAnchor() {
        if (hidden || over) return;
        hidden = true;
        anchorAlpha = anchor.getAlpha();
        anchor.setAlpha(0f);
        cover();
    }

    /**
     * The top bar's other buttons under the open card step aside while it is out: through its glass, the Search pill
     * showed half covered beside the Settings menu.
     */
    private void cover() {
        if (!(anchor.getParent() instanceof ViewGroup) || room.getWidth() == 0) return;
        int[] at = new int[2];
        room.getLocationOnScreen(at);
        Rect card = new Rect(at[0], at[1], at[0] + room.getWidth(), at[1] + room.getHeight());
        ViewGroup bar = (ViewGroup) anchor.getParent();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View b = bar.getChildAt(i);
            if (b == anchor || b.getVisibility() != View.VISIBLE || b.getAlpha() == 0f) continue;
            if (!Rect.intersects(card, FromButton.bounds(b))) continue;
            covered.add(b);
            coveredAlpha.add(b.getAlpha());
            b.animate().cancel();
            if (motion == FromButton.NONE) b.setAlpha(0f);
            else b.animate().alpha(0f).setDuration(140).start();
        }
    }

    /** The covered buttons back: with a short fade as the card folds away from them, or at once. */
    private void uncover(boolean fade) {
        for (int i = 0; i < covered.size(); i++) {
            View b = covered.get(i);
            b.animate().cancel();
            if (fade && motion != FromButton.NONE) b.animate().alpha(coveredAlpha.get(i)).setDuration(220).start();
            else b.setAlpha(coveredAlpha.get(i));
        }
        covered.clear();
        coveredAlpha.clear();
    }

    private void run(boolean opening, Runnable done) {
        if (anim != null) anim.cancel();
        if (motion == FromButton.NONE) {
            set(opening ? 1f : 0f);
            if (done != null) done.run();
            return;
        }
        final float from = progress, startAlpha = room.getAlpha();
        float seconds = motion == FromButton.FADE ? 0.16f
                : opening ? FromButton.OPEN.settle : FromButton.CLOSE.settle;
        ValueAnimator a = ValueAnimator.ofFloat(0f, seconds);
        a.setDuration(Math.round(seconds * 1000));
        a.setInterpolator(null);   // the spring's own time
        a.addUpdateListener(v -> {
            float t = (float) v.getAnimatedValue();
            if (motion == FromButton.FADE) {
                room.setAlpha(opening ? t / seconds : startAlpha * (1f - t / seconds));
                return;
            }
            set(opening ? from + (1f - from) * FromButton.OPEN.at(t) : from * (1f - FromButton.CLOSE.at(t)));
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                panel.setLayerType(View.LAYER_TYPE_NONE, null);
                if (anim == animation) anim = null;
                if (cancelled) return;
                if (motion == FromButton.FADE) room.setAlpha(opening ? 1f : 0f);
                else set(opening ? 1f : 0f);
                if (done != null) done.run();
            }
        });
        anim = a;
        // The rows fade as one texture, not redrawn each frame.
        panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        a.start();
    }

    /** Where it is between the button (0) and the card (1, a spring runs a hair past it). */
    private void set(float p) {
        progress = p;
        float q = clamp(p);
        shape.p = q;
        shape.invalidate();
        title.setAlpha(clamp((q - 0.4f) / 0.4f));
        panel.setAlpha(clamp((q - 0.45f) / 0.4f));
        // The rows only within the card as it grows: none over the glass still on its way.
        if (q >= 1f) {
            panel.setClipBounds(null);
        } else {
            RectF r = shape.card(room.getWidth(), room.getHeight(), q);
            clip.set(Math.round(r.left) - panel.getLeft(), Math.round(r.top) - panel.getTop(),
                    Math.round(r.right) - panel.getLeft(), Math.round(r.bottom) - panel.getTop());
            panel.setClipBounds(clip);
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** What the button is called: what it says, else what it tells accessibility (an icon-only button). */
    private static CharSequence name(View anchor) {
        CharSequence text = anchor instanceof TextView ? ((TextView) anchor).getText() : null;
        CharSequence said = anchor.getContentDescription();
        if (said != null && said.length() > 0) return said;
        return text != null ? text : "";
    }

    /**
     * The card itself: the buttons' glass (pill_bg's gradient, a brighter hairline) over a see-through dark tint, between the
     * button's box (0) and the whole window (1); at first the button's own picture, fading as the card grows, and on
     * an icon-only button its glyph in white, which stays in the card's corner.
     */
    private static final class Shape extends View {
        float p;
        private final boolean atEnd;
        private final float d, bw, bh, sx, sy;
        private final Bitmap face;
        final Drawable glyph;
        private final GradientDrawable ground = new GradientDrawable();
        private final GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2EFFFFFF, 0x14FFFFFF});
        private final Paint facePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint line = new Paint();
        private final RectF r = new RectF();
        private final Rect ir = new Rect();

        Shape(Context ctx, View anchor, boolean atEnd, float d) {
            super(ctx);
            this.atEnd = atEnd;
            this.d = d;
            bw = Math.max(1, anchor.getWidth());
            bh = Math.max(1, anchor.getHeight());
            sx = anchor.getScaleX();
            sy = anchor.getScaleY();
            setWillNotDraw(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            // See-through glass, as the buttons are (owner: a glass frame with everything visible behind it): the
            // picture behind shows through a dark tint that keeps the words readable. Android's blur behind a window
            // is off on these TVs, so it is a tint, not frosting.
            ground.setColor(0xB30A0B0D);
            glass.setStroke(Math.max(1, Math.round(d)), 0x59FFFFFF);
            line.setColor(0x1FFFFFFF);
            // The button as it is drawn now (focused: white, its glyph dark), for the first frames.
            Bitmap b = null;
            try {
                b = Bitmap.createBitmap((int) bw, (int) bh, Bitmap.Config.ARGB_8888);
                anchor.draw(new Canvas(b));
            } catch (RuntimeException | OutOfMemoryError e) {
                b = null;
            }
            face = b;
            // An icon-only button keeps its glyph in the card's corner, white on the dark glass.
            Drawable g = null;
            if (anchor instanceof TextView && ((TextView) anchor).getText().length() == 0) {
                Drawable start = ((TextView) anchor).getCompoundDrawablesRelative()[0];
                if (start != null && start.getConstantState() != null) {
                    g = start.getConstantState().newDrawable().mutate();
                    g.setTint(0xFFFFFFFF);
                }
            }
            glyph = g;
        }

        /** The card's box in a window w by h at q (0..1): from the button's, scaled as it is drawn, to the window. */
        RectF card(int w, int h, float q) {
            float cx = atEnd ? w - bw / 2f : bw / 2f, cy = bh / 2f;
            float l = cx - bw * sx / 2f, t = cy - bh * sy / 2f, rr = cx + bw * sx / 2f, b = cy + bh * sy / 2f;
            r.set(l + (0f - l) * q, t + (0f - t) * q, rr + (w - rr) * q, b + (h - b) * q);
            return r;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            card(w, h, p);
            float radius = (bh * sy) / 2f + (20f * d - (bh * sy) / 2f) * p;
            ir.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
            // The picture of the button gives way to the card in the first third of the way.
            float faceAlpha = 1f - Math.min(1f, p / 0.35f);
            ground.setCornerRadius(radius);
            ground.setBounds(ir);
            ground.setAlpha((int) (255 * Math.min(1f, p * 1.6f)));
            ground.draw(c);
            glass.setCornerRadius(radius);
            glass.setBounds(ir);
            glass.setAlpha((int) (255 * (1f - faceAlpha)));
            glass.draw(c);
            float cx = atEnd ? w - bw / 2f : bw / 2f, cy = bh / 2f;
            if (face != null && faceAlpha > 0f) {
                facePaint.setAlpha((int) (255 * faceAlpha));
                c.save();
                c.scale(sx, sy, cx, cy);
                c.drawBitmap(face, cx - bw / 2f, cy - bh / 2f, facePaint);
                c.restore();
            }
            if (glyph != null) {
                int gw = glyph.getIntrinsicWidth() > 0 ? glyph.getIntrinsicWidth() : Math.round(22 * d);
                int gh = glyph.getIntrinsicHeight() > 0 ? glyph.getIntrinsicHeight() : Math.round(22 * d);
                glyph.setBounds(Math.round(cx - gw / 2f), Math.round(cy - gh / 2f),
                        Math.round(cx + gw / 2f), Math.round(cy + gh / 2f));
                glyph.setAlpha((int) (255 * (1f - faceAlpha)));
                glyph.draw(c);
            }
            // A hairline under the header, once the card is there.
            if (p > 0.6f) {
                line.setAlpha((int) (0x1F * Math.min(1f, (p - 0.6f) / 0.3f)));
                c.drawRect(r.left + 16f * d, bh, r.right - 16f * d, bh + Math.max(1f, d), line);
            }
        }
    }
}

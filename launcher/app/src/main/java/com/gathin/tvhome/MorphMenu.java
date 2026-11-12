package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
 * A top bar button's menu: a card of see-through glass that grows out from under the button, below the row of
 * buttons, its edge in line with the button's (owner, 12.11, from a preview: every button stays where it is and in
 * view, the pressed one white, the card hanging under it, nothing covered). It starts as a drop of glass the button's
 * size right under it and spreads down and towards the middle of the screen, the menu's name across its top, the rows
 * coming in once there is room; closing draws it back up under the button.
 */
final class MorphMenu {

    private final View panel;
    private final TextView title;
    private final Shape shape;
    private final FrameLayout room;
    private final int motion;
    private final int header;
    private final Rect clip = new Rect();
    private ValueAnimator anim;
    private float progress;

    /**
     * `panel` (popup_inputs: the rows) goes below a header band with `heading` (null: the button's own name);
     * `atEnd`: the button stands over the card's right end (it spreads towards the left), else over its left end.
     */
    MorphMenu(Context ctx, View anchor, View panel, CharSequence heading, boolean atEnd, int motion) {
        this.panel = panel;
        this.motion = motion;
        float d = ctx.getResources().getDisplayMetrics().density;
        header = Math.round(46 * d);
        room = new FrameLayout(ctx);
        shape = new Shape(ctx, Math.max(1, anchor.getWidth()), atEnd, d);
        room.addView(shape, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        title = new TextView(ctx);
        title.setText(heading != null ? heading : name(anchor));
        title.setTextColor(0xF2FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(ctx.getResources().getFont(R.font.outfit_medium));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, header);
        tlp.leftMargin = Math.round(22 * d);
        tlp.rightMargin = Math.round(22 * d);
        room.addView(title, tlp);
        // The rows below the name, on the card's glass (the card draws it: the panel's own goes).
        panel.setBackground(null);
        panel.setElevation(0f);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = header;
        room.addView(panel, plp);
        set(motion == FromButton.FULL ? 0f : 1f);
        if (motion == FromButton.FADE) room.setAlpha(0f);
    }

    View room() {
        return room;
    }

    /** The band with the menu's name above the rows. */
    int header() {
        return header;
    }

    /** Spreads out from under the button as soon as the window is laid out. */
    void open() {
        room.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                room.getViewTreeObserver().removeOnPreDrawListener(this);
                run(true, null);
                return true;
            }
        });
    }

    /** Draws back up under the button, then runs `done` (which removes the window). */
    void close(Runnable done) {
        if (!room.isAttachedToWindow() || room.getWidth() == 0) {
            done.run();
            return;
        }
        run(false, done);
    }

    /** The window is going at once: no animation left running on it. */
    void restore() {
        if (anim != null) anim.cancel();
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

    /** Where it is between the drop under the button (0) and the whole card (1; a spring runs a hair past it). */
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

    /** What the button is called: what it tells accessibility, else what it says. */
    private static CharSequence name(View anchor) {
        CharSequence said = anchor.getContentDescription();
        if (said != null && said.length() > 0) return said;
        CharSequence text = anchor instanceof TextView ? ((TextView) anchor).getText() : null;
        return text != null ? text : "";
    }

    /**
     * The card: the buttons' glass (pill_bg's gradient, a brighter hairline) over a see-through dark tint, between a
     * button-wide drop at the top of the window under the button (0) and the whole window (1).
     */
    private static final class Shape extends View {
        float p;
        private final boolean atEnd;
        private final float d, bw;
        private final GradientDrawable ground = new GradientDrawable();
        private final GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2EFFFFFF, 0x14FFFFFF});
        private final Paint line = new Paint();
        private final RectF r = new RectF();
        private final Rect ir = new Rect();

        Shape(Context ctx, float buttonWidth, boolean atEnd, float d) {
            super(ctx);
            this.atEnd = atEnd;
            this.d = d;
            bw = buttonWidth;
            setWillNotDraw(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            // See-through glass, as the buttons are: the picture behind shows through a dark tint that keeps the
            // words readable. Android's blur behind a window is off on these TVs, so it is a tint, not frosting.
            ground.setColor(0xB30A0B0D);
            glass.setStroke(Math.max(1, Math.round(d)), 0x59FFFFFF);
            line.setColor(0x1FFFFFFF);
        }

        /**
         * No size of its own: the name and the rows give the window its height, then this fills it. Measured as a plain
         * View it took all the height the window could have, and the card ran down to the bottom of the screen.
         */
        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(
                    MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : 0,
                    MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : 0);
        }

        /** The card's box in a window w by h at q (0..1): from a drop as wide as the button, under it, to the window. */
        RectF card(int w, int h, float q) {
            float drop = Math.min(bw, w);
            float l = atEnd ? w - drop : 0f, rr = atEnd ? w : drop, b = Math.min(h, drop * 0.6f);
            r.set(l + (0f - l) * q, 0f, rr + (w - rr) * q, b + (h - b) * q);
            return r;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            card(w, h, p);
            float radius = Math.min(r.height(), r.width()) / 2f;
            radius = radius + (20f * d - radius) * p;
            ir.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
            // The drop comes in as it starts to spread: nothing there before the first frames.
            float in = Math.min(1f, p / 0.2f);
            ground.setCornerRadius(radius);
            ground.setBounds(ir);
            ground.setAlpha((int) (255 * in));
            ground.draw(c);
            glass.setCornerRadius(radius);
            glass.setBounds(ir);
            glass.setAlpha((int) (255 * in));
            glass.draw(c);
            // A hairline under the menu's name, once the card is there.
            if (p > 0.6f) {
                float y = 46f * d;
                line.setAlpha((int) (0x1F * Math.min(1f, (p - 0.6f) / 0.3f)));
                c.drawRect(r.left + 16f * d, y, r.right - 16f * d, y + Math.max(1f, d), line);
            }
        }
    }
}

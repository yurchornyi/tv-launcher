package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Headphones or a speaker connecting, over any app: Home's round headphones button unfolds where it stands into a
 * capsule of its own glass, the device's name and "Connected" with the battery beside its glyph, and folds back into
 * the button four seconds later. The glyph never moves; the capsule grows from the button towards the middle of the
 * screen, over the menus' dark scrim so its words read on a bright poster, and the top bar's buttons it covers step
 * aside meanwhile. Over another app it unfolds where that button sits on Home and fades as it folds away. Needs the
 * overlay permission (build-and-install grants it with appops); without it nothing is shown. Never focusable.
 */
final class AudioBanner {

    private static View shown;
    private static Pill shownPill;
    /** Gives Home's button back its look once the capsule over it is gone; null when it was not hidden. */
    private static Runnable giveBack;
    /** Brings back the top bar's buttons the open capsule covered (Search, Wi-Fi); null when none were. */
    private static Runnable uncover;
    /** Home's headphones button while Home is in front (MainActivity), else null. */
    private static java.lang.ref.WeakReference<View> button;
    /** Where that button was last seen on the screen: the capsule unfolds there over another app. */
    private static Rect lastButton;

    private static final long HOLD_MS = 4000;

    private AudioBanner() { }

    /** Home is in front with its headphones button (null: Home went to the background). */
    static void from(View homeButton) {
        button = homeButton == null ? null : new java.lang.ref.WeakReference<>(homeButton);
    }

    /** Home's button when it is on the screen now, else null. */
    private static View homeButton() {
        View b = button != null ? button.get() : null;
        return b != null && b.isAttachedToWindow() && b.isShown() && b.getWidth() > 0 ? b : null;
    }

    /** The button's place now, or where it was last; failing both, where it would be at the top right. */
    private static Rect buttonPlace(int screenWidth, float d, View home) {
        if (home != null) lastButton = FromButton.bounds(home);
        if (lastButton != null) return new Rect(lastButton);
        int size = (int) (46 * d), right = screenWidth - (int) (48 * d), top = (int) (34 * d);
        return new Rect(right - size, top, right, top + size);
    }

    static void show(Context context, String name, int battery, boolean speaker) {
        Context ctx = context.getApplicationContext();
        if (!Settings.canDrawOverlays(ctx)) return;
        new Handler(Looper.getMainLooper()).post(() -> showNow(ctx, name, battery, speaker));
    }

    private static void showNow(Context ctx, String name, int battery, boolean speaker) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        float d = ctx.getResources().getDisplayMetrics().density;
        removeShown(wm);

        android.util.DisplayMetrics screen = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(screen);
        View home = homeButton();
        Rect from = buttonPlace(screen.widthPixels, d, home);
        // Towards the middle of the screen: from the top bar's right end it grows to the left.
        boolean towardsLeft = from.centerX() > screen.widthPixels / 2;
        boolean focused = home != null && home.isFocused();
        int ink = focused ? 0xFF111111 : 0xFFFFFFFF, dim = focused ? 0xCC111111 : 0xE6FFFFFF;
        int bw = from.width(), bh = from.height();

        Pill pill = new Pill(ctx, bw, towardsLeft, home == null, glass(ctx, focused, bh), focused ? null : base(bh));

        // The glyph exactly where the button has it: centred in the button's square at the capsule's end. A focused
        // button is drawn a little larger, and so is everything here.
        float k = bh / (46 * d);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(AudioDevices.icon(speaker));
        icon.setImageTintList(ColorStateList.valueOf(ink));
        int iconSize = Math.round(22 * d * k), side = (bw - iconSize) / 2, gap = Math.round(10 * d * k);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ilp.leftMargin = towardsLeft ? gap : side;
        ilp.rightMargin = towardsLeft ? side : gap;

        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int outer = Math.round(22 * d * k);
        if (towardsLeft) wlp.leftMargin = outer;
        else wlp.rightMargin = outer;
        TextView title = text(ctx, name, ink, 15 * k, R.font.outfit_medium);
        title.setMaxWidth((int) (320 * d));
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(title);

        LinearLayout status = new LinearLayout(ctx);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.addView(text(ctx, ctx.getString(R.string.connected), dim, 12 * k, R.font.outfit_regular));
        if (battery >= 0) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Math.round(20 * d * k), Math.round(10 * d * k));
            blp.setMarginStart(Math.round(10 * d));
            status.addView(new BatteryView(ctx, battery, d, dim, ink), blp);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            plp.setMarginStart(Math.round(5 * d));
            status.addView(text(ctx, battery + "%", dim, 12 * k, R.font.outfit_regular), plp);
        }
        words.addView(status);

        if (towardsLeft) {
            pill.addView(words, wlp);
            pill.addView(icon, ilp);
        } else {
            pill.addView(icon, ilp);
            pill.addView(words, wlp);
        }
        pill.words = words;

        // The window starts at the button and takes in the capsule's full width, with a little room on the far side
        // for the spring to run a hair past it.
        FrameLayout room = new FrameLayout(ctx);
        room.setClipChildren(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, bh,
                Gravity.TOP | (towardsLeft ? Gravity.RIGHT : Gravity.LEFT));
        int spare = Math.round(16 * d);
        if (towardsLeft) clp.leftMargin = spare;
        else clp.rightMargin = spare;
        room.addView(pill, clp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not FLAG_NOT_TOUCHABLE: Android caps a pass-through overlay at 80% opacity, which let the top bar
                // show through. A TV has no touch screen, and outside touches still pass (NOT_TOUCH_MODAL).
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | (towardsLeft ? Gravity.RIGHT : Gravity.LEFT);
        lp.x = towardsLeft ? screen.widthPixels - from.right : from.left;
        lp.y = from.top;

        int motion = motion(ctx);
        pill.ready(motion);
        pill.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                pill.getViewTreeObserver().removeOnPreDrawListener(this);
                // From its first frame the capsule is the button: Home's own would show twice through the glass.
                if (home != null && home.isAttachedToWindow()) {
                    float was = home.getAlpha();
                    home.setAlpha(0f);
                    giveBack = () -> home.setAlpha(was);
                    cover(home, towardsLeft ? from.right - pill.getWidth() : from.left,
                            towardsLeft ? from.right : from.left + pill.getWidth(), motion);
                }
                pill.run(motion, true, null);
                return true;
            }
        });
        try {
            wm.addView(room, lp);
        } catch (RuntimeException e) {
            return;
        }
        shown = room;
        shownPill = pill;
        room.postDelayed(() -> {
            uncover(motion);   // the buttons come back as it folds away from them
            pill.run(motion, false, () -> {
                if (shown == room) removeShown(wm);
            });
        }, HOLD_MS);
    }

    /** Takes the capsule off the screen now and gives Home's button back. */
    private static void removeShown(WindowManager wm) {
        if (shownPill != null) shownPill.stop();
        if (shown != null) {
            try { wm.removeView(shown); } catch (RuntimeException ignored) { }
        }
        shown = null;
        shownPill = null;
        if (giveBack != null) {
            giveBack.run();
            giveBack = null;
        }
        uncover(FromButton.NONE);
    }

    /**
     * Hides the top bar's other buttons the open capsule will lie over (screen x from `left` to `right`): through its
     * glass, «Пошук» and the Wi-Fi glyph showed under the device's name and its «Підключено».
     */
    private static void cover(View home, int left, int right, int motion) {
        if (!(home.getParent() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup bar = (android.view.ViewGroup) home.getParent();
        java.util.List<View> hidden = new java.util.ArrayList<>();
        java.util.List<Float> was = new java.util.ArrayList<>();
        for (int i = 0; i < bar.getChildCount(); i++) {
            View b = bar.getChildAt(i);
            if (b == home || b.getVisibility() != View.VISIBLE || b.getAlpha() == 0f) continue;
            Rect r = FromButton.bounds(b);
            if (r.right <= left || r.left >= right) continue;
            hidden.add(b);
            was.add(b.getAlpha());
            b.animate().cancel();
            if (motion == FromButton.NONE) b.setAlpha(0f);
            else b.animate().alpha(0f).setDuration(140).start();
        }
        if (hidden.isEmpty()) return;
        uncover = () -> {
            for (int i = 0; i < hidden.size(); i++) {
                View b = hidden.get(i);
                b.animate().cancel();
                b.setAlpha(was.get(i));
            }
        };
        uncoverFade = () -> {
            for (int i = 0; i < hidden.size(); i++) hidden.get(i).animate().alpha(was.get(i)).setDuration(220).start();
        };
    }

    /** The same back, at once or (motion) with a short fade. */
    private static Runnable uncoverFade;

    private static void uncover(int motion) {
        Runnable now = uncover, fade = uncoverFade;
        uncover = null;
        uncoverFade = null;
        if (motion != FromButton.NONE && fade != null) fade.run();
        else if (now != null) now.run();
    }

    /** The button's own glass (icon_button_bg), focused or not, rounded to a capsule of this height. */
    private static Drawable glass(Context ctx, boolean focused, int height) {
        Drawable all = ctx.getDrawable(R.drawable.icon_button_bg).mutate();
        all.setState(focused ? new int[]{android.R.attr.state_focused} : new int[0]);
        Drawable g = all.getCurrent();
        if (g instanceof GradientDrawable) ((GradientDrawable) g).setCornerRadius(height / 2f);
        return g;
    }

    /**
     * A dark capsule under the glass, the menus' own scrim (glass_panel): the words read over a bright poster or
     * another app's picture. Over Home's button it comes in as the capsule opens, so its first frame is the button.
     */
    private static Drawable base(int height) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF20A0B0D);
        g.setCornerRadius(height / 2f);
        return g;
    }

    private static TextView text(Context ctx, String s, int color, float sp, int font) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(ctx.getResources().getFont(font));
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** The launcher's motion setting (Config): unfolding, a plain fade, or none. */
    private static int motion(Context ctx) {
        try {
            Config c = Config.load(ctx);
            return FromButton.motion(c.animations, c.reducedMotion);
        } catch (RuntimeException e) {
            return FromButton.FULL;
        }
    }

    /**
     * The capsule's left and right edge in a view `width` wide, `open` of the way from the button's square (0) to
     * its full width (1; a spring runs a little past it), the button at the right end when it grows to the left.
     */
    static float[] span(int width, int button, float open, boolean towardsLeft) {
        float shown = button + (width - button) * open;
        return towardsLeft ? new float[]{width - shown, width} : new float[]{0f, shown};
    }

    /** The capsule: its glass from the button's square out to its full width as it opens, its words revealed. */
    private static final class Pill extends LinearLayout {
        private final int button;
        private final boolean towardsLeft, alone;
        private final Drawable glass, base;
        private final Rect edges = new Rect();
        View words;
        private float open = 1f;
        private ValueAnimator anim;

        /** `alone`: no button of Home's under it (another app is in front), so it comes and goes with a fade. */
        Pill(Context ctx, int button, boolean towardsLeft, boolean alone, Drawable glass, Drawable base) {
            super(ctx);
            this.button = button;
            this.towardsLeft = towardsLeft;
            this.alone = alone;
            this.glass = glass;
            this.base = base;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setLayoutDirection(LAYOUT_DIRECTION_LTR);   // the glyph stays at the button's end in any language
        }

        private void setOpen(float o) {
            open = o;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            float[] s = span(getWidth(), button, open, towardsLeft);
            edges.set(Math.round(s[0]), 0, Math.round(s[1]), getHeight());
            if (base != null) {
                base.setBounds(edges);
                base.setAlpha(alone ? 255 : (int) (255 * Math.max(0f, Math.min(1f, open))));
                base.draw(c);
            }
            glass.setBounds(edges);
            glass.draw(c);
            c.save();
            c.clipRect(edges);
            super.dispatchDraw(c);
            c.restore();
        }

        /** How it looks before its first frame: the button alone, or, with a fade, whole and transparent. */
        void ready(int motion) {
            if (motion == FromButton.FULL) {
                open = 0f;
                words.setAlpha(0f);
                if (alone) setAlpha(0f);
            } else if (motion == FromButton.FADE) {
                setAlpha(0f);
            }
        }

        void stop() {
            if (anim != null) anim.cancel();
        }

        /** Unfolds out of the button, or folds back into it and then runs `end`. Only the drawing changes. */
        void run(int motion, boolean opening, Runnable end) {
            stop();
            if (motion == FromButton.NONE || !isAttachedToWindow()) {
                setOpen(opening ? 1f : 0f);
                if (end != null) end.run();
                return;
            }
            float startOpen = open, startWords = words.getAlpha(), startAlpha = getAlpha();
            float seconds = motion == FromButton.FADE ? 0.16f
                    : opening ? FromButton.OPEN.settle : FromButton.CLOSE.settle;
            ValueAnimator a = ValueAnimator.ofFloat(0f, seconds);
            a.setDuration(Math.round(seconds * 1000));
            a.setInterpolator(null);   // the spring's own time
            a.addUpdateListener(v -> {
                float t = (float) v.getAnimatedValue();
                if (motion == FromButton.FADE) {
                    setAlpha(opening ? t / seconds : startAlpha * (1f - t / seconds));
                    return;
                }
                if (opening) {
                    float p = FromButton.OPEN.at(t);
                    setOpen(p);
                    words.setAlpha(clamp((p - 0.35f) / 0.5f));   // the words come once there is room for them
                    if (alone) setAlpha(clamp(t / 0.12f));
                } else {
                    float p = FromButton.CLOSE.at(t);
                    setOpen(startOpen * (1f - p));
                    words.setAlpha(startWords * clamp(1f - t / 0.12f));   // gone before the glass covers them
                    if (alone) setAlpha(startAlpha * (1f - p));
                }
            });
            a.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override public void onAnimationEnd(Animator animation) {
                    if (anim == animation) anim = null;
                    if (cancelled) return;
                    if (opening) {
                        setOpen(1f);
                        words.setAlpha(1f);
                        setAlpha(1f);
                    }
                    if (end != null) end.run();
                }
            });
            anim = a;
            a.start();
        }

        private static float clamp(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }

    /** A small horizontal battery: outline, cap and a fill for the level, red at 20% and below. */
    private static final class BatteryView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int level, outline, fill;
        private final float d;

        BatteryView(Context c, int level, float d, int outline, int fill) {
            super(c);
            this.level = Math.max(0, Math.min(100, level));
            this.d = d;
            this.outline = outline;
            this.fill = fill;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() - 3 * d, h = getHeight(), r = 3 * d;
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * d);
            paint.setColor(outline);
            c.drawRoundRect(0.6f * d, 0.6f * d, w, h - 0.6f * d, r, r, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            c.drawRoundRect(w + 0.8f * d, h * 0.32f, w + 2.6f * d, h * 0.68f, d, d, paint);
            paint.setColor(level <= 20 ? 0xFFFF453A : fill);
            float inner = (w - 3.2f * d) * level / 100f;
            c.drawRoundRect(2 * d, 2 * d, 2 * d + inner, h - 2 * d, 1.5f * d, 1.5f * d, paint);
        }
    }
}

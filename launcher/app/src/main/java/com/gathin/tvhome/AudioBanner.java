package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The banner at the top right when headphones or a speaker connect, over any app, like Apple TV's: the device glyph,
 * its name and "Connected" with the battery level. It comes out of the headphones button in Home's top bar and goes
 * back into it a few seconds later (FromButton); over another app, from where that button sits on Home. Needs the
 * overlay permission (build-and-install grants it with appops); without it nothing is shown. Never focusable.
 */
final class AudioBanner {

    private static View shown;
    /** Home's headphones button while Home is in front (MainActivity), else null. */
    private static java.lang.ref.WeakReference<View> button;
    /** Where that button was last seen on the screen: the banner comes from there over another app. */
    private static Rect lastButton;

    private AudioBanner() { }

    /** Home is in front with its headphones button (null: Home went to the background). */
    static void from(View homeButton) {
        button = homeButton == null ? null : new java.lang.ref.WeakReference<>(homeButton);
    }

    /** The button's place now, or where it was last; failing both, where it would be at the top right. */
    private static Rect buttonPlace(Context ctx, int screenWidth, float d) {
        View b = button != null ? button.get() : null;
        if (b != null && b.isAttachedToWindow() && b.isShown() && b.getWidth() > 0) {
            lastButton = FromButton.bounds(b);
        }
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
        if (shown != null) {
            try { wm.removeView(shown); } catch (RuntimeException ignored) { }
            shown = null;
        }

        // Apple TV's look: a dark rounded card, the device glyph large and white on the left, name over status.
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumWidth((int) (340 * d));
        card.setPadding((int) (20 * d), (int) (16 * d), (int) (28 * d), (int) (16 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF51C1C1E);
        bg.setCornerRadius(24 * d);
        bg.setStroke(Math.max(1, (int) d), 0x26FFFFFF);
        card.setBackground(bg);
        card.setElevation(16 * d);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(AudioDevices.icon(speaker));
        card.addView(icon, new LinearLayout.LayoutParams((int) (40 * d), (int) (40 * d)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart((int) (18 * d));
        TextView title = new TextView(ctx);
        title.setText(name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(ctx.getResources().getFont(R.font.outfit_medium));
        title.setSingleLine(true);
        title.setMaxWidth((int) (380 * d));
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);

        LinearLayout status = new LinearLayout(ctx);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView sub = new TextView(ctx);
        sub.setText(ctx.getString(R.string.connected));
        sub.setTextColor(0x99FFFFFF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setTypeface(ctx.getResources().getFont(R.font.outfit_regular));
        status.addView(sub);
        if (battery >= 0) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int) (24 * d), (int) (12 * d));
            blp.setMarginStart((int) (14 * d));
            status.addView(new BatteryView(ctx, battery, d), blp);
            TextView pct = new TextView(ctx);
            pct.setText(battery + "%");
            pct.setTextColor(0x99FFFFFF);
            pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            pct.setTypeface(ctx.getResources().getFont(R.font.outfit_regular));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            plp.setMarginStart((int) (6 * d));
            status.addView(pct, plp);
        }
        texts.addView(status);
        card.addView(texts, tlp);

        // Hanging under the button, its outer edge in line with the button's. The window takes in the button too
        // (transparent there), so the card can start over it without being cut off.
        android.util.DisplayMetrics screen = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(screen);
        Rect from = buttonPlace(ctx, screen.widthPixels, d);
        boolean right = from.centerX() > screen.widthPixels / 2;
        android.widget.FrameLayout room = new android.widget.FrameLayout(ctx);
        android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (right ? Gravity.RIGHT : Gravity.LEFT));
        clp.topMargin = from.height() + (int) (12 * d);
        room.addView(card, clp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not FLAG_NOT_TOUCHABLE: Android caps a pass-through overlay at 80% opacity, which let the top bar
                // show through. A TV has no touch screen, and outside touches still pass (NOT_TOUCH_MODAL).
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | (right ? Gravity.RIGHT : Gravity.LEFT);
        lp.x = right ? screen.widthPixels - from.right : from.left;
        lp.y = from.top;
        int motion = motion(ctx);
        FromButton.grow(card, from, motion);
        try {
            wm.addView(room, lp);
        } catch (RuntimeException e) {
            return;
        }
        shown = room;
        room.postDelayed(() -> FromButton.shrink(card, from, motion, () -> {
            try { wm.removeView(room); } catch (RuntimeException ignored) { }
            if (shown == room) shown = null;
        }), 4000);
    }

    /** The launcher's motion setting (Config): out of the button, a plain fade, or none. */
    private static int motion(Context ctx) {
        try {
            Config c = Config.load(ctx);
            return FromButton.motion(c.animations, c.reducedMotion);
        } catch (RuntimeException e) {
            return FromButton.FULL;
        }
    }

    /** A small horizontal battery: outline, cap and a fill for the level, red at 20% and below. */
    private static final class BatteryView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int level;
        private final float d;

        BatteryView(Context c, int level, float d) {
            super(c);
            this.level = Math.max(0, Math.min(100, level));
            this.d = d;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float w = getWidth() - 3 * d, h = getHeight(), r = 3 * d;
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f * d);
            paint.setColor(0x99FFFFFF);
            c.drawRoundRect(0.6f * d, 0.6f * d, w, h - 0.6f * d, r, r, paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            c.drawRoundRect(w + 0.8f * d, h * 0.32f, w + 2.6f * d, h * 0.68f, d, d, paint);
            paint.setColor(level <= 20 ? 0xFFFF453A : 0xFFFFFFFF);
            float inner = (w - 3.2f * d) * level / 100f;
            c.drawRoundRect(2 * d, 2 * d, 2 * d + inner, h - 2 * d, 1.5f * d, 1.5f * d, paint);
        }
    }
}

package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Behind a film that has a poster but no wide frame (an Asian series TMDB lacks): the poster's soft colours across
 * the screen and the poster itself, whole and sharp, on the right, between the top bar and the dock, while the
 * Now Playing card's words stay on the left. One drawable in the hero's own view, so no extra layer for the GPU.
 */
final class PosterBackdrop extends Drawable {

    private final Drawable soft;
    private final Bitmap poster;
    private final RectF place;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /** `place`: the poster's box in the hero's coordinates; the poster fits its height, right-aligned in it. */
    PosterBackdrop(Drawable soft, Bitmap poster, RectF place, float radius) {
        this.soft = soft;
        this.poster = poster;
        this.radius = radius;
        float h = place.height(), w = h * poster.getWidth() / poster.getHeight();
        this.place = new RectF(place.right - w, place.top, place.right, place.bottom);
        BitmapShader shader = new BitmapShader(poster, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(w / poster.getWidth(), h / poster.getHeight());
        m.postTranslate(this.place.left, this.place.top);
        shader.setLocalMatrix(m);
        paint.setShader(shader);
    }

    @Override
    protected void onBoundsChange(android.graphics.Rect bounds) {
        soft.setBounds(bounds);
    }

    @Override
    public void draw(Canvas canvas) {
        soft.draw(canvas);
        canvas.drawRoundRect(place, radius, radius, paint);
    }

    @Override public void setAlpha(int alpha) { soft.setAlpha(alpha); paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { soft.setColorFilter(cf); paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

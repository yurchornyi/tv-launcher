package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Arrays;
import java.util.Random;

/**
 * Snow, rain, leaves, blossom or fireflies across Home (Season picks which). It knows where the app tiles, the shelf's
 * pill, the Now Playing card and a channel row's logos are (Tiles): rain bursts on their top edges, beads run down
 * their faces and hang from their lower edges until they drip; snow settles on them in a cap; a leaf or a petal rests
 * on an edge for a moment. Drawn over Home about 30 times a second while Home is on the screen (MainActivity), keys
 * and all, allocating nothing per frame. Takes no focus, keys or accessibility.
 */
public final class SeasonView extends View {

    interface Tiles {
        /**
         * Writes the boxes things land on as (id, left, top, right, bottom) in this view's coordinates, 5 floats each,
         * at most out.length / 5, and returns how many. An id stays with its box while it moves (adapter position).
         */
        int fill(float[] out);
    }

    /**
     * Box ids: tiles go by adapter position, below PILL; then the shelf's pill, the Now Playing card, and a channel
     * row's logos (CHANNELS + their place in the row), all below IDS.
     */
    static final int PILL = 256, CARD = 257, CHANNELS = 258, IDS = 320;

    private static final float TAU = (float) (2 * Math.PI);
    private static final int MAX_BOXES = 48, SPLASHES = 64, RUNNERS = 24, DRIPS = 24;
    /** The next frame is asked this long after one is drawn: about 30 a second at 60 Hz, half the GPU work. */
    private static final long FRAME_MS = 20;
    /** How often the boxes are read again (a scroll, a focused tile growing). */
    private static final long BOXES_MS = 250;
    private static final int[] LEAVES = {0xFFE07A1F, 0xFFC4471B, 0xFFD9A53A, 0xFF9C4A1A, 0xFFB8862B, 0xFFE3B04B};
    private static final int[] PETALS = {0xFFFFD1DC, 0xFFFFB7C9, 0xFFFDE2EA, 0xFFF8C8D8, 0xFFFFF0F4};
    private static final int[] GLOWS = {0xFFFFE7A3, 0xFFFFF4C2, 0xFFFFD36E};
    private static final int WATER = 0xFFDCE8FF;

    private final float d;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path(), vein = new Path(), petal = new Path();
    private final RectF oval = new RectF();
    private final Runnable next = this::invalidate;

    private Season.Scene scene = Season.Scene.LEAVES;
    private boolean heavy, running, reseed;
    private Tiles source;
    private final float[] boxes = new float[MAX_BOXES * 5];
    private int boxCount;
    private long boxesAt, last;
    private float clock;

    // the falling pieces
    private int n;
    private float[] px = new float[0], py = new float[0], vy = new float[0], size = new float[0], depth = new float[0],
            phase = new float[0], freq = new float[0], amp = new float[0], rot = new float[0], spin = new float[0],
            flipF = new float[0], flipP = new float[0], restX = new float[0], restLeft = new float[0];
    private int[] tint = new int[0], restOn = new int[0];

    // rain bursting on an edge
    private final float[] sX = new float[SPLASHES], sY = new float[SPLASHES], sVx = new float[SPLASHES],
            sVy = new float[SPLASHES], sLife = new float[SPLASHES];
    private int splashAt;

    // beads running down a face: box id, across (0..1), down from the top (px), the wet trail's top, speed, wait, size
    private final boolean[] rOn = new boolean[RUNNERS];
    private final int[] rBox = new int[RUNNERS];
    private final float[] rX = new float[RUNNERS], rY = new float[RUNNERS], rTop = new float[RUNNERS],
            rV = new float[RUNNERS], rWait = new float[RUNNERS], rR = new float[RUNNERS];
    private float runnerDue;

    // drops hanging from a lower edge, then falling: box id, across (0..1), size, age; once falling, x, y and speed
    private final boolean[] dOn = new boolean[DRIPS], dFalling = new boolean[DRIPS];
    private final int[] dBox = new int[DRIPS];
    private final float[] dAcross = new float[DRIPS], dR = new float[DRIPS], dAge = new float[DRIPS],
            dX = new float[DRIPS], dY = new float[DRIPS], dVy = new float[DRIPS], dFell = new float[DRIPS];

    /** Snow settled on each box's top edge, by id. */
    private final float[] cap = new float[IDS];

    /**
     * Rain, snow and splashes go out in a few batches (drawLines, drawPoints), one per depth layer, not one call per
     * drop: a hundred and thirty lines each with its own width and alpha made issuing the frame's commands the slow
     * part of Home on the XGIMI. LAYERS layers for rain, FLAKES for snow; the pieces are filled in, then drawn.
     */
    private static final int LAYERS = 3, FLAKES = 4;
    private final float[][] batch = new float[Math.max(LAYERS, FLAKES)][0];
    private final int[] batched = new int[Math.max(LAYERS, FLAKES)];
    private final float[] splashFresh = new float[SPLASHES * 2], splashFading = new float[SPLASHES * 2];
    private float flashT = -1f, flashDue;

    public SeasonView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        setAlpha(0f);
        setVisibility(INVISIBLE);
        // A leaf, a unit tall around its middle; its midrib and veins; a cherry petal.
        leaf.moveTo(0f, -0.5f);
        leaf.cubicTo(0.34f, -0.36f, 0.36f, 0.18f, 0f, 0.42f);
        leaf.cubicTo(-0.36f, 0.18f, -0.34f, -0.36f, 0f, -0.5f);
        leaf.close();
        vein.moveTo(0f, -0.42f);
        vein.lineTo(0f, 0.5f);
        vein.moveTo(0f, -0.1f);
        vein.lineTo(0.16f, -0.24f);
        vein.moveTo(0f, 0.08f);
        vein.lineTo(-0.16f, -0.06f);
        vein.moveTo(0f, 0.2f);
        vein.lineTo(0.14f, 0.08f);
        petal.moveTo(0f, -0.5f);
        petal.cubicTo(0.42f, -0.42f, 0.36f, 0.3f, 0f, 0.5f);
        petal.cubicTo(-0.36f, 0.3f, -0.42f, -0.42f, 0f, -0.5f);
        petal.close();
    }

    void setTiles(Tiles source) {
        this.source = source;
    }

    boolean running() {
        return running;
    }

    /** Starts the scene, or switches to it, fading in. */
    void start(Season.Scene scene, boolean heavy) {
        if (!running || scene != this.scene || heavy != this.heavy) reseed = true;
        this.scene = scene;
        this.heavy = heavy;
        if (!running) {
            running = true;
            Arrays.fill(cap, 0f);   // last time's snow has melted
            Arrays.fill(rOn, false);
            Arrays.fill(dOn, false);
            Arrays.fill(sLife, 0f);
            boxesAt = 0;
            last = SystemClock.uptimeMillis();
            setVisibility(VISIBLE);
        }
        animate().cancel();
        animate().alpha(1f).setDuration(1200).start();
        removeCallbacks(next);
        invalidate();
    }

    /** Stops it: a short fade, or at once. */
    void stop(boolean fade) {
        if (!running) return;
        animate().cancel();
        if (fade && getAlpha() > 0f) animate().alpha(0f).setDuration(300).withEndAction(this::halt).start();
        else halt();
    }

    private void halt() {
        running = false;
        removeCallbacks(next);
        setAlpha(0f);
        setVisibility(INVISIBLE);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;   // its pieces fade one by one: no offscreen layer for the whole screen
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animate().cancel();
        halt();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!running) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        if (reseed) {
            reseed = false;
            seed(w, h);
            last = now;
        }
        float dt = Math.min(0.1f, Math.max(0f, (now - last) / 1000f));
        last = now;
        clock += dt;
        if (now - boxesAt >= BOXES_MS) {
            boxCount = source == null ? 0 : Math.max(0, Math.min(MAX_BOXES, source.fill(boxes)));
            boxesAt = now;
        }
        drawCaps(c);
        if (scene == Season.Scene.RAIN || scene == Season.Scene.STORM) rain(c, dt, w, h);
        else fall(c, dt, w, h);
        removeCallbacks(next);
        postDelayed(next, FRAME_MS);
    }

    // ---- the pieces --------------------------------------------------------------------------------------------

    private void seed(int w, int h) {
        n = Season.count(scene, heavy);
        if (px.length < n) grow(n);
        for (int k = 0; k < batch.length; k++) if (batch[k].length < n * 4) batch[k] = new float[n * 4];
        for (int i = 0; i < n; i++) spawn(i, w, h, true);
        flashT = -1f;
        flashDue = 4f + rnd.nextFloat() * 6f;
        runnerDue = 0.5f;
    }

    /** A piece starting out: anywhere on the screen (the first ones), else just above it (below it, a firefly). */
    private void spawn(int i, int w, int h, boolean anywhere) {
        float z = rnd.nextFloat();   // 0 far .. 1 near: bigger, faster, brighter
        depth[i] = z;
        px[i] = rnd.nextFloat() * w;
        restLeft[i] = 0f;
        phase[i] = rnd.nextFloat() * TAU;
        rot[i] = rnd.nextFloat() * 360f;
        flipP[i] = rnd.nextFloat() * TAU;
        spin[i] = 0f;
        switch (scene) {
            case SNOW:
                size[i] = (1.4f + 3.2f * z) * d;
                vy[i] = (22f + 50f * z) * d * (heavy ? 1.25f : 1f);
                amp[i] = (6f + 16f * rnd.nextFloat()) * d;
                freq[i] = 0.15f + 0.35f * rnd.nextFloat();
                break;
            case RAIN:
            case STORM:
                float slant = slant();
                size[i] = (14f + 16f * z) * d;
                vy[i] = (900f + 500f * z) * d * (scene == Season.Scene.STORM ? 1.15f : 1f);
                px[i] = rnd.nextFloat() * (w + slant * h) - slant * h;
                break;
            case LEAVES:
                size[i] = (14f + 10f * z) * d;
                vy[i] = (34f + 36f * z) * d;
                amp[i] = (22f + 34f * rnd.nextFloat()) * d;
                freq[i] = 0.18f + 0.3f * rnd.nextFloat();
                spin[i] = (25f + 70f * rnd.nextFloat()) * (rnd.nextBoolean() ? 1f : -1f);
                flipF[i] = 0.25f + 0.6f * rnd.nextFloat();
                tint[i] = LEAVES[rnd.nextInt(LEAVES.length)];
                break;
            case BLOSSOM:
                size[i] = (7f + 5f * z) * d;
                vy[i] = (22f + 30f * z) * d;
                amp[i] = (16f + 26f * rnd.nextFloat()) * d;
                freq[i] = 0.2f + 0.4f * rnd.nextFloat();
                spin[i] = (40f + 120f * rnd.nextFloat()) * (rnd.nextBoolean() ? 1f : -1f);
                flipF[i] = 0.4f + 0.9f * rnd.nextFloat();
                tint[i] = PETALS[rnd.nextInt(PETALS.length)];
                break;
            default:   // fireflies
                size[i] = (1.4f + 1.4f * z) * d;
                vy[i] = -(4f + 12f * rnd.nextFloat()) * d;
                amp[i] = (10f + 30f * rnd.nextFloat()) * d;
                freq[i] = 0.05f + 0.15f * rnd.nextFloat();
                flipF[i] = 0.3f + 0.9f * rnd.nextFloat();
                tint[i] = GLOWS[rnd.nextInt(GLOWS.length)];
        }
        float margin = size[i] * 1.5f;
        if (anywhere) py[i] = rnd.nextFloat() * h;
        else if (scene == Season.Scene.FIREFLIES) py[i] = h + margin;
        else py[i] = -margin - rnd.nextFloat() * h * 0.15f;
    }

    /** Snow, leaves, blossom and fireflies. */
    private void fall(Canvas c, float dt, int w, int h) {
        float wind = (scene == Season.Scene.SNOW ? 8f : scene == Season.Scene.LEAVES ? 14f : 6f) * d;
        boolean snow = scene == Season.Scene.SNOW, flies = scene == Season.Scene.FIREFLIES;
        Arrays.fill(batched, 0);
        for (int i = 0; i < n; i++) {
            if (restLeft[i] > 0f) {
                restLeft[i] -= dt;
                int b = box(restOn[i]);
                if (restLeft[i] <= 0f || b < 0) {
                    spawn(i, w, h, false);
                    continue;
                }
                int o = b * 5;
                float x = boxes[o + 1] + restX[i] * (boxes[o + 3] - boxes[o + 1]);
                drawPiece(c, i, x, boxes[o + 2] - size[i] * 0.18f, Math.min(1f, restLeft[i] / 0.6f), true);
                continue;
            }
            float before = py[i];
            py[i] += vy[i] * dt;
            if (!flies) px[i] += wind * (0.4f + 0.6f * depth[i]) * dt;
            rot[i] += spin[i] * dt;
            float x = px[i] + amp[i] * (float) Math.sin(TAU * freq[i] * clock + phase[i]);
            float y = py[i];
            if (flies) {
                if (y < -20f * d) spawn(i, w, h, false);
                else drawFirefly(c, i, x, y);
                continue;
            }
            float bottom = snow ? size[i] : size[i] * 0.45f;
            int b = crossed(x, before + bottom, y + bottom);
            if (b >= 0) {
                int o = b * 5, id = (int) boxes[o];
                if (snow) {
                    if (id >= 0 && id < IDS) cap[id] = Math.min(5f * d, cap[id] + 0.35f * d * (0.6f + 0.8f * depth[i]));
                    spawn(i, w, h, false);
                    continue;
                }
                if (rnd.nextFloat() < 0.55f) {   // a leaf or a petal comes to rest on the edge for a moment
                    restLeft[i] = 1.8f + rnd.nextFloat() * 2.6f;
                    restOn[i] = id;
                    restX[i] = (x - boxes[o + 1]) / Math.max(1f, boxes[o + 3] - boxes[o + 1]);
                    continue;
                }
            }
            if (y - size[i] * 2f > h) {
                spawn(i, w, h, false);
                continue;
            }
            if (px[i] > w + 40f * d) px[i] -= w + 80f * d;
            if (snow) {
                int layer = Math.min(FLAKES - 1, (int) (depth[i] * FLAKES));
                float[] points = batch[layer];
                points[batched[layer]++] = x;
                points[batched[layer]++] = y;
            } else {
                drawPiece(c, i, x, y, 1f, false);
            }
        }
        if (!snow) return;
        // Each layer's flakes as round points of its size and brightness, in one call.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFFFFFFF);
        for (int layer = 0; layer < FLAKES; layer++) {
            if (batched[layer] == 0) continue;
            float z = (layer + 0.5f) / FLAKES;
            paint.setStrokeWidth(2f * (1.4f + 3.2f * z) * d);
            paint.setAlpha((int) (90 + 150 * z));
            c.drawPoints(batch[layer], 0, batched[layer], paint);
        }
    }

    /** A leaf or a petal, turning as it falls and showing its darker back; `flat`: lying on an edge. */
    private void drawPiece(Canvas c, int i, float x, float y, float alpha, boolean flat) {
        float len = size[i];
        float flip = flat ? 0.35f : (float) Math.cos(TAU * flipF[i] * clock + flipP[i]);
        float sx = Math.abs(flip) < 0.1f ? (flip < 0f ? -0.1f : 0.1f) : flip;
        int a = (int) (255 * alpha * (0.75f + 0.25f * depth[i]) * (flip < 0f ? 0.8f : 1f));
        boolean isLeaf = scene == Season.Scene.LEAVES;
        c.save();
        c.translate(x, y);
        c.rotate(flat ? (i % 2 == 0 ? 90f : -90f) : rot[i]);
        c.scale(len * sx, len);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(shade(tint[i], (flip < 0f ? 0.8f : 1f) * (0.8f + 0.2f * Math.abs(flip))));
        paint.setAlpha(a);
        c.drawPath(isLeaf ? leaf : petal, paint);
        if (isLeaf) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.05f);
            paint.setColor(shade(tint[i], 0.5f));
            paint.setAlpha(a * 2 / 3);
            c.drawPath(vein, paint);
        }
        c.restore();
    }

    private void drawFirefly(Canvas c, int i, float x, float y) {
        float twinkle = (float) Math.sin(TAU * flipF[i] * clock + flipP[i]);
        float glow = 0.25f + 0.75f * twinkle * twinkle;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tint[i]);
        paint.setAlpha((int) (40 * glow));
        c.drawCircle(x, y, size[i] * 4.5f, paint);
        paint.setAlpha((int) (220 * glow));
        c.drawCircle(x, y, size[i], paint);
    }

    /** Snow lying on the top edges. */
    private void drawCaps(Canvas c) {
        if (scene != Season.Scene.SNOW) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(238);
        for (int b = 0; b < boxCount; b++) {
            int o = b * 5, id = (int) boxes[o];
            float h = id >= 0 && id < IDS ? cap[id] : 0f;
            if (h < 0.4f * d) continue;
            oval.set(boxes[o + 1] + 5f * d, boxes[o + 2] - h, boxes[o + 3] - 5f * d, boxes[o + 2] + 2.5f * d);
            c.drawRoundRect(oval, h, h, paint);
        }
    }

    // ---- rain --------------------------------------------------------------------------------------------------

    private float slant() {
        return scene == Season.Scene.STORM ? 0.2f : 0.12f;
    }

    private void rain(Canvas c, float dt, int w, int h) {
        boolean storm = scene == Season.Scene.STORM;
        float slant = slant();
        float k = 1f / (float) Math.sqrt(1 + slant * slant);
        Arrays.fill(batched, 0);
        for (int i = 0; i < n; i++) {
            float before = py[i];
            py[i] += vy[i] * dt;
            px[i] += vy[i] * slant * dt;
            int b = crossed(px[i], before, py[i]);
            if (b >= 0) {
                splash(px[i], boxes[b * 5 + 2], depth[i]);
                spawn(i, w, h, false);
                continue;
            }
            if (py[i] - size[i] > h) {
                spawn(i, w, h, false);
                continue;
            }
            float len = size[i] * k;
            int layer = Math.min(LAYERS - 1, (int) (depth[i] * LAYERS));
            float[] lines = batch[layer];
            int o = batched[layer];
            lines[o] = px[i] - slant * len;
            lines[o + 1] = py[i] - len;
            lines[o + 2] = px[i];
            lines[o + 3] = py[i];
            batched[layer] = o + 4;
        }
        // Far drops thin and faint, near ones wider and brighter: one call per layer.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(WATER);
        for (int layer = 0; layer < LAYERS; layer++) {
            if (batched[layer] == 0) continue;
            float z = (layer + 0.5f) / LAYERS;
            paint.setStrokeWidth((0.9f + 0.9f * z) * d);
            paint.setAlpha((int) (50 + 80 * z));
            c.drawLines(batch[layer], 0, batched[layer], paint);
        }
        splashes(c, dt);
        runners(c, dt, storm);
        drips(c, dt, h);
        if (storm) flash(c, dt, w, h);
    }

    /** A drop bursting on an edge: two or three droplets thrown up and out. */
    private void splash(float x, float top, float z) {
        int count = 2 + rnd.nextInt(2);
        for (int k = 0; k < count; k++) {
            int s = splashAt;
            splashAt = (splashAt + 1) % SPLASHES;
            sX[s] = x;
            sY[s] = top - d;
            sVx[s] = (rnd.nextFloat() - 0.5f) * 2f * (60f + 60f * rnd.nextFloat()) * d;
            sVy[s] = -(70f + 110f * rnd.nextFloat()) * d * (0.6f + 0.6f * z);
            sLife[s] = 0.28f + 0.15f * rnd.nextFloat();
        }
    }

    private void splashes(Canvas c, float dt) {
        int fresh = 0, fading = 0;
        for (int s = 0; s < SPLASHES; s++) {
            if (sLife[s] <= 0f) continue;
            sLife[s] -= dt;
            if (sLife[s] <= 0f) continue;
            sVy[s] += 900f * d * dt;
            sX[s] += sVx[s] * dt;
            sY[s] += sVy[s] * dt;
            // Fresh droplets bright, the last moments of each dimmer: two calls in all.
            if (sLife[s] > 0.12f) {
                splashFresh[fresh++] = sX[s];
                splashFresh[fresh++] = sY[s];
            } else {
                splashFading[fading++] = sX[s];
                splashFading[fading++] = sY[s];
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f * d);
        paint.setColor(0xFFE1ECFF);
        if (fresh > 0) {
            paint.setAlpha(150);
            c.drawPoints(splashFresh, 0, fresh, paint);
        }
        if (fading > 0) {
            paint.setAlpha(70);
            c.drawPoints(splashFading, 0, fading, paint);
        }
    }

    /** Beads on the faces: each waits, then runs down with a wet trail and hangs from the lower edge (drips). */
    private void runners(Canvas c, float dt, boolean storm) {
        runnerDue -= dt;
        if (runnerDue <= 0f && boxCount > 0) {
            addRunner();
            runnerDue = (storm ? 0.25f : heavy ? 0.4f : 0.8f) * (0.5f + rnd.nextFloat());
        }
        for (int j = 0; j < RUNNERS; j++) {
            if (!rOn[j]) continue;
            int b = box(rBox[j]);
            if (b < 0) {
                rOn[j] = false;
                continue;
            }
            int o = b * 5;
            float l = boxes[o + 1], t = boxes[o + 2], r = boxes[o + 3], bottom = boxes[o + 4];
            if (rWait[j] > 0f) rWait[j] -= dt;
            else {
                rV[j] = Math.min(150f * d, rV[j] + 260f * d * dt);
                rY[j] += rV[j] * dt;
            }
            rTop[j] = Math.max(rTop[j], rY[j] - 46f * d);
            float base = l + rX[j] * (r - l);
            float x = base + (rV[j] > 0f ? 1.2f * d * (float) Math.sin(clock * 3f + j) : 0f);
            if (t + rY[j] + rR[j] >= bottom) {   // at the foot of the box: it gathers there and drips
                rOn[j] = false;
                hang(rBox[j], rX[j], rR[j]);
                continue;
            }
            if (rY[j] - rTop[j] > d) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(WATER);
                paint.setAlpha(84);
                paint.setStrokeWidth(rR[j] * 0.9f);
                c.drawLine(base, t + rTop[j], x, t + rY[j] - rR[j], paint);
            }
            drawBead(c, x, t + rY[j], rR[j], 1f);
        }
    }

    private void addRunner() {
        for (int j = 0; j < RUNNERS; j++) {
            if (rOn[j]) continue;
            int o = rnd.nextInt(boxCount) * 5;
            float height = boxes[o + 4] - boxes[o + 2];
            rOn[j] = true;
            rBox[j] = (int) boxes[o];
            rX[j] = 0.1f + 0.8f * rnd.nextFloat();
            rY[j] = height * (0.05f + 0.4f * rnd.nextFloat());
            rTop[j] = rY[j];
            rV[j] = 0f;
            rWait[j] = 0.2f + 1.2f * rnd.nextFloat();
            rR[j] = (1.5f + 1.3f * rnd.nextFloat()) * d;
            return;
        }
    }

    /** A bead gathering under a box's lower edge. */
    private void hang(int boxId, float across, float r) {
        for (int k = 0; k < DRIPS; k++) {
            if (dOn[k]) continue;
            dOn[k] = true;
            dFalling[k] = false;
            dBox[k] = boxId;
            dAcross[k] = across;
            dR[k] = r;
            dAge[k] = 0f;
            dFell[k] = 0f;
            return;
        }
    }

    /** Beads hanging from lower edges swell, stretch and let go, falling until they burst on an edge or fade. */
    private void drips(Canvas c, float dt, int h) {
        for (int k = 0; k < DRIPS; k++) {
            if (!dOn[k]) continue;
            dAge[k] += dt;
            if (!dFalling[k]) {
                int b = box(dBox[k]);
                if (b < 0) {
                    dOn[k] = false;
                    continue;
                }
                int o = b * 5;
                float x = boxes[o + 1] + dAcross[k] * (boxes[o + 3] - boxes[o + 1]), edge = boxes[o + 4];
                float grow = Math.min(1f, dAge[k] / 0.5f);
                float r = dR[k] * (1f + 0.4f * grow);
                // The neck to the edge, then the bead under it, hanging lower as it fills.
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFE8F1FF);
                paint.setAlpha(140);
                oval.set(x - r * 0.45f, edge - r * 0.3f, x + r * 0.45f, edge + r * (0.6f + 0.8f * grow));
                c.drawOval(oval, paint);
                drawBead(c, x, edge + r * (0.6f + 0.8f * grow), r, 1f);
                if (dAge[k] >= 0.5f + 0.4f * (k % 3) / 2f) {   // heavy enough: it lets go
                    dFalling[k] = true;
                    dX[k] = x;
                    dY[k] = edge + r * 1.4f;
                    dVy[k] = 0f;
                    dR[k] = r;
                }
                continue;
            }
            float before = dY[k];
            dVy[k] += 1800f * d * dt;
            dY[k] += dVy[k] * dt;
            dFell[k] += dVy[k] * dt;
            int b = crossed(dX[k], before + dR[k], dY[k] + dR[k]);
            if (b >= 0) {
                splash(dX[k], boxes[b * 5 + 2], 0.5f);
                dOn[k] = false;
                continue;
            }
            float alpha = 1f - Math.min(1f, dFell[k] / (140f * d));
            if (alpha <= 0f || dY[k] > h) {
                dOn[k] = false;
                continue;
            }
            // Stretched by its speed, as a falling drop looks.
            float stretch = 1f + Math.min(1.6f, dVy[k] / (600f * d));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE8F1FF);
            paint.setAlpha((int) (160 * alpha));
            oval.set(dX[k] - dR[k] * 0.8f, dY[k] - dR[k] * stretch, dX[k] + dR[k] * 0.8f, dY[k] + dR[k]);
            c.drawOval(oval, paint);
        }
    }

    /** A water bead: clear body, bright glint at its upper left. */
    private void drawBead(Canvas c, float x, float y, float r, float alpha) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE8F1FF);
        paint.setAlpha((int) (150 * alpha));
        c.drawCircle(x, y, r, paint);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha((int) (225 * alpha));
        c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.32f, paint);
    }

    /** Lightning: a flicker of light across the screen every five to thirteen seconds. */
    private void flash(Canvas c, float dt, int w, int h) {
        flashDue -= dt;
        if (flashDue <= 0f) {
            flashT = 0f;
            flashDue = 5f + rnd.nextFloat() * 8f;
        }
        if (flashT < 0f) return;
        flashT += dt;
        float f = flashT;
        float a = f < 0.06f ? f / 0.06f : f < 0.14f ? 1f - (f - 0.06f) / 0.08f * 0.7f
                : f < 0.2f ? 0.3f + (f - 0.14f) / 0.06f * 0.5f : Math.max(0f, 0.8f - (f - 0.2f) / 0.5f * 0.8f);
        if (f > 0.7f) flashT = -1f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha((int) (38 * a));
        c.drawRect(0, 0, w, h, paint);
    }

    // ---- the boxes ---------------------------------------------------------------------------------------------

    /** The box whose top edge this point crossed going from y0 down to y1 (an index into boxes), or -1. */
    private int crossed(float x, float y0, float y1) {
        for (int b = 0; b < boxCount; b++) {
            int o = b * 5;
            float t = boxes[o + 2];
            if (x > boxes[o + 1] + 6f * d && x < boxes[o + 3] - 6f * d && y0 < t && y1 >= t) return b;
        }
        return -1;
    }

    /** The box with this id now, or -1 (scrolled away). */
    private int box(int id) {
        for (int b = 0; b < boxCount; b++) if ((int) boxes[b * 5] == id) return b;
        return -1;
    }

    private void grow(int size) {
        px = new float[size];
        py = new float[size];
        vy = new float[size];
        this.size = new float[size];
        depth = new float[size];
        phase = new float[size];
        freq = new float[size];
        amp = new float[size];
        rot = new float[size];
        spin = new float[size];
        flipF = new float[size];
        flipP = new float[size];
        restX = new float[size];
        restLeft = new float[size];
        tint = new int[size];
        restOn = new int[size];
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((c >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((c & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

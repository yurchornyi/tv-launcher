package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

/**
 * A holiday on Home (Holiday picks it), drawn by SeasonView over the top row of app tiles: decorations hang from the
 * tiles' top edges all day (a garland, baubles, gifts, hearts, Easter eggs, flags and wheat, pumpkins), and for a
 * "moment" a character walks along the tops of the tiles from the left and does its thing at the focused one:
 * a snowman lights a rocket for the salute (at midnight on New Year's Eve, counting down to 00:00), a caroler's star
 * spins and sings, Saint Nicholas leaves a gift on each tile, Cupid shoots the focused one, a bunny hops and an egg
 * hatches a chick, a stork walks, wheat grows and it flies off, a ghost says boo and bats fly out of the pumpkin.
 * Owner, 13.11, from a preview: minimal, but something must happen, someone must walk.
 *
 * Sizes go by the tiles (a unit is 1/146 of a tile's width, as in the preview), so it looks the same on any screen.
 * What repeats on every tile (hearts, flags, eggs, wheat, pumpkins) is drawn once into small bitmaps; the one
 * character is a few circles, ovals and rectangles. Nothing is allocated per frame.
 */
final class HolidayScene {

    private static final float TAU = (float) (2 * Math.PI);
    private static final int ROW = 8, PARTS = 200, ROCKETS = 4;
    private static final int OFF = 0, WALK = 1, ACT = 2, FLY = 3;
    private static final int DOT = 0, HEART = 1, NOTE = 2, BAT = 3;
    private static final int GOLD = 0xFFFFD782, WARM = 0xFFFFD696, PINK = 0xFFFF5F7E, ORANGE = 0xFFFFA032;
    private static final int[] HEARTS = {0xFFFF5F7E, 0xFFFF8FA8, 0xFFFFC2D1};
    private static final int[] EGGS = {0xFFE0683A, 0xFFF0C24A, 0xFF5D8FE8, 0xFF7FB96A};
    private static final int[] GIFTS = {0xFF3A6FD8, 0xFFE8C24A, 0xFFD23B3B, 0xFF3A9A6A};
    private static final int[] SPARKS = {0xFFFFD27A, 0xFFFFF1D6, 0xFFFFB3C7, 0xFFBFE0FF};
    private static final String[] DIGITS = {"1", "2", "3"};

    private final float d;
    private final Random rnd = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffColorFilter goldGlow = glowOf(GOLD), warmGlow = glowOf(WARM), orangeGlow = glowOf(ORANGE);
    private final Path path = new Path(), star = new Path(), mitre = new Path();
    private final RectF rf = new RectF();
    private final Bitmap glow;
    private Canvas k;   // the canvas being drawn on (draw's), for the shape helpers

    private Holiday.Day day;
    private float u = 1f, built, T;
    private boolean finished;

    // The row walked on: the topmost tiles on the screen, left to right.
    private int rn, focusRow;
    private final float[] rl = new float[ROW], rt = new float[ROW], rr = new float[ROW], rb = new float[ROW];
    private final int[] rid = new int[ROW];

    // The character.
    private int st = OFF, aim;   // aim: the row's tile it acts at
    private boolean acted, midnight;
    private float wx, wy, wph, wAct, wFy, wRot, spin = 1.5f;
    private int hops;

    // Sparks, hearts, notes and bats.
    private final int[] pType = new int[PARTS], pC = new int[PARTS];
    private final float[] pX = new float[PARTS], pY = new float[PARTS], pPx = new float[PARTS], pPy = new float[PARTS],
            pVx = new float[PARTS], pVy = new float[PARTS], pL = new float[PARTS], pFade = new float[PARTS],
            pG = new float[PARTS], pR = new float[PARTS], pPh = new float[PARTS];
    private final boolean[] pStuck = new boolean[PARTS];
    private int pNext;

    // New Year: the rocket on the tile, those in the air, the count to midnight.
    private final boolean[] rkOn = new boolean[ROCKETS];
    private final float[] rkX = new float[ROCKETS], rkY = new float[ROCKETS], rkTop = new float[ROCKETS];
    private final int[] rkC = new int[ROCKETS];
    private boolean rocketStands, midnightDone;
    private float rocketX, launchAt, boost;
    private int fired;
    private long midnightAt;
    private String year = "";

    // Christmas baubles (two a tile: angle and swing), Nicholas's gifts, hearts, eggs, pumpkins.
    private final float[] ballA = new float[ROW * 2], ballV = new float[ROW * 2];
    private final boolean[] giftOn = new boolean[ROW], giftBig = new boolean[ROW];
    private final float[] giftAt = new float[ROW], giftY = new float[ROW], giftVy = new float[ROW];
    private final float[] jump = new float[ROW], flare = new float[ROW];
    private float heartDue, grow, puff;
    // Cupid's arrow; the hatched egg, its shell and the chick.
    private boolean arrowOn, arrowStuck, chickOn, shellOn;
    private float arX, arY, arSx, arSy, arTx, arTy, arT;
    private int cracked = -1;
    private float chX, chY, chPh, chPop, chJump, shX, shY, shVx, shVy, shA;

    // Shapes drawn once at the tiles' size.
    private final Bitmap[] heart = new Bitmap[3], flag = new Bitmap[2], egg = new Bitmap[4], eggLow = new Bitmap[4],
            eggTop = new Bitmap[4];
    private Bitmap ear, pumpkin, face, spider;

    HolidayScene(float density) {
        d = density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setColor(0xFFFFFFFF);
        glow = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(32, 32, 32, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        new Canvas(glow).drawCircle(32, 32, 32, g);
        for (int i = 0; i < 16; i++) {   // the caroler's eight-pointed star
            double a = i * Math.PI / 8;
            float r = i % 2 == 0 ? 10f : 4.2f;
            if (i == 0) star.moveTo((float) Math.cos(a) * r, (float) Math.sin(a) * r);
            else star.lineTo((float) Math.cos(a) * r, (float) Math.sin(a) * r);
        }
        star.close();
        mitre.moveTo(-3.4f, -34f);   // Saint Nicholas's mitre
        mitre.lineTo(5.4f, -34f);
        mitre.lineTo(4.4f, -41f);
        mitre.lineTo(1f, -44.5f);
        mitre.lineTo(-2.4f, -41f);
        mitre.close();
    }

    private static PorterDuffColorFilter glowOf(int color) {
        return new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    /**
     * The holiday to show; `fresh`: Home just came back (the decorations as the day has them, no character half way);
     * `moment`: a character walks now. A moment already walking goes on.
     */
    void begin(Holiday.Day day, boolean fresh, boolean moment) {
        if (fresh || day != this.day) {
            this.day = day;
            st = OFF;
            java.util.Arrays.fill(pL, 0f);
            java.util.Arrays.fill(rkOn, false);
            java.util.Arrays.fill(ballV, 0f);
            java.util.Arrays.fill(jump, 0f);
            java.util.Arrays.fill(flare, 0f);
            settle(true);
            LocalDate today = LocalDate.now();
            boolean eve = today.getMonthValue() == 12 && today.getDayOfMonth() == 31;
            long midnightNext = eve
                    ? today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
            if (midnightNext != midnightAt) {   // a new New Year's Eve: its countdown has not played yet
                midnightAt = midnightNext;
                midnightDone = false;
            }
            year = String.valueOf(eve ? today.getYear() + 1 : today.getYear());
        }
        if (moment && st == OFF) walk(false);
    }

    /** The day's decorations at rest: every tile with its gift, the wheat grown, no arrow, no egg hatched. */
    private void settle(boolean gifts) {
        java.util.Arrays.fill(giftOn, gifts);
        java.util.Arrays.fill(giftBig, false);
        for (int i = 0; i < ROW; i++) {
            giftAt[i] = 0.7f;
            giftY[i] = -1f;
        }
        grow = 1f;
        arrowOn = false;
        cracked = -1;
        chickOn = false;
        shellOn = false;
        puff = 0f;
        rocketStands = false;
    }

    /** A moment has walked off the screen since this was last asked. */
    boolean takeFinished() {
        boolean f = finished;
        finished = false;
        return f;
    }

    private void walk(boolean atMidnight) {
        st = WALK;
        midnight = atMidnight;
        acted = false;
        wx = -40f * u;
        wy = rn > 0 ? rt[0] : -1f;   // -1: put on the tiles at the first frame that knows where they are
        wph = 0f;
        wAct = 0f;
        wFy = 0f;
        hops = 0;
        fired = 0;
        settle(false);
        if (day == Holiday.Day.INDEPENDENCE) grow = 0f;
    }

    // ---- each frame ---------------------------------------------------------------------------------------------

    void draw(Canvas c, float dt, float[] boxes, int count, int focusedId, int w, int h) {
        if (day == null) return;
        T += dt;
        readRow(boxes, count, focusedId);
        if (rn == 0) return;
        if (Math.abs(u - built) > built * 0.1f) build();
        if (midnightAt > 0 && !midnightDone && st == OFF) {
            long left = midnightAt - System.currentTimeMillis();
            if (left < 15_000 && left > -20_000) {
                midnightDone = true;
                walk(true);
            }
        }
        k = c;
        update(dt, w, h);
        decorations();
        character();
        particles(dt, w);
        k = null;
    }

    /** The topmost tiles on the screen (SeasonView's boxes below PILL), left to right; which one is focused. */
    private void readRow(float[] boxes, int count, int focusedId) {
        float top = Float.MAX_VALUE;
        for (int b = 0; b < count; b++) {
            if ((int) boxes[b * 5] < SeasonView.PILL) top = Math.min(top, boxes[b * 5 + 2]);
        }
        rn = 0;
        focusRow = -1;
        float width = 0f;
        for (int b = 0; b < count && rn < ROW; b++) {
            int o = b * 5, id = (int) boxes[o];
            if (id >= SeasonView.PILL || boxes[o + 2] > top + 14f * d) continue;
            int i = rn++;
            while (i > 0 && rl[i - 1] > boxes[o + 1]) {   // insertion by left edge
                rl[i] = rl[i - 1];
                rt[i] = rt[i - 1];
                rr[i] = rr[i - 1];
                rb[i] = rb[i - 1];
                rid[i] = rid[i - 1];
                i--;
            }
            rl[i] = boxes[o + 1];
            rt[i] = boxes[o + 2];
            rr[i] = boxes[o + 3];
            rb[i] = boxes[o + 4];
            rid[i] = id;
            width += boxes[o + 3] - boxes[o + 1];
        }
        for (int i = 0; i < rn; i++) if (rid[i] == focusedId) focusRow = i;
        if (rn > 0) u = width / rn / 146f;
    }

    private float gapHalf() {
        return rn > 1 ? Math.max(2f * u, (rl[1] - rr[0]) / 2f) : 8f * u;
    }

    /** The tile under x, if x is well within it, else -1. */
    private int under(float x) {
        for (int i = 0; i < rn; i++) if (x > rl[i] + 4f * u && x < rr[i] - 4f * u) return i;
        return -1;
    }

    /** What the character stands on at x: the top of the tile there (or of the nearest one at either end). */
    private float ground(float x) {
        float g = gapHalf();
        for (int i = 0; i < rn; i++) if (x >= rl[i] - g && x < rr[i] + g) return rt[i];
        return x < rl[0] ? rt[0] : rt[rn - 1];
    }

    private float speed() {
        switch (day) {
            case NEW_YEAR: return 80f;
            case CHRISTMAS: return 70f;
            case NICHOLAS: return 58f;
            case VALENTINE: return 85f;
            case EASTER: return 95f;
            case INDEPENDENCE: return 72f;
            default: return 70f;
        }
    }

    private float actLength() {
        switch (day) {
            case NEW_YEAR: return launchAt + 1.9f;
            case CHRISTMAS: return 2.8f;
            case EASTER: return 1.7f;
            case INDEPENDENCE: return 2.4f;
            default: return 2.5f;
        }
    }

    private void update(float dt, int w, int h) {
        boost *= (float) Math.exp(-dt * 1.4f);
        spin += ((st == ACT && day == Holiday.Day.CHRISTMAS ? 7f : 1.5f) - spin) * Math.min(1f, dt * 3f);
        wRot += spin * dt;
        for (int i = 0; i < ROW; i++) {
            jump[i] = Math.max(0f, jump[i] - dt * 2.2f);
            flare[i] = Math.max(0f, flare[i] - dt * 0.5f);
        }
        if (st == WALK) {
            if (!acted) aim = focusRow >= 0 ? focusRow : rn / 2;
            wx += speed() * u * dt;
            wph += dt * (day == Holiday.Day.EASTER ? 2.2f : day == Holiday.Day.NICHOLAS ? 5f : 6f);
            walking(dt);
            if (!acted && aim < rn && wx >= (rl[aim] + rr[aim]) / 2f - 8f * u) {
                st = ACT;
                wAct = 0f;
                launchAt = midnight ? Math.max(0.6f, (midnightAt - System.currentTimeMillis()) / 1000f) : 3f;
            }
            if (wx > w + 40f * u) end();
        } else if (st == ACT) {
            wAct += dt;
            act(dt);
            if (wAct >= actLength()) {
                acted = true;
                st = day == Holiday.Day.INDEPENDENCE ? FLY : WALK;
            }
        } else if (st == FLY) {
            wx += 130f * u * dt;
            wFy += 70f * u * dt;
            wph += dt * 14f;
            if (wx > w + 40f * u || wFy > h) end();
        }
        if (wy < 0f) wy = ground(wx);
        if (st == WALK || st == ACT) wy += (ground(wx) - wy) * Math.min(1f, dt * 14f);
        if (st != ACT) puff = Math.max(0f, puff - dt * 2f);

        if (day == Holiday.Day.CHRISTMAS) {
            for (int b = 0; b < rn * 2; b++) {
                int i = b / 2;
                float bx = rl[i] + (rr[i] - rl[i]) * (b % 2 == 0 ? 0.3f : 0.7f);
                if ((st == WALK || st == ACT) && Math.abs(bx - wx) < 7f * u && Math.abs(ballV[b]) < 0.4f) {
                    ballV[b] += (wx < bx ? 1f : -1f) * 2.4f;
                }
                ballV[b] += (-18f * ballA[b] - 1.4f * ballV[b]) * dt;
                ballA[b] += ballV[b] * dt;
            }
        }
        if (day == Holiday.Day.NICHOLAS) {
            for (int i = 0; i < rn; i++) {
                if (!giftOn[i] || giftY[i] < 0f) continue;
                giftVy[i] += 500f * u * dt;
                giftY[i] += giftVy[i] * dt;
                if (giftY[i] >= rt[i]) giftY[i] = -1f;   // landed: it sits on the tile's top from now on
            }
        }
        if (day == Holiday.Day.VALENTINE) {
            heartDue -= dt;
            if (heartDue < 0f) {
                heartDue = 0.9f + rnd.nextFloat() * 0.8f;
                int i = rnd.nextInt(rn);
                spawn(HEART, rl[i] + 20f * u + rnd.nextFloat() * Math.max(1f, rr[i] - rl[i] - 40f * u), rt[i],
                        0f, -(20f + rnd.nextFloat() * 14f) * u, 0.55f, 0f, 3f + rnd.nextFloat() * 1.5f,
                        rnd.nextInt(3));
            }
            if (arrowOn && !arrowStuck) {
                arT = Math.min(1f, arT + dt / 0.35f);
                arX = arSx + (arTx - arSx) * arT;
                arY = arSy + (arTy - arSy) * arT;
                if (arT >= 1f) {
                    arrowStuck = true;
                    flare[aim] = 1.2f;
                    for (int n = 0; n < 10; n++) {
                        spawn(HEART, arTx, arTy - 4f * u, (rnd.nextFloat() - 0.5f) * 60f * u,
                                -(40f + rnd.nextFloat() * 40f) * u, 0.55f, 0f, 4f + rnd.nextFloat() * 3f,
                                rnd.nextInt(3));
                    }
                }
            }
        }
        if (day == Holiday.Day.EASTER) {
            if (chickOn) {
                float to = wx - 34f * u;
                if (acted && chX < to) {
                    chX = Math.min(to, chX + 110f * u * dt);
                    chPh += dt * 14f;
                }
                chY += (ground(chX) - chY) * Math.min(1f, dt * 14f);
                if (chX > w + 40f * u) chickOn = false;
            }
            if (shellOn) {
                shVy += 200f * u * dt;
                shX += shVx * dt;
                shY += shVy * dt;
                shA += dt * 460f;
                if (shY > h) shellOn = false;
            }
        }
        for (int r = 0; r < ROCKETS; r++) {
            if (!rkOn[r]) continue;
            rkY[r] -= 290f * u * dt;
            if (rkY[r] <= rkTop[r]) {
                rkOn[r] = false;
                for (int n = 0; n < 36; n++) {
                    double a = n * TAU / 36 + rnd.nextFloat() * 0.1f;
                    float s = (55f + rnd.nextFloat() * 45f) * u;
                    spawn(DOT, rkX[r], rkY[r], (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.5f, 38f * u, 1.3f,
                            rkC[r]);
                }
            }
        }
    }

    private void end() {
        st = OFF;
        finished = true;
    }

    /** What the character does on the way (a sparkler's sparks, a gift for each tile, a hop that shakes an egg). */
    private void walking(float dt) {
        switch (day) {
            case NEW_YEAR:
                if (rnd.nextFloat() < 30f * dt) {
                    float bob = Math.abs((float) Math.sin(wph)) * 2.5f * u;
                    spawn(DOT, wx + 15f * u, wy - bob - 37f * u, (rnd.nextFloat() - 0.5f) * 40f * u,
                            -rnd.nextFloat() * 30f * u, 1.6f, 60f * u, 0.9f, 0xFFFFE7A8);
                }
                break;
            case NICHOLAS: {
                int i = under(wx);
                if (i >= 0 && i != aim && !giftOn[i] && wx > rl[i] + (rr[i] - rl[i]) * 0.45f) {
                    giftOn[i] = true;
                    giftAt[i] = (wx - 6f * u - rl[i]) / (rr[i] - rl[i]);
                    giftY[i] = wy - 20f * u;
                    giftVy[i] = 0f;
                }
                break;
            }
            case EASTER: {
                int n = (int) wph;
                if (n != hops) {   // a hop lands: the egg on that tile jumps
                    hops = n;
                    int i = under(wx);
                    if (i >= 0) jump[i] = 1f;
                }
                break;
            }
            default:
                break;
        }
    }

    /** What happens at the focused tile. */
    private void act(float dt) {
        int i = Math.min(aim, rn - 1);
        float a = wAct, top = rt[i];
        switch (day) {
            case NEW_YEAR: {
                if (!rocketStands && fired == 0) {
                    rocketStands = true;
                    rocketX = wx + 20f * u;
                }
                if (a < launchAt && rnd.nextFloat() < 40f * dt) {   // the fuse
                    spawn(DOT, rocketX, ground(rocketX) - 2f * u, (rnd.nextFloat() - 0.5f) * 50f * u,
                            -rnd.nextFloat() * 40f * u, 2.2f, 80f * u, 0.9f, 0xFFFFD27A);
                }
                while (fired < ROCKETS && a >= launchAt + fired * 0.45f) {
                    float x = fired == 0 ? rocketX : gap(fired);
                    rkOn[fired] = true;
                    rkX[fired] = x;
                    rkY[fired] = ground(x) - 10f * u;
                    rkTop[fired] = (40f + rnd.nextFloat() * 60f) * u * 1.4f;
                    rkC[fired] = SPARKS[fired];
                    if (fired == 0) {
                        rocketStands = false;
                        boost = 1.3f;
                    }
                    fired++;
                }
                break;
            }
            case CHRISTMAS:
                if (rnd.nextFloat() < 7f * dt) spawn(NOTE, wx + 6f * u, wy - 58f * u, 0f, -30f * u, 0.45f, 0f, 1f, 0);
                if (rnd.nextFloat() < 30f * dt) {
                    spawn(DOT, wx + 7f * u, wy - 55f * u, (rnd.nextFloat() - 0.5f) * 70f * u,
                            -rnd.nextFloat() * 40f * u, 1f, 30f * u, 1f, 0xFFFFDC8F);
                }
                break;
            case NICHOLAS:
                if (a > 0.4f && !giftBig[i]) {
                    giftOn[i] = true;
                    giftBig[i] = true;
                    giftAt[i] = (wx + 16f * u - rl[i]) / (rr[i] - rl[i]);
                    giftY[i] = wy - 24f * u;
                    giftVy[i] = 0f;
                }
                if (a > 0.8f && rnd.nextFloat() < 30f * dt) {
                    spawn(DOT, wx + 16f * u + (rnd.nextFloat() - 0.5f) * 14f * u, top - 8f * u,
                            (rnd.nextFloat() - 0.5f) * 30f * u, -(20f + rnd.nextFloat() * 30f) * u, 0.8f, 20f * u,
                            1.2f, 0xFFFFDC8F);
                }
                break;
            case VALENTINE:
                if (a > 0.55f && !arrowOn) {
                    arrowOn = true;
                    arrowStuck = false;
                    arT = 0f;
                    arSx = arX = wx + 14f * u;
                    arSy = arY = wy - 30f * u;
                    arTx = rl[i] + (rr[i] - rl[i]) * 0.62f;
                    arTy = top + 3f * u;
                }
                break;
            case EASTER:
                if (a > 0.35f && cracked < 0) {
                    cracked = i;
                    float ex = rr[i] - 20f * u, ey = top + u;
                    shellOn = true;
                    shX = ex;
                    shY = ey;
                    shVx = 40f * u;
                    shVy = -90f * u;
                    shA = 0f;
                    chickOn = true;
                    chX = ex;
                    chY = ey;
                    chPh = 0f;
                }
                if (chickOn) {
                    chPop = Math.max(0f, Math.min(1f, (a - 0.45f) / 0.4f));
                    chJump = a > 1.05f && a < 1.4f ? (float) Math.sin((a - 1.05f) / 0.35f * Math.PI) * 6f * u : 0f;
                }
                break;
            case INDEPENDENCE:
                grow = Math.max(grow, Math.min(1f, a / 1.8f));
                wph += dt * 8f;
                break;
            case HALLOWEEN:
                puff = (float) Math.sin(Math.min(1f, a / 1.1f) * Math.PI);
                if (a > 0.5f) flare[i] = Math.max(flare[i], 1f);
                if (a > 0.6f && fired < 4 && a > 0.6f + fired * 0.12f) {
                    fired++;
                    spawn(BAT, rr[i] - 26f * u, top - 10f * u, (40f + rnd.nextFloat() * 60f) * u,
                            -(60f + rnd.nextFloat() * 40f) * u, 0f, 8f * u, 1f, 0);
                }
                break;
            default:
                break;
        }
    }

    /** Between two tiles of the row (the n-th gap, wrapping), where a rocket goes up from. */
    private float gap(int n) {
        if (rn < 2) return rl[0] - gapHalf();
        int g = 1 + (n * 2) % (rn - 1);
        return (rr[g - 1] + rl[g]) / 2f;
    }

    // ---- decorations -------------------------------------------------------------------------------------------

    private void decorations() {
        switch (day) {
            case NEW_YEAR:
                garland();
                break;
            case CHRISTMAS:
                baubles();
                break;
            case NICHOLAS:
                for (int i = 0; i < rn; i++) if (giftOn[i]) gift(i);
                break;
            case VALENTINE:
                for (int i = 0; i < rn; i++) {
                    if (flare[i] > 0f) {
                        stroke.setColor(PINK);
                        stroke.setAlpha((int) (200 * Math.min(1f, flare[i])));
                        stroke.setStrokeWidth(3f * u);
                        rf.set(rl[i], rt[i], rr[i], rb[i]);
                        k.drawRoundRect(rf, 12f * u, 12f * u, stroke);
                    }
                    string(i, 20f, true);
                }
                if (arrowOn) arrow();
                break;
            case EASTER:
                for (int i = 0; i < rn; i++) {
                    float x = rr[i] - 20f * u, y = rt[i] + u;
                    if (i == cracked) continue;
                    float j = jump[i];
                    sprite(egg[i % 4], x, y - (float) Math.sin(j * Math.PI) * 12f * u,
                            (float) Math.toDegrees(Math.sin(j * 9f) * 0.3f * j), 1f, 255);
                }
                if (chickOn) chick();
                if (cracked >= 0 && cracked < rn) sprite(eggLow[cracked % 4], rr[cracked] - 20f * u, rt[cracked] + u, 0f, 1f, 255);
                if (shellOn) sprite(eggTop[Math.max(0, cracked) % 4], shX, shY, shA, 1f, 255);
                break;
            case INDEPENDENCE:
                wheat();
                for (int i = 0; i < rn; i++) string(i, 22f, false);
                break;
            case HALLOWEEN:
                for (int i = 0; i < rn; i++) {
                    float x = rr[i] - 22f * u, y = rt[i] + u, f = flare[i];
                    if (f > 0f) glow(x, y - 7f * u, (26f + f * 14f) * u, orangeGlow, 0.25f + f * 0.45f);
                    sprite(pumpkin, x, y, 0f, 1f, 255);
                    float flicker = 0.55f + 0.3f * (float) Math.sin(T * 9f + i) + 0.2f * (float) Math.sin(T * 23f + i * 3);
                    sprite(face, x, y, 0f, 1f, (int) (255 * Math.max(0.2f, Math.min(1f, flicker + f * 0.5f))));
                }
                spiderOn(0);
                break;
            default:
                break;
        }
    }

    /** Warm bulbs on a wire hung from each tile's top corners; the focused tile's brighter, all at the salute. */
    private void garland() {
        for (int i = 0; i < rn; i++) {
            float a = rl[i] + 8f * u, b = rr[i] - 8f * u, y = rt[i] + 2f * u, sag = 30f * u;
            boolean hot = i == focusRow;
            path.reset();
            path.moveTo(a, y);
            path.quadTo((a + b) / 2f, y + sag, b, y);
            stroke.setColor(0x40FFFFFF);
            stroke.setStrokeWidth(Math.max(1f, d));
            k.drawPath(path, stroke);
            for (int n = 1; n <= 6; n++) {
                float t = n / 7f, bx = curveX(a, b, t), by = y + 2f * t * (1f - t) * sag + 2.5f * u;
                float twinkle = 0.55f + 0.45f * (float) Math.sin(T * (hot ? 3.2f : 1.6f) + n * 1.7f + i * 2.3f);
                float al = Math.min(1f, (hot ? 0.75f : 0.4f) * twinkle + 0.1f + boost * 0.6f);
                glow(bx, by, (hot ? 11f : 8f + boost * 6f) * u, warmGlow, al * 0.6f);
                fill.setColor(0xFFFFE8BE);
                fill.setAlpha((int) (255 * (0.5f + al * 0.5f)));
                k.drawCircle(bx, by, (hot ? 2.5f : 2f) * u, fill);
            }
        }
    }

    private static float curveX(float a, float b, float t) {
        float m = (a + b) / 2f;
        return (1 - t) * (1 - t) * a + 2 * t * (1 - t) * m + t * t * b;
    }

    /** Hearts (Valentine's) or blue and yellow flags (24 August) on a string across a tile's top. */
    private void string(int i, float sagUnits, boolean hearts) {
        float a = rl[i] + (hearts ? 10f : 8f) * u, b = rr[i] - (hearts ? 10f : 8f) * u, y = rt[i] + 2f * u,
                sag = sagUnits * u;
        path.reset();
        path.moveTo(a, y);
        path.quadTo((a + b) / 2f, y + sag, b, y);
        stroke.setColor(0x4DFFFFFF);
        stroke.setStrokeWidth(Math.max(1f, d * 0.8f));
        k.drawPath(path, stroke);
        int count = hearts ? 6 : 7;
        for (int n = 1; n <= count; n++) {
            float t = n / (count + 1f), x = curveX(a, b, t), yy = y + 2f * t * (1f - t) * sag;
            float sway = (float) Math.toDegrees(Math.sin(T * 2f + n + i) * (hearts ? 0.12f : 0.1f));
            if (hearts) sprite(heart[n % 2 == 1 ? 0 : 2], x, yy + 4f * u, sway, 3.4f / 7f, 255);
            else sprite(flag[n % 2 == 1 ? 0 : 1], x, yy, sway, 1f, 255);
        }
    }

    private void baubles() {
        for (int b = 0; b < rn * 2; b++) {
            int i = b / 2;
            boolean left = b % 2 == 0;
            float px = rl[i] + (rr[i] - rl[i]) * (left ? 0.3f : 0.7f), py = rt[i] + u, len = (left ? 10f : 16f) * u;
            float a = ballA[b] + (float) Math.sin(T * 1.3f + i + (left ? 1.5f : 3.5f)) * 0.04f;
            float bx = px + (float) Math.sin(a) * len, by = py + (float) Math.cos(a) * len;
            stroke.setColor(0x73FFFFFF);
            stroke.setStrokeWidth(Math.max(1f, 0.8f * u));
            k.drawLine(px, py, bx, by, stroke);
            fill.setColor(left ? 0xFFE8C26A : i % 2 == 1 ? 0xFFF0F0F0 : 0xFFD9534F);
            k.drawCircle(bx, by + 4f * u, 4.2f * u, fill);
            fill.setColor(0xB3FFFFFF);
            k.drawCircle(bx - 1.4f * u, by + 2.6f * u, 1.1f * u, fill);
        }
    }

    private void gift(int i) {
        boolean big = giftBig[i];
        float s = (big ? 12f : 8f) * u, x = rl[i] + (rr[i] - rl[i]) * giftAt[i];
        float y = giftY[i] < 0f ? rt[i] : Math.min(giftY[i], rt[i]);
        if (big) glow(x, y - s / 2f, 26f * u, goldGlow, 0.35f + 0.15f * (float) Math.sin(T * 3f));
        fill.setColor(big ? 0xFFD23B3B : GIFTS[i % 4]);
        k.drawRect(x - s / 2f, y - s, x + s / 2f, y, fill);
        fill.setColor(big || i % 4 == 1 ? 0xFFFFD27A : 0xFFFFFFFF);
        if (i % 4 == 1 && !big) fill.setColor(0xFFD23B3B);
        k.drawRect(x - 0.8f * u, y - s, x + 0.8f * u, y, fill);
        k.drawRect(x - s / 2f, y - s / 2f - 0.8f * u, x + s / 2f, y - s / 2f + 0.8f * u, fill);
        k.drawCircle(x - 2f * u, y - s - 1.5f * u, 1.8f * u, fill);
        k.drawCircle(x + 2f * u, y - s - 1.5f * u, 1.8f * u, fill);
    }

    private void arrow() {
        float ang = (float) Math.toDegrees(Math.atan2(arTy - arSy, arTx - arSx));
        k.save();
        k.translate(arX, arY);
        k.rotate(ang);
        k.scale(u, u);
        stroke.setColor(0xFFE9D6A8);
        stroke.setStrokeWidth(1.1f);
        k.drawLine(-14f, 0f, arrowStuck ? -2f : 0f, 0f, stroke);
        k.restore();
        float r = (float) Math.toRadians(ang);
        sprite(heart[0], arX - (float) Math.cos(r) * 14f * u, arY - (float) Math.sin(r) * 14f * u, ang - 90f,
                2.2f / 7f, 255);
    }

    /** Wheat growing up between the tiles (24 August). */
    private void wheat() {
        if (grow <= 0f) return;
        float bottom = 0f;
        for (int i = 0; i < rn; i++) bottom = Math.max(bottom, rb[i]);
        float g = gapHalf(), len = (bottom - rt[0] + 22f * u) * grow;
        for (int n = 0; n <= rn; n++) {
            float x = n == 0 ? rl[0] - g : n == rn ? rr[rn - 1] + g : (rr[n - 1] + rl[n]) / 2f, base = bottom - 4f * u;
            k.save();
            k.rotate((float) Math.toDegrees(Math.sin(T * 1.3f + n * 1.7f) * 0.07f), x, base);
            fill.setColor(0xD9E9C46A);
            k.drawRect(x - 0.65f * u, base - len, x + 0.65f * u, base, fill);
            if (grow > 0.6f) sprite(ear, x, base - len, 0f, (grow - 0.6f) / 0.4f, 255);
            k.restore();
        }
    }

    /** A spider on its thread from a tile's top edge, going up and down (Halloween). */
    private void spiderOn(int i) {
        float x = rl[i] + 20f * u, len = (16f + 10f * (float) Math.sin(T * 0.9f)) * u, y = rt[i] + u + len;
        stroke.setColor(0x80FFFFFF);
        stroke.setStrokeWidth(Math.max(1f, 0.7f * u));
        k.drawLine(x, rt[i] + u, x, y, stroke);
        sprite(spider, x, y, 0f, 1f, 255);
    }

    // ---- the characters ----------------------------------------------------------------------------------------

    private void character() {
        if (st == OFF) return;
        switch (day) {
            case NEW_YEAR:
                if (rocketStands) rocket(rocketX, ground(rocketX));
                snowman(wx, wy, st == WALK ? wph : 0f);
                countdown();
                break;
            case CHRISTMAS:
                caroler(wx, wy, st == WALK ? wph : 0f, st == ACT ? 1f : 0f);
                break;
            case NICHOLAS:
                nicholas(wx, wy, st == WALK ? wph : 0f);
                break;
            case VALENTINE: {
                float hover = (float) Math.sin(T * (st == ACT ? 3f : 4f)) * (st == ACT ? 2f : 3f);
                cupid(wx, wy - (24f - hover) * u, T * 4f, st == ACT && wAct < 0.6f);
                break;
            }
            case EASTER: {
                float hop = st == WALK ? Math.abs((float) Math.sin(wph * Math.PI)) * 20f : 0f;
                bunny(wx, wy - hop * u, st == WALK ? (hop < 3f ? 0.9f : 1.06f) : 0.9f);
                break;
            }
            case INDEPENDENCE:
                stork(wx, st == FLY ? wy - wFy : wy, wph, st);
                break;
            case HALLOWEEN:
                ghost(wx, wy - 6f * u + (float) Math.sin(T * 2.4f) * 3f * u, puff);
                break;
            default:
                break;
        }
        for (int r = 0; r < ROCKETS; r++) {
            if (!rkOn[r]) continue;
            fill.setColor(rkC[r]);
            fill.setAlpha(204);
            k.drawRect(rkX[r] - 0.8f * u, rkY[r], rkX[r] + 0.8f * u, rkY[r] + 18f * u, fill);
            glow(rkX[r], rkY[r], 8f * u, warmGlow, 0.8f);
        }
    }

    /** 3, 2, 1 over the rocket (to the second at midnight), then the new year's number. */
    private void countdown() {
        if (st != ACT || day != Holiday.Day.NEW_YEAR) return;
        float left = launchAt - wAct, y = rt[Math.min(aim, rn - 1)] - 36f * u;
        if (left > 0f && left <= 3f) {
            int n = (int) Math.ceil(left);
            float f = left - (n - 1);
            text.setTextSize((16f + 6f * (1f - f)) * u);
            text.setAlpha((int) (255 * Math.min(1f, f * 1.4f)));
            k.drawText(DIGITS[Math.max(0, Math.min(2, n - 1))], rocketX, y, text);
        } else if (left <= 0f && -left < 2f) {
            text.setTextSize(18f * u);
            text.setAlpha((int) (255 * Math.min(1f, Math.min(-left * 3f, (2f + left) * 2f))));
            k.drawText(year, rocketX, y - 20f * u, text);
        }
    }

    private void at(float x, float y) {
        k.save();
        k.translate(x, y);
        k.scale(u, u);
    }

    private void color(int c) {
        fill.setColor(c);
    }

    private void circle(float x, float y, float r) {
        k.drawCircle(x, y, r, fill);
    }

    private void oval(float cx, float cy, float rx, float ry) {
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry);
        k.drawOval(rf, fill);
    }

    private void box(float x, float y, float w, float h) {
        k.drawRect(x, y, x + w, y + h, fill);
    }

    private void line(int c, float width, float x0, float y0, float x1, float y1) {
        stroke.setColor(c);
        stroke.setStrokeWidth(width);
        k.drawLine(x0, y0, x1, y1, stroke);
    }

    private void tri(float x0, float y0, float x1, float y1, float x2, float y2) {
        path.reset();
        path.moveTo(x0, y0);
        path.lineTo(x1, y1);
        path.lineTo(x2, y2);
        path.close();
        k.drawPath(path, fill);
    }

    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x0, y0);
        path.lineTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        k.drawPath(path, fill);
    }

    /** A polygon of any size (only for the bitmaps: it takes an array). */
    private void poly(float... xy) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        k.drawPath(path, fill);
    }

    private void snowman(float x, float y, float ph) {
        float bob = Math.abs((float) Math.sin(ph)) * 2.5f;
        at(x, y - bob * u);
        k.rotate((float) Math.toDegrees(Math.sin(ph) * 0.07f));
        color(0xFFEEF2F7);
        circle(0f, -9f, 9f);
        circle(0f, -22f, 6.5f);
        circle(0f, -32f, 4.8f);
        color(0xFFD94A4A);
        box(-5f, -28f, 10f, 2.6f);
        box(1.5f, -28f, 2.6f, 6f);
        color(0xFF1B1B1F);
        box(-5.5f, -37.5f, 11f, 1.6f);
        box(-3.4f, -44f, 6.8f, 6.6f);
        circle(-0.6f, -33.5f, 0.8f);
        circle(2.2f, -33.5f, 0.8f);
        color(0xFFF08A24);
        tri(4f, -32f, 9f, -31f, 4f, -30.2f);
        line(0xFF6B4A2E, 1.2f, 5f, -23f, 12f, -28f);
        line(0xFF6B4A2E, 1.2f, -5f, -23f, -11f, -19f);
        line(0xFFAAAAAA, 1.2f, 12f, -28f, 15f, -36f);
        k.restore();
    }

    private void rocket(float x, float y) {
        at(x, y);
        color(0xFFD9534F);
        box(-1.6f, -14f, 3.2f, 10f);
        color(0xFFFFD27A);
        tri(-2.2f, -14f, 0f, -18f, 2.2f, -14f);
        line(0xFF8A6A44, 1f, 0f, -4f, 0f, 0f);
        k.restore();
    }

    private void caroler(float x, float y, float ph, float shine) {
        float s = (float) Math.sin(ph);
        at(x, y);
        line(0xFF3A2A22, 2f, -2f, -9f, -2f + s * 3f, 0f);
        line(0xFF3A2A22, 2f, 2f, -9f, 2f - s * 3f, 0f);
        color(0xFFB3423A);
        quad(-6f, -9f, 6f, -9f, 4f, -22f, -4f, -22f);
        color(0xFFF1D1B5);
        circle(0f, -26.5f, 4.5f);
        color(0xFF2B2F3A);
        rf.set(-4.9f, -32.4f, 4.9f, -22.6f);
        k.drawArc(rf, 180f, 180f, true, fill);
        line(0xFF8A6A44, 1.4f, 7f, -50f, 7f, -12f);
        line(0xFFB3423A, 2.2f, 3f, -20f, 7f, -18f);
        k.restore();
        glow(x + 7f * u, y - 55f * u, (22f + shine * 14f) * u, goldGlow, 0.35f + shine * 0.3f);
        at(x + 7f * u, y - 55f * u);
        k.rotate((float) Math.toDegrees(wRot));
        color(0xFFFFD36B);
        k.drawPath(star, fill);
        color(0xFFD9534F);
        circle(0f, 0f, 2.8f);
        k.restore();
    }

    private void nicholas(float x, float y, float ph) {
        float bob = Math.abs((float) Math.sin(ph)) * 1.6f, sw = (float) Math.sin(ph) * 1.5f;
        at(x, y - bob * u);
        color(0xFF7A4A2A);
        circle(-9f, -20f, 6.5f);   // the bag of gifts on his back
        color(0xFF8E2A2A);
        quad(-6f, -27f, 6f, -27f, 9f + sw, 0f, -9f + sw, 0f);
        color(0xFFEFE6D2);
        box(-9f + sw, -3f, 18f, 3f);
        box(-1.2f, -27f, 2.4f, 24f);
        color(0xFFF1D1B5);
        circle(1f, -31f, 4.3f);
        color(0xFFF6F6F6);
        oval(2f, -27.5f, 4f, 4.6f);
        color(0xFFE2BF62);   // the mitre
        k.drawPath(mitre, fill);
        color(0xFF8E2A2A);
        box(0.4f, -43f, 1.2f, 8f);
        line(0xFFD8B46A, 1.5f, 11f, 0f, 11f, -44f);   // the staff and its crook
        stroke.setStrokeWidth(1.5f);
        rf.set(5f, -47f, 11f, -41f);
        k.drawArc(rf, 0f, -180f, false, stroke);
        color(0xFFF1D1B5);
        circle(10.2f, -22f, 1.8f);
        k.restore();
    }

    private void cupid(float x, float y, float ph, boolean drawing) {
        float flap = (float) Math.sin(ph * 2.4f);
        at(x, y);
        color(0xD9FFFFFF);
        for (int s = -1; s <= 1; s += 2) {
            k.save();
            k.translate(-3f, -14f);
            k.rotate((float) Math.toDegrees(s * 0.25f - 0.6f + flap * 0.35f));
            oval(-6f, 0f, 7f, 3.4f);
            k.restore();
        }
        color(0xFFF6D2B8);
        circle(0f, -10f, 5.6f);
        circle(1.5f, -19f, 4.8f);
        color(0xFFE8C26A);
        circle(-1.5f, -22.5f, 1.8f);
        circle(1.5f, -23.6f, 1.8f);
        circle(4.2f, -22f, 1.8f);
        line(0xFFE0526E, 1.6f, -4.5f, -13f, 4.5f, -7f);
        stroke.setColor(0xFFA5763F);
        stroke.setStrokeWidth(1.4f);
        rf.set(2f, -19f, 16f, -5f);
        k.drawArc(rf, -71.6f, 143.2f, false, stroke);
        float pull = drawing ? 3.5f : 0f;
        line(0xBFFFFFFF, 0.7f, 11.2f, -18.6f, 9f - pull, -12f);
        line(0xBFFFFFFF, 0.7f, 9f - pull, -12f, 11.2f, -5.4f);
        if (drawing) line(0xFFE9D6A8, 1f, 9f - pull, -12f, 20f, -10f);
        k.restore();
    }

    private void bunny(float x, float y, float squash) {
        at(x, y);
        k.scale(1f / (float) Math.sqrt(squash), squash);
        color(0xFFF4EDE3);
        oval(0f, -7f, 10f, 7f);
        circle(9f, -13f, 5.2f);
        for (int e = 0; e < 2; e++) {
            k.save();
            k.translate(e == 0 ? 7.5f : 10.5f, -17f);
            k.rotate(e == 0 ? -23f : -7f);
            oval(0f, -6f, 1.9f, 6.5f);
            k.restore();
        }
        color(0xFFFFFFFF);
        circle(-10f, -9f, 2.8f);
        color(0xFF2A2420);
        circle(11.3f, -14f, 0.9f);
        k.restore();
    }

    private void chick() {
        float s = (float) Math.sin(chPh);
        at(chX, chY + (1f - chPop) * 11f * u - chJump);
        line(0xFFE08A2A, 1f, -1.5f, -3f, -1.5f + s * 1.5f, 0f);
        line(0xFFE08A2A, 1f, 1.5f, -3f, 1.5f - s * 1.5f, 0f);
        color(0xFFFFD84A);
        circle(0f, -6.5f, 4.6f);
        circle(2.8f, -11f, 3.2f);
        color(0xFFE8892A);
        tri(5.6f, -11.5f, 8f, -10.8f, 5.6f, -10f);
        color(0xFF222222);
        circle(3.8f, -11.8f, 0.7f);
        k.restore();
    }

    private void stork(float x, float y, float ph, int state) {
        boolean walking = state == WALK, flying = state == FLY;
        float bob = walking ? Math.abs((float) Math.sin(ph)) * 1.5f : 0f, yb = -21f - bob;
        at(x, y);
        if (flying) line(0xFFE0533A, 1.3f, -8f, yb + 1f, -24f, yb + 3f);
        else {
            float s = walking ? (float) Math.sin(ph) : 0f;
            line(0xFFE0533A, 1.3f, -1f, yb + 4f, -1f + s * 5f, 0f);
            line(0xFFE0533A, 1.3f, 2f, yb + 4f, 2f - s * 5f, 0f);
        }
        color(0xFFF4F4F0);
        k.save();
        k.rotate(-7f, 0f, yb);
        oval(0f, yb, 11f, 5.5f);
        color(0xFF1D1D1F);
        oval(-7f, yb + 1f, 6f, 3.2f);
        k.restore();
        float hx, hy;
        stroke.setColor(0xFFF4F4F0);
        stroke.setStrokeWidth(2.4f);
        path.reset();
        if (flying) {
            path.moveTo(8f, yb - 1f);
            path.lineTo(19f, yb - 4f);
            hx = 20f;
            hy = yb - 4f;
        } else {
            float nod = walking ? (float) Math.sin(ph * 2f) * 1.2f : 0f;
            path.moveTo(8f, yb - 2f);
            path.quadTo(13f, yb - 10f, 10f + nod, yb - 19f);
            hx = 10f + nod;
            hy = yb - 19f;
        }
        k.drawPath(path, stroke);
        color(0xFFF4F4F0);
        circle(hx, hy, 2.8f);
        line(0xFFE0533A, 1.6f, hx + 2f, hy, hx + 11f, hy + (flying ? 1f : 2f));
        if (!walking) {   // the wing, beating
            float f = (float) Math.sin(ph);
            color(0xFFF4F4F0);
            tri(-5f, yb - 2f, 6f, yb - 2f, -4f, yb - 2f - 16f * f);
            line(0xFF1D1D1F, 2.2f, -4f, yb - 2f - 16f * f, -1.5f, yb - 2f - 10f * f);
        }
        k.restore();
    }

    private void ghost(float x, float y, float boo) {
        at(x, y);
        float s = 1f + boo * 0.28f;
        k.scale(s, s);
        color(0xF0F3F3F8);
        path.reset();
        path.moveTo(-9f, -2f);
        path.arcTo(-9f, -24f, 9f, -6f, 180f, 180f, false);
        path.lineTo(9f, -2f);
        for (int n = 0; n <= 4; n++) {
            path.lineTo(9f - n * 4.5f, -2f + (n % 2 == 1 ? 3f : 0f) + (float) Math.sin(T * 6f + n) * 1.2f);
        }
        path.close();
        k.drawPath(path, fill);
        color(0xFF1C1B22);
        oval(-3f, -16f, 1.6f, 2.4f);
        oval(3f, -16f, 1.6f, 2.4f);
        if (boo > 0.2f) oval(0f, -9.5f, 1.6f * boo + 0.5f, 2.3f * boo + 0.5f);
        k.restore();
    }

    // ---- sparks, hearts, notes, bats ---------------------------------------------------------------------------

    /** A new piece: `fade` is how much life goes a second, `g` its fall, `c` a colour (a heart: which one). */
    private void spawn(int type, float x, float y, float vx, float vy, float fade, float g, float r, int c) {
        for (int n = 0; n < PARTS; n++) {
            int p = (pNext + n) % PARTS;
            if (pL[p] > 0f) continue;
            pNext = (p + 1) % PARTS;
            pType[p] = type;
            pX[p] = pPx[p] = x;
            pY[p] = pPy[p] = y;
            pVx[p] = vx;
            pVy[p] = vy;
            pL[p] = 1f;
            pFade[p] = fade;
            pG[p] = g;
            pR[p] = r;
            pC[p] = c;
            pPh[p] = rnd.nextFloat() * TAU;
            pStuck[p] = false;
            return;
        }
    }

    private void particles(float dt, int w) {
        for (int p = 0; p < PARTS; p++) {
            if (pL[p] <= 0f) continue;
            pPx[p] = pX[p];
            pPy[p] = pY[p];
            switch (pType[p]) {
                case DOT:
                    if (!pStuck[p]) {
                        pVx[p] *= 1f - 1.2f * dt;
                        pVy[p] = pVy[p] * (1f - 1.2f * dt) + pG[p] * dt;
                        pX[p] += pVx[p] * dt;
                        pY[p] += pVy[p] * dt;
                        int i = under(pX[p]);
                        if (i >= 0 && pVy[p] > 0f && pY[p] > rt[i] && pY[p] < rt[i] + 8f * u) {
                            pStuck[p] = true;   // a spark settles on the tile as glitter
                            pY[p] = rt[i] - u;
                            pL[p] = Math.min(pL[p], 0.9f);
                        }
                    }
                    pL[p] -= dt * (pStuck[p] ? 0.4f : pFade[p]);
                    break;
                case HEART:
                    pVx[p] *= 1f - 1.5f * dt;
                    pX[p] += (pVx[p] + (float) Math.sin(T * 2f + pPh[p]) * 10f * u) * dt;
                    pY[p] += pVy[p] * dt;
                    pL[p] -= dt * pFade[p];
                    break;
                case NOTE:
                    pX[p] += (float) Math.sin(T * 3f + pPh[p]) * 14f * u * dt;
                    pY[p] += pVy[p] * dt;
                    pL[p] -= dt * pFade[p];
                    break;
                default:   // a bat
                    pVy[p] += pG[p] * dt;
                    pX[p] += pVx[p] * dt;
                    pY[p] += pVy[p] * dt;
                    if (pY[p] < -30f * u || pX[p] > w + 30f * u) pL[p] = 0f;
                    break;
            }
            if (pL[p] <= 0f) continue;
            float a = Math.min(1f, pL[p]);
            switch (pType[p]) {
                case DOT:
                    fill.setColor(pC[p]);
                    fill.setAlpha((int) (255 * a));
                    k.drawCircle(pX[p], pY[p], pR[p] * u, fill);
                    if (!pStuck[p]) {
                        fill.setAlpha((int) (110 * a));
                        k.drawCircle((pX[p] + pPx[p]) / 2f, (pY[p] + pPy[p]) / 2f, pR[p] * 0.7f * u, fill);
                    }
                    break;
                case HEART:
                    sprite(heart[pC[p] % 3], pX[p], pY[p], 0f, pR[p] / 7f, (int) (255 * a));
                    break;
                case NOTE:
                    at(pX[p], pY[p]);
                    fill.setColor(0xFFFFE7B0);
                    fill.setAlpha((int) (255 * a));
                    k.rotate(-23f);
                    oval(0f, 0f, 2.6f, 1.9f);
                    k.restore();
                    at(pX[p], pY[p]);
                    fill.setColor(0xFFFFE7B0);
                    fill.setAlpha((int) (255 * a));
                    box(1.8f, -9f, 1f, 9f);
                    box(1.8f, -9f, 3.4f, 1.2f);
                    k.restore();
                    break;
                default: {
                    float f = (float) Math.sin(T * 18f + pPh[p]);
                    at(pX[p], pY[p]);
                    color(0xFF7B6A8C);
                    oval(0f, 0f, 2.6f, 2f);
                    quad(1.5f, 0f, 9f, -5f * f, 6f, 1f, 3.5f, 0f);
                    quad(-1.5f, 0f, -9f, -5f * f, -6f, 1f, -3.5f, 0f);
                    k.restore();
                    break;
                }
            }
        }
    }

    // ---- the bitmaps -------------------------------------------------------------------------------------------

    private void glow(float x, float y, float r, PorterDuffColorFilter tint, float alpha) {
        rf.set(x - r, y - r, x + r, y + r);
        img.setColorFilter(tint);
        img.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
        k.drawBitmap(glow, null, rf, img);
        img.setColorFilter(null);
    }

    /**
     * A bitmap drawn at x, y (its origin: an egg's or a pumpkin's foot, a flag's top), turned by `deg`, at `scale`.
     * Each was drawn at the tiles' size with its origin in the middle of the bitmap (shape).
     */
    private void sprite(Bitmap b, float x, float y, float deg, float scale, int alpha) {
        if (b == null) return;
        k.save();
        k.translate(x, y);
        if (deg != 0f) k.rotate(deg);
        if (scale != 1f) k.scale(scale, scale);
        img.setAlpha(alpha);
        k.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, img);
        k.restore();
    }

    /** Draws the repeated shapes again at the tiles' size now. */
    private void build() {
        built = u;
        for (int n = 0; n < 3; n++) heart[n] = shape(10f, n, 0);
        for (int n = 0; n < 2; n++) flag[n] = shape(11f, n, 1);
        for (int n = 0; n < 4; n++) {
            egg[n] = shape(21f, n, 2);
            eggLow[n] = shape(21f, n, 3);
            eggTop[n] = shape(21f, n, 4);
        }
        ear = shape(26f, 0, 5);
        pumpkin = shape(17f, 0, 6);
        face = shape(17f, 0, 7);
        spider = shape(8f, 0, 8);
    }

    /** One shape in a square reaching `units` out from its origin, which is the square's middle (see sprite). */
    private Bitmap shape(float units, int n, int what) {
        int px = Math.max(2, Math.round(units * u * 2f));
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate(px / 2f, px / 2f);
        c.scale(u, u);
        Canvas keep = k;
        k = c;
        switch (what) {
            case 0: {   // a heart, 7 units across
                color(HEARTS[n]);
                path.reset();
                path.moveTo(0f, 5.6f);
                path.cubicTo(-8.75f, -1.05f, -4.2f, -7.35f, 0f, -2.45f);
                path.cubicTo(4.2f, -7.35f, 8.75f, -1.05f, 0f, 5.6f);
                c.drawPath(path, fill);
                break;
            }
            case 1:   // a flag, hanging from its top edge
                color(n == 0 ? 0xFF3B7DDD : 0xFFFFD43B);
                tri(-4.5f, 0f, 4.5f, 0f, 0f, 10f);
                break;
            case 2:
            case 3:
            case 4: {   // an Easter egg standing on its foot; its lower or upper half once it hatches
                c.save();
                if (what == 3) c.clipRect(-9f, -9f, 9f, 1f);
                if (what == 4) c.clipRect(-9f, -20f, 9f, -9f);
                color(EGGS[n]);
                path.reset();
                path.addOval(-7f, -18.5f, 7f, 0.5f, Path.Direction.CW);
                c.drawPath(path, fill);
                c.clipPath(path);
                color(0xE6FFFFFF);
                box(-8f, -11f, 16f, 2.2f);
                box(-8f, -6.6f, 16f, 1.1f);
                color(0xCC28140A);
                circle(-3.4f, -14.6f, 0.9f);
                circle(0f, -14.6f, 0.9f);
                circle(3.4f, -14.6f, 0.9f);
                c.restore();
                if (what == 3) {
                    stroke.setColor(0xE6FFFFFF);
                    stroke.setStrokeWidth(1f);
                    path.reset();
                    for (int z = 0; z <= 6; z++) {
                        float zx = -7f + z * 14f / 6f, zy = -9f - (z % 2 == 1 ? 2.5f : 0f);
                        if (z == 0) path.moveTo(zx, zy);
                        else path.lineTo(zx, zy);
                    }
                    c.drawPath(path, stroke);
                }
                break;
            }
            case 5:   // an ear of wheat, its stalk's top at the origin
                color(0xFFE9C46A);
                for (int g = 0; g < 5; g++) {
                    for (int s = -1; s <= 1; s += 2) {
                        c.save();
                        c.translate(s * 2f, 4f + g * 4.2f - 12f);
                        c.rotate(s * 26f);
                        oval(0f, 0f, 1.7f, 3.4f);
                        c.restore();
                    }
                }
                line(0xFFE9C46A, 1.3f, 0f, -12f, 0f, -20f);
                break;
            case 6:   // a pumpkin on its foot
                color(0xFFC85A10);
                oval(-4f, -6.5f, 5.5f, 6.5f);
                oval(4f, -6.5f, 5.5f, 6.5f);
                color(0xFFE8741C);
                oval(0f, -6.5f, 5.5f, 7f);
                color(0xFF5C7A2A);
                box(-1f, -15.5f, 2f, 3.5f);
                break;
            case 7:   // its face, lit from inside
                color(0xFFFFD65A);
                poly(4.6f, -8.5f, 1.4f, -8.5f, 3f, -11f);
                poly(-4.6f, -8.5f, -1.4f, -8.5f, -3f, -11f);
                poly(-5f, -5f, -2.5f, -3.2f, 0f, -4.6f, 2.5f, -3.2f, 5f, -5f, 2.5f, -1.8f, -2.5f, -1.8f);
                break;
            default:   // a spider, hanging by its back
                for (int s = -1; s <= 1; s += 2) {
                    for (int l = 0; l < 4; l++) {
                        line(0xFFCFCFD6, 0.9f, 0f, 0f, s * 4.5f, -3f + l * 2.2f);
                        line(0xFFCFCFD6, 0.9f, s * 4.5f, -3f + l * 2.2f, s * 6f, -1f + l * 2.6f);
                    }
                }
                color(0xFFCFCFD6);
                circle(0f, 0f, 2.6f);
                circle(0f, -3.2f, 1.6f);
                break;
        }
        k = keep;
        return b;
    }
}

package com.serhii.cleanhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * Заставка «раскрытие» при включении ТВ. Математика один в один с Unfold Lab
 * (браузерный симулятор), параметры ниже — подобранные там.
 *
 * Небольшой складной девайс: левая половина неподвижна, правая створка откидывается
 * от шарнира к зрителю. Картинка не поворачивается со створкой — створка как стекло
 * над неподвижным изображением: у шарнира резко, к краю размыто и темнее.
 * У створки есть толщина — пока она стоит к нам углом, виден металлический торец.
 * Со второй половины пути камера наезжает, и в конце рамка уходит за края ТВ.
 */
public class Unfold extends View {

    /** Откуда взять текущий кадр фонового видео (SurfaceView в снимок не попадает). */
    public interface FrameSource { Bitmap frame(int w, int h); }

    // ---- параметры из Unfold Lab (пиксели — в пересчёте на ширину 1920) ----
    private static final long OPEN_MS = 950;
    private static final float EASE_X1 = .32f, EASE_Y1 = 0f, EASE_X2 = .15f, EASE_Y2 = 1f;
    private static final float START_DEG = 90f;
    private static final float FOCAL = 1.5f;
    private static final float MID_BLUR = 80, HEAVY_BLUR = 160;
    private static final float MID_STRENGTH = 2f, HEAVY_STRENGTH = 2f;
    private static final float GAP_POW = .5f;
    private static final float DIM_HINGE = .09f, DIM_EDGE = .63f, EDGE_LINE = .33f;
    private static final long FADE_MS = 200;
    private static final float BEZEL = 1, RADIUS = 70, DEPTH = 38;
    private static final float START_SCALE = .56f, ZOOM_START = .5f;

    private static final int SNAP_DIV = 2;     // резкий кадр — половина разрешения
    private static final float[] MID_CURVE = {0f, .35f, .8f, 1f, 1f};
    private static final float[] HEAVY_CURVE = {0f, .016f, .125f, .42f, 1f};  // куб к краю
    private static final float[] STOPS = {0f, .25f, .5f, .75f, 1f};

    private final ViewGroup root;
    private final View sky;
    private final FrameSource video;

    private Bitmap sharp, mid, heavy;
    private final Paint sharpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint midPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint heavyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Shader midBase, heavyBase;
    private final Paint dimPaint = new Paint();
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hingePaint = new Paint();
    private final Paint torPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float p;          // 0 — сложен, 1 — раскрыт (уже с кривой)
    private boolean ready;
    private ValueAnimator anim;

    public Unfold(Context c, ViewGroup root, View sky, FrameSource video) {
        super(c);
        this.root = root;
        this.sky = sky;
        this.video = video;
        setVisibility(GONE);
        setFocusable(false);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setColor(Color.WHITE);
        bodyPaint.setColor(0xFF17191C);
        rimLine.setStyle(Paint.Style.STROKE);
        rimLine.setColor(0x24FFFFFF);
        hingePaint.setColor(0xFF2A2D31);
    }

    /** Закрыть экран чёрным, пока кадр не готов (сразу при запуске). */
    public void cover() {
        cancel();
        p = 0f;
        ready = false;
        setAlpha(1f);
        setVisibility(VISIBLE);
        invalidate();
    }

    /** Снять кадр главного экрана и встать в «сложенное» положение. */
    public void prepare() {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        cancel();
        recycle();
        try {
            setVisibility(GONE);
            int sw = w / SNAP_DIV, sh = h / SNAP_DIV;
            Bitmap frame = video == null ? null : video.frame(sw, sh);

            sharp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(sharp);
            c.save();
            c.scale(1f / SNAP_DIV, 1f / SNAP_DIV);
            float skyAlpha = sky.getAlpha();
            if (frame == null) sky.setAlpha(1f); // видео нет — в кадре небо
            root.draw(c);
            sky.setAlpha(skyAlpha);
            c.restore();
            if (frame != null) {
                // интерфейс уже нарисован; кадр видео подкладываем ПОД него
                Paint under = new Paint(Paint.FILTER_BITMAP_FLAG);
                under.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
                c.drawBitmap(frame, null, new Rect(0, 0, sw, sh), under);
                frame.recycle();
            }

            mid = blurred(sharp, w, h, MID_BLUR);
            heavy = blurred(sharp, w, h, HEAVY_BLUR);

            sharpPaint.setShader(shader(sharp, w, h));
            midBase = shader(mid, w, h);
            heavyBase = shader(heavy, w, h);

            p = 0f;
            ready = true;
            setAlpha(1f);
            setVisibility(VISIBLE);
            invalidate();
        } catch (Throwable t) { // нехватка памяти и т.п. — просто без заставки
            recycle();
            setVisibility(GONE);
        }
    }

    /** Размытие как в Lab: копия шириной 5760/px (≈3 px исходника на пиксель) + 3 прохода box blur. */
    private static Bitmap blurred(Bitmap src, int w, int h, float px1920) {
        int bw = Math.max(1, Math.round(5760f / Math.max(1f, px1920)));
        bw = Math.min(bw, src.getWidth());
        int bh = Math.max(1, Math.round(bw * h / (float) w));
        Bitmap b = Bitmap.createScaledBitmap(src, bw, bh, true);
        boxBlur(b, 1, 3);
        return b;
    }

    private static Shader shader(Bitmap b, int w, int h) {
        Shader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(w / (float) b.getWidth(), h / (float) b.getHeight());
        s.setLocalMatrix(m);
        return s;
    }

    /** Проиграть раскрытие. Если кадра нет — снимет сам. */
    public void play() {
        if (!ready) prepare();
        if (!ready) { setVisibility(GONE); return; }
        cancel();
        PathInterpolator ease = new PathInterpolator(EASE_X1, EASE_Y1, EASE_X2, EASE_Y2);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(OPEN_MS);
        anim.setInterpolator(ease);
        anim.addUpdateListener(v -> { p = (float) v.getAnimatedValue(); invalidate(); });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator x) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator x) {
                if (cancelled) return;
                // в конце кадр совпадает с живым экраном — растворяем коротко
                animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                    setVisibility(GONE);
                    recycle();
                }).start();
            }
        });
        anim.start();
    }

    /** Убрать заставку без анимации. */
    public void hide() {
        cancel();
        setVisibility(GONE);
        recycle();
    }

    public boolean isActive() { return getVisibility() == VISIBLE; }

    private void cancel() {
        if (anim != null) { anim.cancel(); anim = null; }
        animate().cancel();
    }

    private void recycle() {
        ready = false;
        sharpPaint.setShader(null);
        midPaint.setShader(null);
        heavyPaint.setShader(null);
        midBase = heavyBase = null;
        if (sharp != null) { sharp.recycle(); sharp = null; }
        if (mid != null) { mid.recycle(); mid = null; }
        if (heavy != null) { heavy.recycle(); heavy = null; }
    }

    // ---------- кадр ----------

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        if (!ready) return;

        float k = w / 1920f;
        float cx = w / 2f, cy = h / 2f, B = BEZEL * k, R0 = RADIUS * k;

        // наезд: от START_SCALE до 1 — при 1 рамка ровно за краем экрана
        float u = Math.max(0f, Math.min(1f, (p - ZOOM_START) / Math.max(1e-3f, 1f - ZOOM_START)));
        float zu = u * u * (3 - 2 * u);
        float S = B > 0 ? START_SCALE + (1 - START_SCALE) * zu : 1f;
        float R = R0 * (1 - zu);          // к концу скругление уходит — углы не видны

        int save = c.save();
        c.scale(S, S, cx, cy);

        double th = Math.toRadians(START_DEG * (1f - p));
        float d = cx, f = w * FOCAL;
        float z = (float) (d * Math.sin(th));
        float s = f / Math.max(1f, f - z);
        float freeX = cx + (float) (d * Math.cos(th)) * s;
        float dir = Math.signum(freeX - cx); if (dir == 0) dir = 1;
        float top = cy - cy * s, bot = cy + cy * s;
        boolean open = Math.abs(freeX - cx) >= 2f;

        // корпус
        if (B > 0) {
            rimLine.setStrokeWidth(1.5f / S);
            rpoly(path, new float[]{-B, -B, cx, -B, cx, h + B, -B, h + B},
                    new float[]{R + B, 0, 0, R + B});
            c.drawPath(path, bodyPaint);
            c.drawPath(path, rimLine);
            if (Math.abs(freeX - cx) > 4f) {
                float e = B * s;
                rpoly(path, new float[]{cx, -B, freeX + dir * e, top - e, freeX + dir * e, bot + e, cx, h + B},
                        new float[]{0, (R + B) * s, (R + B) * s, 0});
                c.drawPath(path, bodyPaint);
                c.drawPath(path, rimLine);
            }
            c.drawRect(cx - B * .25f, -B, cx + B * .25f, 0, hingePaint);
            c.drawRect(cx - B * .25f, h, cx + B * .25f, h + B, hingePaint);
        }

        // левый экран — неподвижный
        int s2 = c.save();
        float lr = B > 0 ? R : 0;
        rpoly(path, new float[]{0, 0, cx, 0, cx, h, 0, h}, new float[]{lr, 0, 0, lr});
        c.clipPath(path);
        c.drawRect(0, 0, cx, h, sharpPaint);
        c.restoreToCount(s2);

        float gap = (float) Math.pow(Math.max(0, Math.sin(th)), GAP_POW);
        if (open) {
            // правая створка: картинка в своих координатах — стоит на месте
            s2 = c.save();
            float pr = B > 0 ? R * s : 0;
            rpoly(path, new float[]{cx, 0, freeX, top, freeX, bot, cx, h}, new float[]{0, pr, pr, 0});
            c.clipPath(path);
            float l = Math.min(cx, freeX), r = Math.max(cx, freeX);
            c.drawRect(l, Math.min(0, top), r, Math.max(h, bot), sharpPaint);
            if (gap > .01f) {
                midPaint.setShader(masked(midBase, cx, freeX, gap * MID_STRENGTH, MID_CURVE));
                c.drawRect(l, Math.min(0, top), r, Math.max(h, bot), midPaint);
                heavyPaint.setShader(masked(heavyBase, cx, freeX, gap * HEAVY_STRENGTH, HEAVY_CURVE));
                c.drawRect(l, Math.min(0, top), r, Math.max(h, bot), heavyPaint);
                // притемнение к свободному краю (яркость — после размытия)
                int a0 = (int) (255 * DIM_HINGE * gap), a1 = (int) (255 * DIM_EDGE * gap);
                dimPaint.setShader(new LinearGradient(cx, 0, freeX, 0,
                        a0 << 24, a1 << 24, Shader.TileMode.CLAMP));
                c.drawRect(l, Math.min(0, top), r, Math.max(h, bot), dimPaint);
            }
            c.restoreToCount(s2);

            // тонкая резкая грань стекла
            if (gap > .01f && EDGE_LINE > 0) {
                edgePaint.setStrokeWidth(Math.max(1.5f, w / 900f) / S);
                edgePaint.setAlpha((int) (255 * EDGE_LINE * gap));
                c.drawLine(freeX, top + R * s, freeX, bot - R * s, edgePaint);
            }
        }

        drawRim(c, th, cx, cy, d, f, B, DEPTH * k, S);
        c.restoreToCount(save);
    }

    /**
     * Торец свободного края: створка — пластина толщиной T. Лицевую и тыльную кромки
     * считаем в 3D и проецируем той же камерой. Отвернулся от камеры — не рисуем.
     */
    private void drawRim(Canvas c, double th, float cx, float cy, float d, float f,
                         float B, float T, float S) {
        if (B <= 0 || T <= 0) return;
        float ux = (float) Math.cos(th), uw = (float) Math.sin(th);   // вдоль створки (w — к зрителю)
        float bx = (float) Math.sin(th), bw = (float) -Math.cos(th);  // тыльная нормаль
        float fx = cx + (d + B) * ux, fw = (d + B) * uw;
        float kx = fx + T * bx, kw = fw + T * bw;
        float k1 = f / Math.max(1f, f - fw), k2 = f / Math.max(1f, f - kw);
        float x1 = cx + (fx - cx) * k1, x2 = cx + (kx - cx) * k2;
        if (x2 - x1 < .5f) return;
        float hy = cy + B;
        torPaint.setShader(new LinearGradient(x1, 0, x2, 0,
                new int[]{0xFF3A3E44, 0xFF24272B, 0xFF141518}, new float[]{0f, .45f, 1f},
                Shader.TileMode.CLAMP));
        float r = (x2 - x1) * .45f;
        rpoly(path, new float[]{x1, cy - hy * k1, x2, cy - hy * k2, x2, cy + hy * k2, x1, cy + hy * k1},
                new float[]{r, r, r, r});
        c.drawPath(path, torPaint);
        rimLine.setStrokeWidth(1.5f / S);
        c.drawPath(path, rimLine);
        // узкий блик вдоль торца
        int a = rimLine.getAlpha();
        rimLine.setAlpha(0x1A);
        float mx = x1 + (x2 - x1) * .3f;
        c.drawLine(mx, cy - hy * k1 + r, mx, cy + hy * k1 - r, rimLine);
        rimLine.setAlpha(a);
    }

    /** Размытая картинка с прозрачностью от шарнира (x0) к свободному краю (x1). */
    private static Shader masked(Shader base, float x0, float x1, float kk, float[] curve) {
        int[] cols = new int[curve.length];
        for (int i = 0; i < curve.length; i++) {
            cols[i] = ((int) (255 * Math.min(1f, curve[i] * kk))) << 24;
        }
        Shader mask = new LinearGradient(x0, 0, x1, 0, cols, STOPS, Shader.TileMode.CLAMP);
        return new ComposeShader(base, mask, PorterDuff.Mode.DST_IN);
    }

    /**
     * Скруглённый многоугольник. Радиус ограничен и длиной граней, и углом между ними:
     * на остром угле (створка почти ребром) иначе дуга улетает за контур.
     */
    private static void rpoly(Path path, float[] xy, float[] rs) {
        path.rewind();
        int n = xy.length / 2;
        for (int i = 0; i < n; i++) {
            float ax = xy[2 * i], ay = xy[2 * i + 1];
            int pi = (i + n - 1) % n, ni = (i + 1) % n;
            float v1x = xy[2 * pi] - ax, v1y = xy[2 * pi + 1] - ay;
            float v2x = xy[2 * ni] - ax, v2y = xy[2 * ni + 1] - ay;
            float l1 = (float) Math.hypot(v1x, v1y), l2 = (float) Math.hypot(v2x, v2y);
            float t = 0;
            if (rs[i] > 0 && l1 > 1e-3f && l2 > 1e-3f) {
                double cos = Math.max(-1, Math.min(1, (v1x * v2x + v1y * v2y) / (l1 * l2)));
                double al = Math.acos(cos);
                if (al > 1e-3 && al < Math.PI - 1e-3) {
                    t = (float) Math.min(rs[i] / Math.tan(al / 2), Math.min(l1 / 2, l2 / 2));
                }
            }
            float p1x = l1 > 1e-3f ? ax + v1x / l1 * t : ax, p1y = l1 > 1e-3f ? ay + v1y / l1 * t : ay;
            float p2x = l2 > 1e-3f ? ax + v2x / l2 * t : ax, p2y = l2 > 1e-3f ? ay + v2y / l2 * t : ay;
            if (i == 0) path.moveTo(p1x, p1y); else path.lineTo(p1x, p1y);
            path.quadTo(ax, ay, p2x, p2y);
        }
        path.close();
    }

    // ---------- быстрое размытие маленькой картинки (несколько проходов box blur) ----------

    private static void boxBlur(Bitmap b, int radius, int passes) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < passes; i++) {
            pass(px, tmp, w, h, radius, true);
            pass(tmp, px, w, h, radius, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int len = horiz ? w : h, lines = horiz ? h : w;
        int div = r * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int ra = 0, rr = 0, rg = 0, rb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[idx(line, clamp(i, len), w, horiz)];
                ra += c >>> 24; rr += (c >> 16) & 255; rg += (c >> 8) & 255; rb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                out[idx(line, i, w, horiz)] =
                        (ra / div) << 24 | (rr / div) << 16 | (rg / div) << 8 | (rb / div);
                int o = in[idx(line, clamp(i - r, len), w, horiz)];
                int n = in[idx(line, clamp(i + r + 1, len), w, horiz)];
                ra += (n >>> 24) - (o >>> 24);
                rr += ((n >> 16) & 255) - ((o >> 16) & 255);
                rg += ((n >> 8) & 255) - ((o >> 8) & 255);
                rb += (n & 255) - (o & 255);
            }
        }
    }

    private static int clamp(int i, int len) { return i < 0 ? 0 : (i >= len ? len - 1 : i); }

    private static int idx(int line, int i, int w, boolean horiz) {
        return horiz ? line * w + i : i * w + line;
    }
}

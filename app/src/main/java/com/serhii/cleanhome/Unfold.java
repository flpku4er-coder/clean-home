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
 * Заставка «раскрытие» по модели iPhone Duo
 * (разбор: empirical.health/blog/why-iphone-duo-fold-animation-works).
 *
 * Главное: картинка НЕ поворачивается вместе со створкой. Она стоит на месте,
 * как будто лежит за экраном, а створка — это стекло, через которое на неё смотрят.
 * Левая половина неподвижна («внешний экран»). Правая створка откидывается
 * от шарнира по центру на зрителя: свободный край растёт по высоте (перспектива).
 * Размытие зависит от зазора между стеклом и картинкой: у шарнира резко,
 * к свободному краю сильно (кубическая кривая). Притемнение тоже к краю.
 */
public class Unfold extends View {

    /** Откуда взять текущий кадр фонового видео (SurfaceView в снимок не попадает). */
    public interface FrameSource { Bitmap frame(int w, int h); }

    private static final long OPEN_MS = 950;
    private static final long FADE_MS = 200;
    private static final int SNAP_DIV = 2;    // резкий кадр — половина разрешения
    private static final int MID_DIV = 8;     // среднее размытие
    private static final int HEAVY_DIV = 24;  // сильное размытие (свободный край)

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
    private final Path panel = new Path();

    private float p;          // 0 — сложен, 1 — раскрыт
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

            mid = Bitmap.createScaledBitmap(sharp, Math.max(1, w / MID_DIV), Math.max(1, h / MID_DIV), true);
            boxBlur(mid, 2, 2);
            heavy = Bitmap.createScaledBitmap(sharp, Math.max(1, w / HEAVY_DIV), Math.max(1, h / HEAVY_DIV), true);
            boxBlur(heavy, 2, 3);

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
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(OPEN_MS);
        // плавный разгон и мягкая посадка, как у петли
        anim.setInterpolator(new PathInterpolator(0.32f, 0f, 0.15f, 1f));
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

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        if (!ready) return;

        float cx = w / 2f, cy = h / 2f;

        // неподвижная левая половина — резкая, полной яркости
        c.drawRect(0, 0, cx, h, sharpPaint);

        // правая створка: угол от 90° (стоит ребром к зрителю) до 0° (легла в экран)
        double th = Math.toRadians(90.0 * (1f - p));
        float d = cx;
        float f = w * 1.5f;                       // «фокусное расстояние» камеры
        float zIn = (float) (d * Math.sin(th));   // насколько край вышел на зрителя
        float s = f / (f - zIn);                  // ближе к камере — крупнее
        float freeX = cx + (float) (d * Math.cos(th)) * s;
        if (freeX - cx < 2f) return;              // ещё ребром — створки не видно
        float top = cy - cy * s, bot = cy + cy * s;

        panel.rewind();
        panel.moveTo(cx, 0);
        panel.lineTo(freeX, top);
        panel.lineTo(freeX, bot);
        panel.lineTo(cx, h);
        panel.close();

        int save = c.save();
        c.clipPath(panel);
        // картинка в своих экранных координатах — не искажается, «стоит на месте»
        c.drawRect(cx, 0, w, h, sharpPaint);

        // зазор между стеклом и картинкой: чем больше угол, тем сильнее размытие
        float gap = (float) Math.sqrt(Math.sin(th));
        if (gap > 0.01f) {
            // среднее размытие нарастает быстро, сильное — по кубу к свободному краю
            midPaint.setShader(masked(midBase, cx, freeX, gap,
                    new float[]{0f, .35f, .8f, 1f, 1f}));
            c.drawRect(cx, 0, w, h, midPaint);
            heavyPaint.setShader(masked(heavyBase, cx, freeX, gap,
                    new float[]{0f, .016f, .125f, .42f, 1f}));
            c.drawRect(cx, 0, w, h, heavyPaint);

            // притемнение к свободному краю (яркость — после размытия)
            int a0 = (int) (0x18 * gap), a1 = (int) (0xA0 * gap);
            dimPaint.setShader(new LinearGradient(cx, 0, freeX, 0,
                    a0 << 24, a1 << 24, Shader.TileMode.CLAMP));
            c.drawRect(cx, 0, w, h, dimPaint);
        }
        c.restoreToCount(save);

        // тонкая резкая грань стекла: размыт контент, а не край
        if (gap > 0.01f) {
            edgePaint.setStrokeWidth(Math.max(1.5f, w / 900f));
            edgePaint.setAlpha((int) (0x55 * gap));
            c.drawLine(freeX, Math.max(0, top), freeX, Math.min(h, bot), edgePaint);
        }
    }

    /** Размытая картинка с прозрачностью от шарнира (x0) к свободному краю (x1). */
    private static Shader masked(Shader base, float x0, float x1, float k, float[] curve) {
        int[] cols = new int[curve.length];
        float[] pos = new float[curve.length];
        for (int i = 0; i < curve.length; i++) {
            cols[i] = ((int) (255 * Math.min(1f, curve[i] * k))) << 24;
            pos[i] = i / (float) (curve.length - 1);
        }
        Shader mask = new LinearGradient(x0, 0, x1, 0, cols, pos, Shader.TileMode.CLAMP);
        return new ComposeShader(base, mask, PorterDuff.Mode.DST_IN);
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

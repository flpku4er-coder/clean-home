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
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * Заставка «раскрытие» в духе iPhone Duo: экран раскрывается от центральной линии,
 * как книга. Края половинок размыты и притемнены, у «шарнира» картинка резкая.
 * По мере раскрытия размытие и тень уходят, и остаётся живой главный экран.
 *
 * Как пользоваться: {@link #prepare()} — снять кадр и закрыть экран чёрным
 * (делаем при выключении ТВ), {@link #play()} — проиграть (при включении).
 */
public class Unfold extends View {

    private static final long OPEN_MS = 1100;   // раскрытие
    private static final long FADE_MS = 280;    // переход на живой экран
    private static final float START_DEG = 82f; // насколько «сложен» экран в начале
    private static final int SNAP_DIV = 2;      // кадр в половину разрешения
    private static final int BLUR_DIV = 16;     // размытый кадр — в 1/16

    private final ViewGroup root;
    private final View[] hideWhileSnapping;

    private Bitmap sharp, blurred;
    private final Paint sharpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint blurPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Matrix half = new Matrix();
    private final float[] src = new float[8], dst = new float[8];

    private float p;            // 0 — сложен, 1 — раскрыт
    private boolean ready;
    private ValueAnimator anim;

    public Unfold(Context c, ViewGroup root, View... hideWhileSnapping) {
        super(c);
        this.root = root;
        this.hideWhileSnapping = hideWhileSnapping;
        setVisibility(GONE);
        setFocusable(false);
        dimPaint.setColor(Color.BLACK);
    }

    /** Закрыть экран чёрным, пока кадр не готов (например, сразу при запуске). */
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
            float[] old = new float[hideWhileSnapping.length];
            for (int i = 0; i < old.length; i++) {
                old[i] = hideWhileSnapping[i].getAlpha();
                hideWhileSnapping[i].setAlpha(1f); // небо вместо видео: SurfaceView в кадр не попадает
            }
            sharp = Bitmap.createBitmap(w / SNAP_DIV, h / SNAP_DIV, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(sharp);
            c.scale(1f / SNAP_DIV, 1f / SNAP_DIV);
            root.draw(c);
            for (int i = 0; i < old.length; i++) hideWhileSnapping[i].setAlpha(old[i]);

            blurred = Bitmap.createScaledBitmap(sharp, Math.max(1, w / BLUR_DIV),
                    Math.max(1, h / BLUR_DIV), true);
            boxBlur(blurred, 2, 3);

            Shader s = new BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix sm = new Matrix();
            sm.setScale(SNAP_DIV, SNAP_DIV);
            s.setLocalMatrix(sm);
            sharpPaint.setShader(s);

            Shader b = new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix bm = new Matrix();
            bm.setScale(w / (float) blurred.getWidth(), h / (float) blurred.getHeight());
            b.setLocalMatrix(bm);
            // у шарнира почти резко, к свободному краю — сильнее (кубическая кривая)
            float cx = w / 2f;
            int[] a = new int[]{0x26, 0x30, 0x55, 0x9B, 0xFF};
            int[] cols = new int[a.length];
            for (int i = 0; i < a.length; i++) cols[i] = a[i] << 24;
            Shader mask = new LinearGradient(cx, 0, 0, 0, cols,
                    new float[]{0f, .25f, .5f, .75f, 1f}, Shader.TileMode.MIRROR);
            blurPaint.setShader(new ComposeShader(b, mask, PorterDuff.Mode.DST_IN));

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

    /** Проиграть раскрытие. Если кадра нет — снимет сам. */
    public void play() {
        if (!ready) prepare();
        if (!ready) { setVisibility(GONE); return; }
        cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(OPEN_MS);
        anim.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f)); // быстро, с мягкой посадкой
        anim.addUpdateListener(v -> { p = (float) v.getAnimatedValue(); invalidate(); });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator x) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator x) {
                if (cancelled) return;
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
        blurPaint.setShader(null);
        if (sharp != null) { sharp.recycle(); sharp = null; }
        if (blurred != null) { blurred.recycle(); blurred = null; }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        if (!ready) return;

        float q = 1f - p;
        float deg = START_DEG * q;
        float blur = (float) Math.pow(q, 0.8);   // размытие уходит чуть позже, чем угол
        int dim = (int) (0xD0 * Math.pow(q, 1.4));

        drawHalf(c, w, h, true, deg, blur, dim);
        drawHalf(c, w, h, false, deg, blur, dim);
    }

    /** Одна половина, повёрнутая вокруг вертикальной линии по центру экрана. */
    private void drawHalf(Canvas c, int w, int h, boolean left, float deg,
                          float blur, int dim) {
        float cx = w / 2f, cy = h / 2f;
        float d = cx;                         // ширина половины
        double r = Math.toRadians(deg);
        float f = w * 1.1f;                   // «фокусное расстояние» камеры
        float z = (float) (d * Math.sin(r));  // насколько свободный край ушёл вглубь
        float k = f / (f + z);                // перспективное уменьшение края
        float ex = (float) (d * Math.cos(r)) * k;
        float edgeX = left ? cx - ex : cx + ex;
        float fromX = left ? 0 : w;

        // исходный прямоугольник половины → трапеция
        src[0] = fromX; src[1] = 0;     src[2] = cx; src[3] = 0;
        src[4] = cx;    src[5] = h;     src[6] = fromX; src[7] = h;
        dst[0] = edgeX; dst[1] = cy - cy * k;  dst[2] = cx; dst[3] = 0;
        dst[4] = cx;    dst[5] = h;            dst[6] = edgeX; dst[7] = cy + cy * k;
        half.setPolyToPoly(src, 0, dst, 0, 4);

        int save = c.save();
        c.concat(half);
        float l = left ? 0 : cx, rgt = left ? cx : w;
        c.clipRect(l, 0, rgt, h);
        c.drawRect(l, 0, rgt, h, sharpPaint);
        if (blur > 0.01f) {
            blurPaint.setAlpha((int) (255 * Math.min(1f, blur)));
            c.drawRect(l, 0, rgt, h, blurPaint);
        }
        if (dim > 0) {
            dimPaint.setAlpha(dim);
            c.drawRect(l, 0, rgt, h, dimPaint);
        }
        c.restoreToCount(save);
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

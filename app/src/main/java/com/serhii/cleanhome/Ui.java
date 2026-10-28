package com.serhii.cleanhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Мелкие помощники для вёрстки без XML. */
final class Ui {
    private Ui() {}

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** Размер плитки (формат TV-баннера 16:9). */
    static final int TILE_W = 192, TILE_H = 108;
    /** Запас вокруг плитки под свечение. */
    static final int HALO = 22;
    /** Цвет «без приложения» — для служебных плиток. */
    static final int NEUTRAL = 0xFF8FA2FF;

    interface FocusListener { void onFocus(int color); }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static void text(TextView t, float sp, int color, boolean bold) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        if (bold) t.setLetterSpacing(-0.02f);
    }

    /** Статичный фон (экран «Все приложения»). */
    static Drawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Sky.colorsAt(System.currentTimeMillis()));
    }

    /** Карточка-«стекло» для виджетов. */
    static Drawable glass(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 20));
        g.setColor(0x1FFFFFFF);
        g.setStroke(dp(c, 1), 0x1AFFFFFF);
        return g;
    }

    /**
     * Главный цвет иконки или баннера: среднее по насыщенным пикселям.
     * YouTube и Netflix — красный, Prime — синий и т.д.
     */
    static int dominantColor(Drawable d) {
        if (d == null) return NEUTRAL;
        try {
            int w = 32, h = 18;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 200) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.3f) continue;
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++;
            }
            b.recycle();
            if (n < 6) return NEUTRAL;
            int avg = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
            Color.colorToHSV(avg, hsv);
            hsv[1] = Math.max(hsv[1], 0.65f);
            hsv[2] = 0.95f;
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return NEUTRAL;
        }
    }

    static View tile(Context c, Drawable d, String glyph, String label,
                     boolean banner, boolean focusable) {
        return tile(c, d, glyph, label, banner, focusable, null);
    }

    /**
     * Плитка с подписью. В фокусе: пружинит, вокруг загорается свечение
     * в цвете приложения, слушатель получает этот цвет (для фона).
     */
    static View tile(Context c, Drawable d, String glyph, String label,
                     boolean banner, boolean focusable, FocusListener listener) {
        int w = dp(c, TILE_W), h = dp(c, TILE_H), halo = dp(c, HALO);
        int color = d != null ? dominantColor(d) : NEUTRAL;

        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setClipChildren(false);
        t.setClipToPadding(false);

        // слой 1: свечение, слой 2: сама плитка
        FrameLayout stack = new FrameLayout(c);
        stack.setClipChildren(false);

        // свечение: квадрат больше плитки, радиус = половина стороны (гаснет до нуля
        // ровно к краю — обрезки нет), сплющен по вертикали в овал
        int s = Math.round(w * 1.6f);
        View glow = new View(c);
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(s / 2f);
        gd.setColors(new int[]{withAlpha(color, 0xA0), withAlpha(color, 0x38), 0x00000000});
        glow.setBackground(gd);
        glow.setAlpha(0f);
        glow.setScaleY(0.7f);
        stack.addView(glow, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));

        FrameLayout box = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 14));
        bg.setColor(0x26FFFFFF);
        box.setBackground(bg);
        box.setClipToOutline(true);

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(c, 14));
        ring.setColor(Color.TRANSPARENT);
        box.setForeground(ring);

        if (d != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(d);
            if (banner) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                box.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            } else {
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int s = dp(c, 58);
                box.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            }
        } else {
            TextView g = new TextView(c);
            g.setText(glyph);
            text(g, glyph.length() > 2 ? 26 : 36, Color.WHITE, glyph.length() > 2);
            g.setGravity(Gravity.CENTER);
            box.addView(g, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(w, h);
        blp.setMargins(halo, halo, halo, halo);
        stack.addView(box, blp);
        t.addView(stack, new LinearLayout.LayoutParams(w + halo * 2, h + halo * 2));

        TextView l = new TextView(c);
        l.setText(label);
        text(l, 14, 0x99FFFFFF, false);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(w, WRAP);
        llp.topMargin = -halo + dp(c, 8);
        t.addView(l, llp);

        if (focusable) {
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    stack.animate().scaleX(1.12f).scaleY(1.12f).setDuration(320)
                            .setInterpolator(new OvershootInterpolator(2.2f)).start();
                    glow.animate().alpha(1f).setDuration(260).start();
                    ring.setStroke(dp(c, 2), 0xE6FFFFFF);
                    l.setTextColor(Color.WHITE);
                    if (listener != null) listener.onFocus(color);
                } else {
                    stack.animate().scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    glow.animate().alpha(0f).setDuration(180).start();
                    ring.setStroke(0, Color.TRANSPARENT);
                    l.setTextColor(0x99FFFFFF);
                }
            });
        }
        return t;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

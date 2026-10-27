package com.serhii.cleanhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Мелкие помощники для вёрстки без XML. */
final class Ui {
    private Ui() {}

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

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

    /** Тёмный фон: глубокий синий → фиолетовый. */
    static Drawable background() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1026, 0xFF1A2050, 0xFF2D1B4E});
        return g;
    }

    /**
     * Плитка приложения 240×135 dp (формат TV-баннера) с подписью.
     * Если drawable == null, в центре рисуется glyph (символ).
     */
    static View tile(Context c, Drawable d, String glyph, String label,
                     boolean banner, boolean focusable) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);

        int w = dp(c, 240), h = dp(c, 135);
        FrameLayout box = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 16));
        bg.setColor(0x26FFFFFF);
        box.setBackground(bg);
        box.setClipToOutline(true);

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(c, 16));
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
                int s = dp(c, 72);
                box.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            }
        } else {
            TextView g = new TextView(c);
            g.setText(glyph);
            text(g, 44, Color.WHITE, false);
            g.setGravity(Gravity.CENTER);
            box.addView(g, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        t.addView(box, new LinearLayout.LayoutParams(w, h));

        TextView l = new TextView(c);
        l.setText(label);
        text(l, 16, 0x99FFFFFF, false);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(c, 10), 0, 0);
        t.addView(l, new LinearLayout.LayoutParams(w, WRAP));

        if (focusable) {
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                float s = has ? 1.1f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(150).start();
                ring.setStroke(has ? dp(c, 3) : 0, Color.WHITE);
                l.setTextColor(has ? Color.WHITE : 0x99FFFFFF);
            });
        }
        return t;
    }
}

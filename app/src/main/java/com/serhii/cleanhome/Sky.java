package com.serhii.cleanhome;

import android.animation.ArgbEvaluator;

import java.util.Calendar;

/** Цвет фона по времени суток: плавный переход между опорными точками. */
final class Sky {
    private Sky() {}

    // час, три цвета градиента (верх-лево → низ-право)
    private static final float[] HOURS = {0, 5.5f, 7.5f, 10, 14, 18, 20, 22, 24};
    private static final int[][] COLORS = {
            {0xFF070B1E, 0xFF121842, 0xFF24174A}, // ночь
            {0xFF070B1E, 0xFF121842, 0xFF24174A}, // ночь
            {0xFF1B1F4B, 0xFF4E3468, 0xFF9A5A62}, // рассвет
            {0xFF0C2453, 0xFF1A4D86, 0xFF3C7FA8}, // утро
            {0xFF0A2560, 0xFF1A4594, 0xFF3266B8}, // день
            {0xFF1A1647, 0xFF4A2A72, 0xFF9A4E5A}, // закат
            {0xFF120F3A, 0xFF2E1F5E, 0xFF4A2350}, // вечер
            {0xFF0A0D28, 0xFF171C4A, 0xFF2A1A4E}, // поздний вечер
            {0xFF070B1E, 0xFF121842, 0xFF24174A}, // ночь
    };
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    static int[] colorsAt(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        float h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f;
        int i = 0;
        while (i < HOURS.length - 2 && h >= HOURS[i + 1]) i++;
        float f = (h - HOURS[i]) / (HOURS[i + 1] - HOURS[i]);
        int[] out = new int[3];
        for (int k = 0; k < 3; k++) {
            out[k] = (Integer) ARGB.evaluate(f, COLORS[i][k], COLORS[i + 1][k]);
        }
        return out;
    }
}

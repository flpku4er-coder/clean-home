package com.serhii.cleanhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/** Главный экран: часы, дата, погода и ряд избранных приложений. */
public class MainActivity extends Activity {

    // El Campello. Поменяй, если нужно другое место.
    private static final double LAT = 38.428;
    private static final double LON = -0.397;
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;

    private LinearLayout row;
    private TextView weather;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            loadWeather();
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());
        root.setClipChildren(false);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int padH = Ui.dp(this, 64), padV = Ui.dp(this, 48);
        col.setPadding(padH, padV, padH, padV);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        Ui.text(time, 104, Color.WHITE, true);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        Ui.text(date, 24, 0xCCFFFFFF, false);

        weather = new TextView(this);
        Ui.text(weather, 24, 0xCCFFFFFF, false);
        weather.setPadding(0, Ui.dp(this, 8), 0, 0);

        col.addView(time);
        col.addView(date);
        col.addView(weather);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        int pad = Ui.dp(this, 20);
        scroll.setPadding(pad, pad, pad, pad);

        row = new LinearLayout(this);
        row.setClipChildren(false);
        scroll.addView(row);
        col.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        root.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRow();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
    }

    /** Кнопка входов на пульте, пока открыт наш экран. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_TV_INPUT) {
            openInputs();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Окно выбора HDMI из оболочки Xiaomi (PatchWall). */
    private void openInputs() {
        Intent i = new Intent("com.mitv.tvhome.atv.app.tv.INPUTSOURCE_POPUP");
        i.setPackage("com.mitv.tvhome.atv");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть выбор входа", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // это главный экран — «назад» никуда не ведёт
    }

    private void refreshRow() {
        row.removeAllViews();
        List<Apps.AppInfo> favs = Apps.favorites(this);
        for (Apps.AppInfo a : favs) {
            boolean banner = a.banner != null;
            View t = Ui.tile(this, banner ? a.banner : a.icon, null, a.label, banner, true);
            t.setOnClickListener(v -> Apps.launch(this, a.pkg));
            t.setOnLongClickListener(v -> {
                Apps.toggleFav(this, a.pkg);
                Toast.makeText(this, "Убрано с главного: " + a.label, Toast.LENGTH_SHORT).show();
                refreshRow();
                return true;
            });
            addTile(t);
        }

        View inputs = Ui.tile(this, null, "HDMI", "Входы", false, true);
        inputs.setOnClickListener(v -> openInputs());
        addTile(inputs);

        View all = Ui.tile(this, null, "⋯", "Все приложения", false, true);
        all.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        addTile(all);

        View settings = Ui.tile(this, null, "⚙", "Настройки", false, true);
        settings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        addTile(settings);

        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    private void addTile(View t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMargins(0, 0, Ui.dp(this, 28), 0);
        row.addView(t, lp);
    }

    // ---------- погода: Open-Meteo, без ключа ----------

    private void loadWeather() {
        new Thread(() -> {
            String text;
            try {
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=temperature_2m_max,temperature_2m_min"
                                + "&timezone=auto&forecast_days=1", LAT, LON);
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject j = new JSONObject(sb.toString());
                JSONObject cur = j.getJSONObject("current");
                JSONObject day = j.getJSONObject("daily");
                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                int max = (int) Math.round(day.getJSONArray("temperature_2m_max").getDouble(0));
                int min = (int) Math.round(day.getJSONArray("temperature_2m_min").getDouble(0));
                text = describe(code) + "  " + t + "°   ·   " + min + "° / " + max + "°";
            } catch (Exception e) {
                text = "";
            }
            final String out = text;
            runOnUiThread(() -> {
                if (!out.isEmpty() || weather.getText().length() == 0) weather.setText(out);
            });
        }).start();
    }

    private static String describe(int c) {
        if (c == 0) return "☀ Ясно";
        if (c <= 2) return "🌤 Малооблачно";
        if (c == 3) return "☁ Пасмурно";
        if (c == 45 || c == 48) return "🌫 Туман";
        if (c >= 51 && c <= 57) return "🌦 Морось";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "🌧 Дождь";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "❄ Снег";
        if (c >= 95) return "⛈ Гроза";
        return "";
    }
}

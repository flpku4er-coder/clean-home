package com.serhii.cleanhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Главный экран: часы, погода, виджет моря и ряд избранных приложений. */
public class MainActivity extends Activity {

    // El Campello. Поменяй, если нужно другое место.
    private static final double LAT = 38.428;
    private static final double LON = -0.397;
    // точка в море напротив пляжа — для температуры воды
    private static final double SEA_LAT = 38.420;
    private static final double SEA_LON = -0.370;
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;
    private static final long SKY_EVERY_MS = 60 * 1000L;
    // тестовый фон: камин, Pexels (бесплатная лицензия), 1080p, 19 с
    private static final String BG_VIDEO =
            "https://videos.pexels.com/video-files/3696057/3696057-hd_1920_1080_24fps.mp4";

    private SurfaceView video;
    private View skyView;
    private MediaPlayer player;
    private boolean playerReady;

    private LinearLayout row;
    private TextView weather, seaTemp;
    private LinearLayout forecast;
    private GradientDrawable sky, tint;
    private int tintColor = 0x00000000;
    private ValueAnimator tintAnim;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            loadWeather();
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };
    private final Runnable skyTick = new Runnable() {
        @Override public void run() {
            sky.setColors(Sky.colorsAt(System.currentTimeMillis()));
            handler.postDelayed(this, SKY_EVERY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);

        // фон по времени суток
        // видео: SurfaceView — аппаратный слой, почти не грузит процессор
        video = new SurfaceView(this);
        video.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { startVideo(h); }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
            @Override public void surfaceDestroyed(SurfaceHolder h) { releaseVideo(); }
        });
        root.addView(video, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // небо поверх видео: видно, пока видео грузится, потом плавно уходит
        skyView = new View(this);
        sky = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Sky.colorsAt(System.currentTimeMillis()));
        skyView.setBackground(sky);
        root.addView(skyView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // затемнение, чтобы текст читался поверх видео
        View dim = new View(this);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0x33000000, 0x99000000}));
        root.addView(dim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // подсветка снизу в цвет выбранного приложения
        View tintView = new View(this);
        tint = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0, 0, 0});
        tintView.setBackground(tint);
        root.addView(tintView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int padH = Ui.dp(this, 64), padV = Ui.dp(this, 48);
        col.setPadding(padH, padV, padH, Ui.dp(this, 36));
        col.setClipChildren(false);
        col.setClipToPadding(false);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        Ui.text(time, 83, Color.WHITE, true);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        Ui.text(date, 19, 0xCCFFFFFF, false);

        weather = new TextView(this);
        Ui.text(weather, 19, 0xCCFFFFFF, false);
        weather.setPadding(0, Ui.dp(this, 6), 0, 0);

        col.addView(time);
        col.addView(date);
        col.addView(weather);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.addView(row);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        slp.leftMargin = -Ui.dp(this, Ui.MONO ? 10 : Ui.HALO); // плитки ровно по краю текста
        col.addView(scroll, slp);

        root.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(buildSeaWidget(), seaWidgetParams());
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRow();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(skyTick);
        handler.post(weatherTick);
        handler.post(skyTick);
        if (player != null && playerReady) player.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(skyTick);
        if (player != null && playerReady) player.pause();
    }

    @Override
    protected void onDestroy() {
        releaseVideo();
        super.onDestroy();
    }

    // ---------- фоновое видео ----------

    private void startVideo(SurfaceHolder holder) {
        try {
            releaseVideo();
            player = new MediaPlayer();
            player.setDisplay(holder);
            player.setDataSource(BG_VIDEO);
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(mp -> {
                playerReady = true;
                if (hasWindowFocus()) mp.start();
                skyView.animate().alpha(0f).setDuration(1200).start();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                skyView.animate().alpha(1f).setDuration(300).start();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            skyView.setAlpha(1f);
        }
    }

    private void releaseVideo() {
        playerReady = false;
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    /** Кнопка входов на пульте, пока открыт наш экран. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (KeyService.isInputKey(keyCode)) {
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

    // ---------- ряд плиток ----------

    private void refreshRow() {
        row.removeAllViews();
        Ui.FocusListener onFocus = Ui.MONO ? (c -> { }) : this::animateTint;
        List<Apps.AppInfo> favs = Apps.favorites(this);
        for (Apps.AppInfo a : favs) {
            boolean banner = a.banner != null;
            View t = Ui.tile(this, banner ? a.banner : a.icon, null, a.label, banner, true, onFocus);
            t.setOnClickListener(v -> Apps.launch(this, a.pkg));
            t.setOnLongClickListener(v -> {
                Apps.toggleFav(this, a.pkg);
                Toast.makeText(this, "Убрано с главного: " + a.label, Toast.LENGTH_SHORT).show();
                refreshRow();
                return true;
            });
            row.addView(t);
        }

        View inputs = Ui.tile(this, null, "HDMI", "Входы", false, true, onFocus);
        inputs.setOnClickListener(v -> openInputs());
        row.addView(inputs);

        View all = Ui.tile(this, null, "⋯", "Все приложения", false, true, onFocus);
        all.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        row.addView(all);

        View settings = Ui.tile(this, null, "⚙", "Настройки", false, true, onFocus);
        settings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        row.addView(settings);

        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    /** Плавно перекрашивает подсветку фона в цвет выбранной плитки. */
    private void animateTint(int target) {
        if (tintAnim != null) tintAnim.cancel();
        tintAnim = ValueAnimator.ofObject(new ArgbEvaluator(), tintColor, target | 0xFF000000);
        tintAnim.setDuration(600);
        tintAnim.addUpdateListener(an -> {
            tintColor = (Integer) an.getAnimatedValue();
            tint.setColors(new int[]{
                    Ui.withAlpha(tintColor, 0x66),
                    Ui.withAlpha(tintColor, 0x1C),
                    0x00000000});
        });
        tintAnim.start();
    }

    // ---------- виджет: море и прогноз ----------

    private View buildSeaWidget() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.glass(this));
        int p = Ui.dp(this, 20);
        card.setPadding(p, Ui.dp(this, 16), p, Ui.dp(this, 16));

        TextView label = new TextView(this);
        label.setText("МОРЕ");
        Ui.text(label, 12, 0x99FFFFFF, true);
        label.setLetterSpacing(0.14f);
        card.addView(label);

        seaTemp = new TextView(this);
        seaTemp.setText("—");
        Ui.text(seaTemp, 40, Color.WHITE, true);
        card.addView(seaTemp);

        View line = new View(this);
        line.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 1));
        llp.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        card.addView(line, llp);

        forecast = new LinearLayout(this);
        forecast.setOrientation(LinearLayout.VERTICAL);
        card.addView(forecast);
        return card;
    }

    private FrameLayout.LayoutParams seaWidgetParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(this, 230), Ui.WRAP, Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.dp(this, 48), Ui.dp(this, 64), 0);
        return lp;
    }

    private void fillForecast(String[] days, int[] codes, int[] mins, int[] maxs) {
        forecast.removeAllViews();
        for (int i = 0; i < days.length; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));

            TextView d = new TextView(this);
            d.setText(days[i]);
            Ui.text(d, 15, 0xCCFFFFFF, false);
            r.addView(d, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));

            TextView ic = new TextView(this);
            ic.setText(icon(codes[i]));
            Ui.text(ic, 15, Color.WHITE, false);
            ic.setPadding(0, 0, Ui.dp(this, 10), 0);
            r.addView(ic);

            TextView t = new TextView(this);
            t.setText(mins[i] + "° / " + maxs[i] + "°");
            Ui.text(t, 15, Color.WHITE, false);
            r.addView(t);

            forecast.addView(r);
        }
    }

    // ---------- данные: Open-Meteo, без ключа ----------

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void loadWeather() {
        new Thread(() -> {
            // погода сейчас + 3 дня вперёд
            try {
                JSONObject j = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&timezone=auto&forecast_days=4", LAT, LON)));
                JSONObject cur = j.getJSONObject("current");
                JSONObject day = j.getJSONObject("daily");
                JSONArray dates = day.getJSONArray("time");
                JSONArray codes = day.getJSONArray("weather_code");
                JSONArray maxA = day.getJSONArray("temperature_2m_max");
                JSONArray minA = day.getJSONArray("temperature_2m_min");

                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                String now = icon(code) + " " + describe(code) + "  " + t + "°   ·   "
                        + Math.round(minA.getDouble(0)) + "° / " + Math.round(maxA.getDouble(0)) + "°";

                int n = Math.min(3, dates.length() - 1);
                String[] dn = new String[n];
                int[] dc = new int[n], dmin = new int[n], dmax = new int[n];
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat out = new SimpleDateFormat("EEEE", Locale.getDefault());
                for (int i = 0; i < n; i++) {
                    Date dt = in.parse(dates.getString(i + 1));
                    String s = out.format(dt);
                    dn[i] = s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
                    dc[i] = codes.getInt(i + 1);
                    dmin[i] = (int) Math.round(minA.getDouble(i + 1));
                    dmax[i] = (int) Math.round(maxA.getDouble(i + 1));
                }
                runOnUiThread(() -> {
                    weather.setText(now);
                    fillForecast(dn, dc, dmin, dmax);
                });
            } catch (Exception ignored) { }

            // температура воды
            try {
                JSONObject m = new JSONObject(get(String.format(Locale.US,
                        "https://marine-api.open-meteo.com/v1/marine?latitude=%.3f&longitude=%.3f"
                                + "&current=sea_surface_temperature&timezone=auto", SEA_LAT, SEA_LON)));
                double sst = m.getJSONObject("current").getDouble("sea_surface_temperature");
                String s = Math.round(sst) + "°";
                runOnUiThread(() -> seaTemp.setText(s));
            } catch (Exception ignored) { }
        }).start();
    }

    private static String icon(int c) {
        if (c == 0) return "☀";
        if (c <= 2) return "🌤";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 51 && c <= 57) return "🌦";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "🌧";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "❄";
        if (c >= 95) return "⛈";
        return "";
    }

    private static String describe(int c) {
        if (c == 0) return "Ясно";
        if (c <= 2) return "Малооблачно";
        if (c == 3) return "Пасмурно";
        if (c == 45 || c == 48) return "Туман";
        if (c >= 51 && c <= 57) return "Морось";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "Дождь";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "Снег";
        if (c >= 95) return "Гроза";
        return "";
    }
}

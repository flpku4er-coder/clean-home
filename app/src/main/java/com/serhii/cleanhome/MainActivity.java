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
    // фоновое видео лежит на самом ТВ (кладётся через adb push, см. README):
    // /sdcard/Android/data/com.serhii.cleanhome/files/bg.mp4
    private static final String BG_FILE = "bg.mp4";

    private SurfaceView video;
    private View skyView;
    private MediaPlayer player;
    private boolean playerReady;

    private LinearLayout row;
    private TextView weather, seaTemp, tomorrow;
    private GradientDrawable sky, tint;
    private int tintColor = 0x00000000;
    private ValueAnimator tintAnim;

    // заставка «раскрытие» (как у iPhone Duo) при включении ТВ
    private Unfold unfold;
    private boolean resumed;
    private long videoPosUs = -1;   // где было видео, когда уходили с главного
    private final android.content.BroadcastReceiver screenReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                // кадр снимаем заранее, чтобы при включении не мигнуло (если onStop ещё не снял)
                if (resumed && !unfold.isActive()) unfold.prepare();
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                if (resumed && unfold.isActive()) handler.postDelayed(unfold::play, 60);
            }
        }
    };

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
        Ui.text(time, 83, 0xB3FFFFFF, true);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        Ui.text(date, 19, 0x80FFFFFF, false);


        col.addView(time);
        col.addView(date);
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

        unfold = new Unfold(this, root, skyView, new Unfold.FrameSource() {
            @Override public long position() {
                if (player != null && playerReady) {
                    try { return player.getCurrentPosition() * 1000L; } catch (Exception e) { return -1; }
                }
                return videoPosUs;
            }
            @Override public android.graphics.Bitmap frame(long us, int w, int h) { return videoFrame(us, w, h); }
        });
        root.addView(unfold, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);

        // холодный старт после загрузки ТВ (а не перезапуск лаунчера системой):
        // раскрываемся, когда экран собран
        if (savedInstanceState == null && android.os.SystemClock.elapsedRealtime() < 3 * 60 * 1000L) {
            unfold.cover();
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            if (root.getWidth() == 0 || row.getChildCount() == 0) return;
                            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            handler.postDelayed(unfold::play, 250); // даём иконкам прогрузиться
                        }
                    });
        }

        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);
    }

    /** Для проверки с компьютера: adb shell am start -n com.serhii.cleanhome/.MainActivity --ez unfold true */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("unfold", false)) {
            handler.postDelayed(() -> { unfold.prepare(); unfold.play(); }, 300);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ServiceGuard.ensure(this);
        refreshRow();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(skyTick);
        handler.post(weatherTick);
        handler.post(skyTick);
        if (player != null && playerReady) player.start();
        // вернулись на главный (из приложения или после включения ТВ) — раскрываемся.
        // Кадр снят в onStop, поэтому экран уже закрыт заставкой и ничего не мигает.
        // При холодном старте экран ещё не размечен — там запускает onCreate.
        if (unfold.isActive() && unfold.getWidth() > 0) handler.postDelayed(unfold::play, 60);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ушли с главного: снимаем кадр заранее (в onStop, а не onPause —
        // иначе полупрозрачные окна поверх, например выбор HDMI, легли бы на чёрный фон)
        unfold.prepare();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        try { videoPosUs = (player != null && playerReady) ? player.getCurrentPosition() * 1000L : -1; }
        catch (Exception e) { videoPosUs = -1; }
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(skyTick);
        if (player != null && playerReady) player.pause();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        releaseVideo();
        super.onDestroy();
    }

    // ---------- фоновое видео ----------

    private void startVideo(SurfaceHolder holder) {
        try {
            releaseVideo();
            java.io.File dir = getExternalFilesDir(null);
            java.io.File f = dir == null ? null : new java.io.File(dir, BG_FILE);
            if (f == null || !f.exists()) return; // видео нет — остаётся небо
            player = new MediaPlayer();
            player.setDisplay(holder);
            player.setDataSource(f.getAbsolutePath());
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(mp -> {
                playerReady = true;
                // продолжаем видео с того же места — совпадёт с кадром в заставке
                if (videoPosUs > 0) try { mp.seekTo((int) (videoPosUs / 1000)); } catch (Exception ignored) { }
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

    /** Текущий кадр фонового видео — для заставки «раскрытие». */
    private android.graphics.Bitmap videoFrame(long us, int w, int h) {
        java.io.File dir = getExternalFilesDir(null);
        if (dir == null) return null;
        android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
        try {
            r.setDataSource(new java.io.File(dir, BG_FILE).getAbsolutePath());
            int opt = android.media.MediaMetadataRetriever.OPTION_CLOSEST;
            return android.os.Build.VERSION.SDK_INT >= 27
                    ? r.getScaledFrameAtTime(us, opt, w, h)
                    : r.getFrameAtTime(us, opt);
        } catch (Throwable t) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) { }
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
        String pkg = KeyService.appForKey(keyCode);
        if (pkg != null) {
            Apps.launch(this, pkg);
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
            addTile(t);
        }

        View inputs = Ui.tile(this, null, "HDMI", "Входы", false, true, onFocus);
        inputs.setOnClickListener(v -> openInputs());
        addTile(inputs);

        View all = Ui.tile(this, null, "⋯", "Все приложения", false, true, onFocus);
        all.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        addTile(all);

        View settings = Ui.tile(this, null, "⚙", "Настройки", false, true, onFocus);
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
        // логотипы: шаг 212dp, видимый зазор ~82dp → сжимаем вдвое (−65dp вместо −24dp)
        if (Ui.MONO) lp.rightMargin = -Ui.dp(this, 65);
        row.addView(t, lp);
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
        card.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));

        // ряд: погода сейчас · море
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weather = new TextView(this);
        weather.setText("—");
        Ui.text(weather, 30, Color.WHITE, true);
        top.addView(weather);

        View gap = new View(this);
        top.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 24), 1));

        seaTemp = new TextView(this);
        seaTemp.setText("—");
        setIcon(seaTemp, R.drawable.wx_sea, 28);
        Ui.text(seaTemp, 30, Color.WHITE, true);
        top.addView(seaTemp);
        // дети вертикального LinearLayout по умолчанию MATCH_PARENT по ширине —
        // из-за этого карточка растягивалась на весь экран и наезжала на часы
        card.addView(top, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));

        View line = new View(this);
        line.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 1));
        llp.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        card.addView(line, llp);

        // завтра
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        TextView tl = new TextView(this);
        tl.setText("Завтра");
        Ui.text(tl, 15, 0x99FFFFFF, false);
        tr.addView(tl);
        View tg = new View(this);
        tr.addView(tg, new LinearLayout.LayoutParams(Ui.dp(this, 14), 1));
        tomorrow = new TextView(this);
        Ui.text(tomorrow, 15, 0xCCFFFFFF, false);
        tr.addView(tomorrow);
        card.addView(tr, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        return card;
    }

    private FrameLayout.LayoutParams seaWidgetParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
        lp.setMargins(0, Ui.dp(this, 48), Ui.dp(this, 64), 0);
        return lp;
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
            // погода сейчас + завтра
            try {
                JSONObject j = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&current=temperature_2m,weather_code"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                + "&timezone=auto&forecast_days=2", LAT, LON)));
                JSONObject cur = j.getJSONObject("current");
                JSONObject day = j.getJSONObject("daily");
                JSONArray dates = day.getJSONArray("time");
                JSONArray codes = day.getJSONArray("weather_code");
                JSONArray maxA = day.getJSONArray("temperature_2m_max");
                JSONArray minA = day.getJSONArray("temperature_2m_min");

                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                String now = t + "°";
                int tcode = codes.getInt(1);
                String tmr = Math.round(minA.getDouble(1)) + "° / " + Math.round(maxA.getDouble(1)) + "°";
                runOnUiThread(() -> {
                    weather.setText(now);
                    setIcon(weather, iconRes(code), 28);
                    tomorrow.setText(tmr);
                    setIcon(tomorrow, iconRes(tcode), 18);
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

    /** Белая полупрозрачная иконка слева от текста. */
    private void setIcon(TextView tv, int res, int sizeDp) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        int px = Ui.dp(this, sizeDp);
        d.setBounds(0, 0, px, px);
        d.setAlpha(0xB3);
        tv.setCompoundDrawablesRelative(d, null, null, null);
        tv.setCompoundDrawablePadding(Ui.dp(this, sizeDp > 20 ? 10 : 6));
    }

    private static int iconRes(int c) {
        if (c == 0) return R.drawable.wx_sun;
        if (c <= 2) return R.drawable.wx_partly;
        if (c == 3) return R.drawable.wx_cloud;
        if (c == 45 || c == 48) return R.drawable.wx_fog;
        if (c >= 51 && c <= 57) return R.drawable.wx_drizzle;
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return R.drawable.wx_rain;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return R.drawable.wx_snow;
        if (c >= 95) return R.drawable.wx_storm;
        return R.drawable.wx_cloud;
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
